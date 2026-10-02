# LedgerBank — PROJECT_SPEC Milestone 3
## Reliability, Testing, Performance, Observability, dan Portfolio Readiness

## 1. Posisi Milestone

Milestone 3 adalah kelanjutan langsung dari fondasi yang dibangun pada Milestone 1 dan diperkuat pada Milestone 2. Milestone ini tidak mengganti database, arsitektur, atau kontrak bisnis yang sudah ada.

Dokumen ini memakai implementasi aktual pada akhir Milestone 2 sebagai baseline. Jika proposal historis Milestone 1 atau 2 berbeda dari schema, konfigurasi, dan dokumentasi yang sudah terverifikasi, kondisi as-built tersebut yang menjadi acuan Milestone 3. Karena itu referensi MariaDB pada rancangan awal Milestone 2 tidak dibawa ke milestone ini; implementasi tetap menggunakan PostgreSQL 18 seperti Milestone 1.

Baseline yang harus dipertahankan:

| Sumber | Baseline |
|---|---|
| Milestone 1 | Java 25, Spring Boot 4, Maven, modular monolith, PostgreSQL 18, Flyway, customer, account, deposit, withdrawal, transfer atomik, dan histori transaksi |
| Milestone 2 | Spring Security, opaque bearer session di Redis, role dan ownership, OpenAPI, idempotensi transfer di Redis + PostgreSQL, RabbitMQ, notification, audit log, serta environment berbasis Docker Compose |
| Milestone 3 | Pembuktian invariant M1/M2 melalui integration dan concurrency test, transactional outbox, observability, performance evidence, automation, CI, quality gate, dan dokumentasi portfolio |

PostgreSQL 18 tetap menjadi satu-satunya relational database dan source of truth. Redis tetap digunakan untuk session, cache/koordinasi idempotensi, dan lock ber-TTL. RabbitMQ tetap digunakan untuk event asynchronous. Milestone 3 tidak memperkenalkan MariaDB atau memecah aplikasi menjadi microservices.

Fokus milestone ini adalah membuktikan dan meningkatkan kualitas fondasi tersebut melalui:

- integration dan concurrency testing yang reproducible,
- keandalan pengiriman event setelah database commit,
- observability dan troubleshooting,
- performance measurement dan optimasi berbasis bukti,
- automation, Linux compatibility, CI, dan quality checks,
- refactoring terukur serta dokumentasi portfolio final.

Milestone 3 tidak memperluas domain dengan produk atau endpoint banking baru. Perubahan pada milestone ini menguatkan reliability, pembuktian, operability, dan maintainability dari fitur M1/M2 yang sudah ada.

---

## 2. Prinsip Pengerjaan

1. Pertahankan invariant Milestone 1: mutasi saldo, histori transaksi, transfer, dan durable idempotency record harus konsisten secara transaksional.
2. Pertahankan security boundary Milestone 2: authentication, role, dan ownership tetap diuji melalui API dan service layer.
3. PostgreSQL adalah sumber kebenaran. Redis dan RabbitMQ tidak boleh menjadi satu-satunya tempat penyimpanan data bisnis yang harus durable.
4. Ukur sebelum mengoptimasi. Simpan baseline, perubahan, dan hasil perbandingan.
5. Uji perilaku yang terlihat oleh client dan invariant data; hindari test yang terlalu terikat pada detail implementasi.
6. Jangan menambahkan ulang mekanisme yang sudah ada. Pessimistic locking dan index histori transaksi yang telah dibuat menjadi baseline untuk diverifikasi.
7. Setiap perubahan reliability atau performance harus memiliki test regresi atau bukti pengukuran yang relevan.

Urutan pengerjaan:

```text
testing foundation -> integration/concurrency proof -> reliable event delivery
-> observability -> performance/profiling -> optimization -> automation/CI/release
```

---

## 3. Tahap 1 — Testing Strategy dan Konvensi

Pisahkan testing menjadi:

```text
unit test          business logic terisolasi
MVC/security test  HTTP contract, authentication, authorization, validation
repository test    mapping JPA, query, constraint, dan locking PostgreSQL
integration test   alur lintas Spring Boot, PostgreSQL, Redis, dan RabbitMQ
concurrency test   invariant saldo, idempotensi, dan perilaku lock
performance test   baseline dan perbandingan; bukan bagian unit test harian
```

