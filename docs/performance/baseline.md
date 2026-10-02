# Performance Baseline

Baseline ini dibuat pada 3 Oktober 2026 untuk memberi titik pembanding yang dapat diulang, bukan
untuk menyatakan kapasitas production atau SLA. Semua request menggunakan user terautentikasi dan
data sintetis pada environment development lokal.

## Environment

| Komponen | Versi atau kapasitas |
|---|---|
| Host | Windows 11 Pro 10.0.26200 build 26200 |
| CPU | AMD Ryzen 3 5300U, 4 core/8 logical processor |
| RAM host | 11,35 GiB |
| Java | Oracle HotSpot 25.0.4.1 LTS |
| Spring Boot | 4.1.1 |
| Docker Desktop Engine | 29.7.2, 8 CPU dan 5,47 GiB dialokasikan |
| k6 | `grafana/k6:2.3.0`, digest `sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34` |
| PostgreSQL | `postgres:18`, server 18.6 |
| Redis | `redis:8.2-alpine`, server 8.2.9 |
| RabbitMQ | `rabbitmq:4.1-management-alpine`, server 4.1.8 |

Aplikasi berjalan sebagai JVM host. PostgreSQL, Redis, RabbitMQ, dan k6 berjalan di Docker Desktop
yang sama tanpa limit per-container tambahan. PostgreSQL dipublikasikan pada port sementara 15432
karena port host 5432 sudah dipakai service lain. k6 mengakses API melalui
`host.docker.internal:8080`; traffic tidak memakai TLS atau jaringan eksternal.

Request log `com.example.ledgerbank.common` dan Hibernate SQL log dimatikan hanya untuk run
pengukuran agar console I/O tidak menjadi bottleneck buatan. Fitur logging tetap aktif pada
konfigurasi aplikasi normal. Raw summary k6 dan recording JFR disimpan di `target/performance/`
dan sengaja tidak di-commit.

## Metode

Script dan command lengkap tersedia di [`performance/`](../../performance/README.md). Parameter
yang dipakai untuk kedua flow:

```text
VUS=4
WARMUP_DURATION=15s
DURATION=30s
REQUEST_TIMEOUT=30s
```

Urutan reproduksi:

1. Jalankan Compose dengan PostgreSQL host port 15432.
2. Jalankan aplikasi dengan `DB_URL=jdbc:postgresql://localhost:15432/ledgerbank`, user
   `ledgerbank`, dan password yang sama dengan `DB_PASSWORD` Compose.
3. Tunggu health API mengembalikan 200.
4. Jalankan workload `history`, kemudian `transfer`, secara terpisah.
5. Hitung throughput dari `ledgerbank_requests count / 30 detik`. Rate counter pada summary k6
   mencakup setup dan warm-up sehingga tidak dipakai sebagai throughput measurement.

Sebelum run final, database berisi 64 account, 12.556 row history, dan 5.247 transfer sintetis dari
validasi workload. Setup history menambahkan satu user, 16 account, dan tepat 1.000 entry pada
account yang dibaca. Setup transfer memakai user lain dan delapan pasangan account; setiap VU
mendapat pasangan sendiri agar baseline write tidak mengukur kontensi lock buatan. Setelah kedua
run, database berisi 96 account, 19.327 row history, dan 8.117 transfer.

## Hasil HTTP

| Flow | Request measurement | Throughput | Error | Average | p50 | p95 | p99 | Max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| History page 0, size 20 | 6.558 | 218,60 req/s | 0,00% | 17,84 ms | 15,93 ms | 27,77 ms | 41,28 ms | 78,09 ms |
| Transfer | 1.954 | 65,13 req/s | 0,00% | 60,66 ms | 57,17 ms | 87,76 ms | 129,79 ms | 193,59 ms |

Transfer lebih lambat secara wajar karena satu request mengunci dua account secara deterministik,
menulis transfer, dua history row, durable idempotency, dan outbox event dalam satu transaksi.
Consumer RabbitMQ dan publisher outbox tetap aktif selama pengukuran.

## Query count dan execution plan

Satu request history terautentikasi menjalankan tiga query PostgreSQL:

1. mengambil account untuk validasi keberadaan dan ownership;
2. mengambil maksimum 20 history row;
3. menghitung total row untuk response `Page`.

