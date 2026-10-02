# JVM and Thread Profiling Analysis

Analisis ini memakai workload dan environment yang sama dengan
[`baseline.md`](baseline.md). Tujuannya adalah menjelaskan karakter runtime, bukan mencari alasan
untuk melakukan optimasi tanpa bukti.

## Metode

JFR dimulai pada JVM LedgerBank yang sudah warm menggunakan profile bawaan JDK 25:

```powershell
jcmd <pid> JFR.start name=history_final settings=profile `
  filename=target/performance/history-final.jfr maxsize=250m dumponexit=true
jcmd <pid> JFR.stop name=history_final

jfr view recording target/performance/history-final.jfr
jfr view cpu-load target/performance/history-final.jfr
jfr view hot-methods target/performance/history-final.jfr
jfr view thread-cpu-load target/performance/history-final.jfr
jfr view allocation-by-site target/performance/history-final.jfr
jfr view latencies-by-type target/performance/history-final.jfr
jfr view contention-by-site target/performance/history-final.jfr
jfr view socket-reads-by-host target/performance/history-final.jfr
jfr view gc target/performance/history-final.jfr
```

Raw `.jfr` dan summary k6 berada di `target/performance/` dan tidak di-commit karena merupakan
artefak mesin/run tertentu. Angka hasil ekstraksi yang dapat direview tersimpan di
[`jfr-evidence.txt`](../assets/performance/jfr-evidence.txt).

Tiga recording dipakai:

| Recording | Workload | Durasi event | Tujuan |
|---|---|---:|---|
| `history-final.jfr` | history, 4 VU, 15 s warm-up + 30 s measurement | 1 m 26 s | read path normal |
| `transfer-final.jfr` | transfer isolated, 4 VU, 15 s + 30 s | 1 m 12 s | write path normal |
| `lock-contention-final.jfr` | transfer shared, 40 VU, 5 s + 20 s | 1 m 34 s | row lock dan pool pressure |

Recording mencakup setup API, warm-up, measurement, dan beberapa detik attach/dump. Karena itu CPU
dan allocation rate JFR adalah karakter lifecycle, sedangkan latency/throughput resmi tetap memakai
window measurement k6 pada baseline.

## CPU hotspot

| Flow | JVM user avg | JVM system avg | Machine avg | Hot method teratas |
|---|---:|---:|---:|---|
| History | 6,81% | 3,90% | 56,71% | `BCrypt.encipher`, 1,72% sample |
| Transfer isolated | 7,26% | 4,42% | 66,12% | `BCrypt.encipher`, 2,43% sample |
| Transfer shared | 1,96% | 1,54% | 44,68% | `BCrypt.encipher`, 8,38% sample |

BCrypt berasal dari register/login pada setup dan bukan loop request terukur. Tidak ada business
method yang mendominasi CPU sample; method lain masing-masing di bawah 1,5%. Pada contention run,
CPU JVM justru turun karena request lebih banyak menunggu connection/row lock. Kesimpulannya flow
yang diukur lebih bersifat I/O-bound daripada CPU-bound pada concurrency ini.

Sepuluh executor HTTP membagi CPU dan allocation relatif merata pada baseline. Thread RabbitMQ,
Redis, outbox scheduler, dan AMQP connection tetap aktif. Pada transfer isolated, consumer RabbitMQ
menjadi thread CPU tertinggi (sekitar 1,20% user untuk audit dan 0,71% untuk notification), tetapi
tidak mendominasi CPU mesin.

## Heap, allocation, dan GC

| Flow | Allocation lifecycle | Heap sebelum/sesudah GC | Pause GC maksimum | Full GC/allocation stall |
|---|---:|---:|---:|---|
| History | 20,91 MiB/s | sekitar 98/50 MiB | 12,6 ms | tidak ada |
| Transfer isolated | 13,11 MiB/s | sekitar 98/50 MiB | 13,0 ms | tidak ada |
| Transfer shared | tidak dipakai sebagai baseline | sekitar 110/59–64 MiB | 19,0 ms | tidak ada |

Snapshot baseline juga menunjukkan heap committed sekitar 106–108 MiB dan tidak tumbuh terus
setelah GC. Recording singkat ini tidak membuktikan tidak adanya memory leak; soak test berdurasi
jam tetap diperlukan untuk kesimpulan tersebut.

Allocation history didominasi `Instant.create` (48,71%) dan `QueryOptionsImpl` (5,32%). Transfer
lebih tersebar pada iterator/collection, servlet request object, JDBC timestamp, dan observation
Micrometer. Nilai ini belum layak menjadi target micro-optimization: GC pause rendah, tidak ada
stall, dan database/serialization I/O lebih relevan pada latency end-to-end.

## Thread state dan I/O baseline

Baseline mempertahankan 42–43 active thread dan peak 43. JFR tidak merekam event
`JavaMonitorEnter`, sehingga view `contention-by-site` dan `contention-by-thread` kosong. Ini berarti
tidak ada intrinsic Java monitor contention yang melewati threshold JFR; bukan berarti request tidak
pernah menunggu dependency.

| Flow | Socket read | Average | p99 | Terpanjang | Java thread park average |
|---|---:|---:|---:|---:|---:|
| History | 18.113 | 2,17 ms | 6,06 ms | 801 ms | 97,4 ms |
| Transfer isolated | 15.426 | 3,81 ms | 9,73 ms | 6,64 s | 249 ms |

Hampir seluruh socket read menuju `localhost`, yaitu PostgreSQL/Redis/RabbitMQ pada stack lokal.
Park panjang umumnya berasal dari thread pool, scheduler, dan listener yang idle; ia tidak dapat
langsung dibaca sebagai latency request. Thread dump diperlukan untuk menghubungkan wait dengan
stack tertentu.

## Account lock dan Hikari contention

Mode profiling berikut sengaja membuat seluruh VU memakai satu pasangan account:

```bash
TRANSFER_ACCOUNT_MODE=shared VUS=40 WARMUP_DURATION=5s DURATION=20s
```

Mode default tetap `isolated`; hasil shared tidak dibandingkan sebagai throughput baseline. Selama
run, endpoint metrics diakses dengan role `ADMIN` dan `pg_stat_activity` disampling dari container.

| Bukti | Hasil |
|---|---|
| Hikari configured max | 10 connection |
| Hikari active peak | 10 |
| Hikari pending peak | 63 |
| Hikari timeout counter | 0 |
| PostgreSQL wait | sampai 8 `Lock:tuple` + 1 `Lock:transactionid` bersamaan |
| Thread dump | 31 HTTP thread parked di `ConcurrentBag.borrow` |
| Thread dump | 10 HTTP thread pada `PGStream.receive` |
| HTTP thread state | 31 waiting/timed-waiting, 10 runnable |
| Active Java thread | baseline 42–43; contention peak 113 |

JFR monitor-contention view tetap kosong karena kedua jenis wait utama bukan intrinsic monitor:

- thread tanpa connection park melalui AQS/Hikari `ConcurrentBag`;
- thread yang sudah memiliki connection menunggu PostgreSQL row/transaction lock melalui socket
  read JDBC.

JFR contention recording memperlihatkan socket-read average 20,3 ms, p99 381 ms, maksimum 15,0 s,
serta thread-park average 1,15 s. Pool memberikan backpressure tanpa timeout pada run pendek ini.
Menaikkan ukuran pool bukan perbaikan otomatis: ia dapat menambah transaksi yang bersaing pada row
yang sama. Strategi lock deterministik dan timeout 5 detik tetap lebih penting; konfigurasi pool
baru layak diubah setelah workload production-like menunjukkan saturasi pada account yang berbeda.

Tomcat membuat thread tambahan hingga peak 113 untuk menangani 40 VU plus sampler management dan
menahannya setidaknya sampai recording selesai. Nilai ini masih di bawah default maximum, tetapi
memory/thread stack dapat menjadi risiko pada lonjakan jauh lebih besar. Production deployment
sebaiknya menetapkan limit Tomcat dan load-shedding berdasarkan kapasitas terukur.

## Kesimpulan

- Tidak ada CPU hotspot business logic yang membenarkan refactor performa sekarang.
- Heap stabil untuk run pendek, tanpa full GC atau allocation stall; allocation history layak
  dipantau, belum layak dioptimasi secara spekulatif.
- Baseline 4 VU tidak menunjukkan Java monitor atau connection-pool contention.
- Shared-account contention terbukti terjadi di PostgreSQL dan mendorong antrian Hikari/Tomcat;
  perilakunya aman pada eksperimen ini karena tidak ada timeout/partial result.
- Prioritas operasional adalah memonitor Hikari pending, PostgreSQL lock wait, outbox backlog, dan
  HTTP thread count; bukan sekadar memperbesar pool.

## Keterbatasan

- JFR memakai sampling dan menambah overhead kecil.
- Semua dependency dan load generator berbagi satu laptop Windows/Docker Desktop.
- Contention run bersifat sengaja ekstrem dan sampler management ikut menambah request HTTP.
- Recording kurang dari dua menit tidak menggantikan soak test atau leak analysis.
- Tidak ada TLS, network latency, cgroup limit per service, atau noisy-neighbor production.