Konvensi minimum:

- Surefire menjalankan test cepat dengan pola `*Test`.
- Failsafe menjalankan integration test dengan pola `*IT` melalui profile Maven `integration`.
- Test tidak bergantung pada urutan eksekusi atau data dari test lain.
- Waktu, UUID, dan komponen eksternal dibuat deterministik jika diperlukan.
- Integration test tidak menggunakan infrastructure development atau database bersama.
- Dokumentasikan perintah untuk menjalankan test cepat dan seluruh test.

### Git Commit

```bash
git commit -m "test: define test suites and execution conventions"
git commit -m "chore(test): configure unit and integration test lifecycle"
```

---

## 4. Tahap 2 — Testcontainers Foundation

Gunakan Testcontainers untuk integration tests.

Gunakan service dan versi mayor yang sama seperti runtime Milestone 2:

```text
PostgreSQL 18
Redis 8.2
RabbitMQ 4.1
```

Container harus menyediakan property secara dinamis kepada Spring. Flyway wajib menjalankan migration yang sama dengan runtime dan Hibernate tetap menggunakan schema validation.

Target:

- test tidak menggunakan database atau infrastructure development bersama,
- image version dipin agar hasil lokal dan CI konsisten,
- startup readiness menunggu service benar-benar siap,
- queue, cache, dan data bisnis dibersihkan atau diisolasi antartest,
- kegagalan karena Docker tidak tersedia memiliki pesan yang jelas.

Jangan menggunakan MariaDB container. Seluruh schema, migration, constraint, dan query LedgerBank ditujukan untuk PostgreSQL.

### Git Commit

```bash
git commit -m "test(integration): add PostgreSQL 18 Testcontainers foundation"
git commit -m "test(integration): add Redis and RabbitMQ containers"
git commit -m "test(integration): verify Flyway schema on containerized PostgreSQL"
```

---

## 5. Tahap 3 — Banking API Integration Tests

Uji alur nyata melalui HTTP dan security filter. Service-level test tetap berguna, tetapi tidak menggantikan pembuktian authentication, authorization, serialization, transaction, dan infrastructure secara bersama.

Scenario minimum:

```text
register -> login -> create account -> deposit -> transfer -> transaction history
invalid or expired bearer token
customer cannot read or mutate another customer's account
successful withdrawal and insufficient balance
successful transfer with debit and credit history
rollback when transfer persistence fails
same idempotency key and same payload returns the original result
same idempotency key and different payload is rejected
Redis cache miss recovers the idempotent result from PostgreSQL
rolled-back transfer does not produce a completion event
successful transfer eventually creates notification and audit records
RabbitMQ redelivery does not duplicate notification or audit records
```

Karena event diproses asynchronous, gunakan bounded polling seperti Awaitility, bukan `Thread.sleep` berdurasi tetap. Assertion harus memeriksa hasil akhir dan jumlah record agar duplicate side effect terdeteksi.

### Git Commit

```bash
git commit -m "test(integration): add authenticated banking API flow"
git commit -m "test(integration): verify ownership and transfer rollback"
git commit -m "test(integration): verify durable transfer idempotency"
git commit -m "test(integration): verify notification and audit consumption"
```

---

## 6. Tahap 4 — Concurrency dan Locking Verification

Milestone 2 sudah menggunakan pessimistic write lock untuk mutasi saldo dan urutan UUID yang stabil saat mengunci dua rekening. Milestone 3 harus membuktikan perilaku tersebut dengan test, bukan menganggapnya benar hanya karena anotasi atau query lock sudah ada.

Scenario utama:

```text
balance awal = Rp100.000

request A = Rp80.000
request B = Rp80.000
```

Target:

- tepat satu transfer berhasil,
- satu transfer ditolak karena saldo tidak cukup,
- saldo sumber tidak pernah negatif,
- saldo akhir dan histori sesuai dengan transfer yang committed.

Tambahkan scenario:

- dua request simultan dengan idempotency key yang sama menghasilkan satu transfer,
- deposit/withdrawal simultan tidak menyebabkan lost update,
- transfer A→B dan B→A tidak merusak saldo atau histori,
- lock timeout/deadlock, jika terjadi, menghasilkan error aman tanpa partial update.

Gunakan synchronization barrier/latch agar request benar-benar overlap. Beri timeout agar CI tidak menggantung. Jika test menemukan cacat, commit reproduksi lebih dahulu lalu perbaikan. Jika implementasi saat ini lulus, tidak perlu membuat commit `fix` buatan.

### Git Commit

```bash
git commit -m "test(concurrency): verify concurrent transfer balance invariant"
git commit -m "test(concurrency): verify idempotent concurrent requests"
git commit -m "test(concurrency): verify deterministic account lock ordering"
```

Commit `fix(account): prevent concurrent balance inconsistency` hanya dibuat bila ada bug nyata.

---

## 7. Tahap 5 — Reliable Event Delivery dengan Transactional Outbox

Milestone 2 menerbitkan event melalui listener `AFTER_COMMIT`. Cara ini mencegah rollback menghasilkan event sukses, tetapi masih memiliki celah jika proses berhenti setelah database commit dan sebelum publish ke RabbitMQ selesai.

Tutup celah tersebut dengan transactional outbox:

```text
database transaction
  +-- update aggregate bisnis
  +-- simpan histori/idempotency
  +-- simpan outbox event
commit
  |
outbox publisher
  |
RabbitMQ publisher confirm
  |
tandai event terkirim
```

Ketentuan minimum:

- outbox record dibuat dalam transaction yang sama dengan perubahan bisnis,
- publisher melakukan retry dengan backoff dan batas yang terukur,
- event hanya ditandai terkirim setelah broker mengonfirmasi publish,
- duplicate delivery tetap diizinkan oleh model at-least-once,
- notification dan audit consumer tetap idempotent berdasarkan `eventId`,
- payload tidak menyimpan password, bearer token, atau secret,
- tersedia retention/cleanup untuk record yang sudah terkirim,
- kegagalan dan backlog dapat diamati melalui log dan metrics.

Integration test minimum:

- commit bisnis selalu menghasilkan outbox record,
- rollback bisnis tidak menghasilkan outbox record,
- RabbitMQ unavailable tidak membatalkan transfer yang sudah committed,
- event pending terkirim setelah RabbitMQ pulih,
- retry/redelivery tidak menggandakan notification atau audit.

### Git Commit

```bash
git commit -m "feat(event): add transactional outbox schema and model"
git commit -m "refactor(event): persist banking events in business transactions"
git commit -m "feat(event): publish pending outbox events with retry"
git commit -m "test(integration): verify outbox recovery and idempotent consumers"
```

---

## 8. Tahap 6 — Actuator dan Observability

Tambahkan Spring Boot Actuator dan expose hanya endpoint yang diperlukan:

```text
health
metrics
info
prometheus, jika registry Prometheus dipilih
```

Ketentuan keamanan:

- detail health tidak dibuka ke client anonymous,
- endpoint selain daftar yang disetujui tetap tidak diexpose,
- secret, credential, environment mentah, dan token tidak muncul pada response,
- authorization endpoint management didefinisikan secara eksplisit.

Tambahkan observability yang membantu operasi:

- correlation/request ID pada log,
- structured logging tanpa data sensitif,
- timer dan counter transfer berhasil/gagal,
- request latency dan error rate,
- metrik koneksi PostgreSQL, Redis, dan RabbitMQ yang relevan,
- metrik backlog, retry, dan failure pada outbox,
- health indicator dependency dengan semantics readiness yang jelas.

### Git Commit

```bash
git commit -m "feat(observability): add Spring Boot Actuator"
git commit -m "chore(observability): secure management endpoints"
git commit -m "feat(observability): add banking and outbox metrics"
git commit -m "test(observability): verify actuator access and secret redaction"
```

---

## 9. Tahap 7 — Performance Baseline

Pilih sedikitnya dua flow representatif:

```http
GET /api/v1/accounts/{accountId}/transactions?page=0&size=20
POST /api/v1/transfers
```