DTO hanya memakai kolom scalar/UUID sehingga tidak ada lazy association atau N+1 query. Session
authentication dibaca dari Redis dan tidak menambah query SQL.

`EXPLAIN (ANALYZE, BUFFERS)` dijalankan setelah `ANALYZE account_transactions` pada total 19.327
row, dengan account target berisi 1.000 row:

| Query | Plan utama | Row dibaca | Shared buffer hit | Execution time |
|---|---|---:|---:|---:|
| Page 0, limit 20 | `Index Scan idx_transactions_account_history` | 20 | 4 | 0,074 ms |
| Count | `Index Only Scan idx_transactions_account_history` | 1.000 | 17 | 0,658 ms |
| Offset 900, limit 20 | Bitmap index/heap scan + quicksort | 1.000, sort 920 | 29 | 0,629 ms |

Index `(account_id, created_at DESC, id DESC)` sudah melayani halaman pertama tanpa sort dan tidak
perlu diduplikasi. Offset dalam mulai membaca dan menyortir lebih banyak row; keyset pagination
menjadi kandidat jika account nyata tumbuh jauh di atas dataset ini, tetapi 0,629 ms belum menjadi
bukti untuk mengubah kontrak API sekarang.

## JVM, allocation, dan GC

Snapshot `Get-Process`, `jcmd GC.heap_info`, dan `jstat -gcutil` diambil sebelum/sesudah setiap run
final. CPU adalah waktu proses JVM selama seluruh lifecycle k6 (setup + warm-up + measurement),
bukan persentase seluruh mesin.

| Flow | Durasi lifecycle | CPU JVM | Rata-rata logical core | Working set | Heap used | Young GC | Full GC |
|---|---:|---:|---:|---:|---:|---:|---:|
| History | 65,270 s | +69,078 s | 1,06 (sekitar 13,2% dari 8 logical CPU) | 319,64 -> 352,43 MiB | 56,73 -> 94,45 MiB | +43; 0,151 s | 0 |
| Transfer | 46,777 s | +62,922 s | 1,35 (sekitar 16,8% dari 8 logical CPU) | 352,43 -> 354,45 MiB | 94,45 -> 60,07 MiB | +20; 0,077 s | 0 |

JFR `profile` direkam pada pengulangan dengan parameter dan logging yang sama. Karena recording
mencakup setup, warm-up, dan measurement, allocation rate berikut adalah rate lifecycle dan tidak
boleh dibandingkan dengan throughput measurement tanpa konteks:

| Flow | Sampel JFR | Allocation rate | Heap sebelum GC | Heap sesudah GC | Pause terpanjang |
|---|---:|---:|---:|---:|---:|
| History | 85,527 s | 20,91 MiB/s | sekitar 98 MiB | sekitar 50 MiB | 12,6 ms |
| Transfer | 71,966 s | 13,11 MiB/s | sekitar 98 MiB | sekitar 50 MiB | 13,0 ms |

Tidak ada allocation stall atau full GC pada snapshot final. JFR menamai concurrent G1 old cycle
sebagai `Old Garbage Collection`; pause terpanjang di atas tetap singkat dan heap kembali ke sekitar
50 MiB. Allocation pressure history paling besar berada pada pembuatan `Instant` (48,71%) dan
`QueryOptionsImpl` (5,32%). Transfer lebih tersebar pada iterator/collection serta observation
Micrometer. Temuan hotspot dan thread dibahas lebih lanjut pada `jvm-analysis.md`.

## Interpretasi dan keterbatasan

- Hasil adalah satu baseline workstation, bukan capacity test, soak test, atau bukti SLA.
- Load generator dan dependency berbagi host sehingga angka mencakup kompetisi resource lokal.
- HTTP loopback tanpa TLS membuat hasil tidak mewakili latency jaringan production.
- Dataset history per account hanya 1.000 row; risiko offset pagination pada jutaan row belum diuji.
- Transfer baseline sengaja menghindari account contention. Perilaku lock bersaing dibuktikan oleh
  integration/concurrency test, bukan angka ini.
- Tidak ada optimasi kode atau index yang dilakukan: plan page pertama sudah memakai index dan
  baseline belum menunjukkan bottleneck database yang membenarkan migration baru.