Gunakan user terautentikasi dan dataset yang terdokumentasi. Pisahkan pengukuran read-heavy dari write flow agar hasil mudah diinterpretasikan. Buat baseline sebelum optimasi.

Catat:

```text
tool dan command
hardware/runtime/container limit
Java dan image version
ukuran dataset
warm-up, durasi, dan concurrency level
throughput dan error rate
average, p50, p95, dan p99 latency
query count dan query plan
CPU, heap, allocation, dan GC activity
```

Buat:

```text
docs/performance/baseline.md
```

Performance test tidak boleh menggunakan production data atau menonaktifkan authentication hanya untuk mendapat angka lebih baik.

### Git Commit

```bash
git commit -m "test(performance): add reproducible workload"
git commit -m "docs(performance): record application performance baseline"
```

---

## 10. Tahap 8 — JVM dan Thread Profiling

Gunakan Java Flight Recorder, VisualVM, atau JConsole untuk menganalisis workload yang sama dengan baseline.

```text
CPU hotspot
heap usage
allocation rate dan garbage collection
thread state dan contention
database connection pool
blocked thread ketika account lock bersaing
```

Dokumentasikan metode, bukti, interpretasi, dan keterbatasan hasil pada:

```text
docs/performance/jvm-analysis.md
docs/assets/performance/
```

Screenshot boleh menjadi bukti pendukung, tetapi kesimpulan utama harus ditulis agar dapat direview tanpa membuka tool profiling.

### Git Commit

```bash
git commit -m "docs(performance): add JVM and thread profiling analysis"
```

---

## 11. Tahap 9 — Database Performance

Analisis query histori transaksi menggunakan PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` pada dataset representatif.

Schema saat ini sudah memiliki index:

```sql
CREATE INDEX idx_transactions_account_history
ON account_transactions(account_id, created_at DESC, id DESC);
```

Tujuan tahap ini adalah memverifikasi bahwa pagination dan urutan query memakai index tersebut, bukan membuat index dengan fungsi sama untuk kedua kalinya.

Periksa:

- query plan dan jumlah row yang discan,
- N+1 query,
- offset pagination pada halaman dalam,
- kebutuhan keyset/cursor pagination,
- ukuran result set dan DTO projection,
- pengaruh index terhadap performa write.

Tambahkan migration index baru atau ubah query hanya jika baseline menunjukkan kebutuhan. Bandingkan sebelum/sesudah dengan dataset, workload, dan environment yang sama.

### Git Commit

```bash
git commit -m "perf(transaction): document transaction history query plan"
git commit -m "perf(transaction): optimize measured transaction history bottleneck"
git commit -m "test(performance): compare transaction query before and after"
```

Commit optimasi hanya dibuat jika ada perubahan yang dibenarkan hasil pengukuran.

---

## 12. Tahap 10 — Technical Debt dan Refactoring

Buat inventory di:

```text
docs/technical-debt.md
```

Setiap item minimum memuat masalah dan bukti, dampak/risiko, prioritas, rencana perbaikan, serta status atau alasan ditunda.

Pilih 2–3 masalah bernilai tertinggi berdasarkan temuan test, profiling, atau review kode. Kandidat antara lain:

- coupling event publisher setelah penambahan outbox,
- duplicate validation,
- error response yang belum konsisten,
- retry policy tanpa observability,
- service dengan terlalu banyak tanggung jawab.

Refactoring tidak boleh mengubah kontrak API secara diam-diam. Test relevan harus tetap lulus sebelum dan sesudah perubahan.

### Git Commit

```bash
git commit -m "docs: record prioritized technical debt"
git commit -m "refactor(transfer): simplify validated transfer workflow"
git commit -m "refactor(event): separate outbox persistence and delivery"
```

Nama commit akhir mengikuti masalah nyata yang dipilih.

---

## 13. Tahap 11 — Automation dan Linux Compatibility

Tambahkan script minimum:

```text
scripts/
|-- dev.sh
|-- test.sh
|-- integration-test.sh
|-- health-check.sh
+-- db-reset.sh
```

Fungsi:

- `dev.sh`: menjalankan infrastructure development dan menunggu health check.
- `test.sh`: menjalankan test cepat.
- `integration-test.sh`: menjalankan integration profile yang membutuhkan Docker.
- `health-check.sh`: memeriksa aplikasi/dependency tanpa mencetak secret.
- `db-reset.sh`: mereset hanya database development lokal dengan konfirmasi eksplisit.

Ketentuan keamanan `db-reset.sh`:

- tolak environment selain local/development,
- tampilkan target yang akan dihapus,
- gunakan nama Compose project/database yang eksplisit,
- jangan menyentuh volume atau database di luar LedgerBank.

Seluruh script harus memakai Bash dan shebang yang benar, line ending LF, berhenti ketika command gagal, serta bekerja dari lokasi mana pun di repository. Dokumentasikan Java 25, Maven Wrapper, Docker/Compose, Bash, environment variables, dan file permission yang diperlukan.

Untuk developer Windows, dokumentasikan penggunaan Git Bash atau WSL tanpa menjadikan path Windows sebagai asumsi di script.

### Git Commit

```bash
git commit -m "chore(scripts): add development and test automation"
git commit -m "chore(scripts): add safe local health and database utilities"
git commit -m "chore(linux): verify Bash and line-ending compatibility"
```

---

## 14. Tahap 12 — CI dan Code Quality

Tambahkan GitHub Actions dengan Java 25 dan Maven Wrapper.

Pipeline minimum:

```text
checkout
validate Maven Wrapper
setup Java 25 + dependency cache
compile
unit/MVC test
integration test dengan Testcontainers
package
upload test reports ketika gagal
```

Runner integration test harus memiliki Docker. Tambahkan timeout dan jangan bergantung pada port host tetap karena Testcontainers menyediakan port dinamis.

Quality checks yang relevan:

```text
compiler warnings
formatting check
static analysis dengan rule yang disepakati
coverage report untuk melihat gap
secret/dependency check bila tool tersedia
```

Coverage diprioritaskan pada transfer dan mutasi saldo, authorization/ownership, idempotency, outbox, consumer idempotency, dan error handling. Jangan mengejar 100% coverage atau membuat test tanpa assertion bermakna hanya untuk menaikkan angka.

### Git Commit

```bash
git commit -m "ci: add Java 25 Maven verification workflow"
git commit -m "ci: run Testcontainers integration suite"
git commit -m "chore(quality): add formatting and static analysis checks"
git commit -m "test: improve critical banking path coverage"
```

---

## 15. Tahap 13 — Dokumentasi Final dan Release

README final harus mencakup:

```text
project purpose and scope
architecture diagram
technology stack and exact runtime requirements
feature list from Milestone 1–3
database and transaction consistency
authentication, role, and ownership model
REST API and Swagger/OpenAPI
Redis session and idempotency design
RabbitMQ and transactional outbox flow
notification and audit idempotency
testing strategy and commands
observability and performance findings
Docker and Linux setup
CI and quality checks
known limitations and future improvements
```

Pastikan dokumen arsitektur, database, API, security, Redis, events, implementation status, dan technical debt tidak saling bertentangan.

Tambahkan bagian `Why this project exists` yang menjelaskan bahwa LedgerBank adalah simulasi/portfolio backend engineering. Jangan menggambarkannya sebagai sistem perbankan production atau menyatakan compliance/security yang belum dibuktikan.

### Verifikasi dan Release

Sebelum release jalankan:

```bash
./mvnw clean verify -Pintegration,quality
```

Release `v1.0.0` hanya dibuat jika working tree release bersih, CI lulus, migration dapat diterapkan dari database kosong, seluruh dokumen sesuai implementasi, dan tidak ada credential/secret di repository.

Tag dan push dilakukan secara sadar setelah verifikasi:

```bash
git tag -a v1.0.0 -m "LedgerBank portfolio release v1.0.0"
git push origin v1.0.0
```

### Git Commit

```bash
git commit -m "docs: finalize LedgerBank portfolio documentation"
git commit -m "chore(release): prepare LedgerBank v1.0.0"
```

---

## 16. Definition of Done

Milestone 3 selesai jika:

- PostgreSQL 18, Redis, dan RabbitMQ integration test berjalan dengan Testcontainers,
- unit dan integration test memiliki lifecycle Maven terpisah dan terdokumentasi,
- authenticated banking flow serta ownership diuji end-to-end,
- rollback transfer, durable idempotency, notification, dan audit diuji lintas infrastructure,
- concurrency test membuktikan saldo tidak negatif, tidak ada lost update, dan transfer tidak ganda,
- strategi locking yang sudah ada tervalidasi atau diperbaiki berdasarkan test,
- transactional outbox menutup celah database commit–message publish,
- retry/redelivery event tidak menggandakan notification atau audit,
- Actuator diexpose secara minimal dan aman,
- metrik/log cukup untuk menelusuri request, transfer, dan backlog outbox,
- performance baseline reproducible serta JVM/thread profiling tersedia,
- query history dianalisis dengan PostgreSQL query plan,
- optimasi database, bila ada, didukung perbandingan sebelum/sesudah,
- technical debt diprioritaskan dan 2–3 item bernilai tinggi ditangani,
- automation aman untuk development dan test tersedia,
- workflow Linux serta Windows/WSL yang didukung terdokumentasi,
- CI menjalankan unit test, integration test, package, dan quality checks,
- README dan seluruh dokumen konsisten dengan implementasi,
- seluruh verification lulus sebelum tag `v1.0.0` dibuat.

---

## 17. Target Histori Git

Gunakan Conventional Commits. Jumlah commit bukan target utama; satu commit harus mewakili satu perubahan logis yang dapat di-build dan diverifikasi.

Contoh urutan realistis:

```text
test: define test suites and execution conventions
chore(test): configure unit and integration test lifecycle
test(integration): add PostgreSQL 18 Testcontainers foundation
test(integration): add Redis and RabbitMQ containers
test(integration): add authenticated banking API flow
test(integration): verify ownership and transfer rollback
test(integration): verify durable transfer idempotency
test(integration): verify notification and audit consumption
test(concurrency): verify concurrent transfer balance invariant
test(concurrency): verify idempotent concurrent requests
test(concurrency): verify deterministic account lock ordering
feat(event): add transactional outbox schema and model
refactor(event): persist banking events in business transactions
feat(event): publish pending outbox events with retry
test(integration): verify outbox recovery and idempotent consumers
feat(observability): add Spring Boot Actuator
chore(observability): secure management endpoints
feat(observability): add banking and outbox metrics
test(performance): add reproducible workload
docs(performance): record application performance baseline
docs(performance): add JVM and thread profiling analysis
perf(transaction): document transaction history query plan
docs: record prioritized technical debt
chore(scripts): add development and test automation
chore(linux): verify Bash and line-ending compatibility
ci: add Java 25 Maven verification workflow
ci: run Testcontainers integration suite
chore(quality): add formatting and static analysis checks
docs: finalize LedgerBank portfolio documentation
chore(release): prepare LedgerBank v1.0.0
```

Commit `fix`, `perf`, atau refactoring tambahan dibuat hanya ketika ada bug, bottleneck, atau technical debt nyata.

---

## 18. Prinsip Commit untuk Seluruh Project

Gunakan Conventional Commits.

Prefix utama:

```text
feat      fitur baru
fix       bug fix
test      testing
refactor  perubahan struktur tanpa perubahan behavior utama
perf      performance optimization
docs      dokumentasi
chore     tooling/configuration
ci        CI/CD
```

Contoh:

```text
feat(transfer): implement atomic internal transfer
fix(account): prevent negative balance during concurrent transfer
test(transfer): add insufficient balance scenario
refactor(transfer): extract transfer validation service
perf(transaction): optimize transaction history query
docs(api): document transfer endpoint
chore(docker): add RabbitMQ service
ci: add Maven test workflow
```

Setiap commit sebaiknya:

```text
satu tujuan
dapat dipahami sendiri
project tetap bisa di-build
test relevan tetap lulus
tidak mencampur banyak fitur
```

Jangan membuat commit seperti:

```text
update
fix
changes
final
revisi
test 2
```

dan jangan memecah satu perubahan kecil menjadi banyak commit palsu.

Histori Git yang bagus menunjukkan cara berpikir engineering, bukan hanya jumlah commit.
