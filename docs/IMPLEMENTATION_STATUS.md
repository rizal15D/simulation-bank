# Status implementasi: Milestone 2 sampai RabbitMQ aktif

Verifikasi terakhir: 19 September 2026.

Scope pekerjaan berhenti tepat pada kriteria pengguna berikut:

- authentication berjalan;
- authorization berjalan;
- account ownership aman;
- Swagger/OpenAPI tersedia;
- Redis aktif;
- transfer memiliki idempotency protection;
- RabbitMQ aktif.

Database yang digunakan hanya PostgreSQL 18. MariaDB tidak ditambahkan ke dependency, migration, Compose, maupun rencana Testcontainers.

## Hasil verifikasi

| Pemeriksaan | Hasil |
|---|---|
| `mvnw test` | 68 test, 0 failure, 0 error, 0 skipped |
| Flyway PostgreSQL | V1-V6 tervalidasi dan diterapkan |
| Hibernate schema validation | Lulus saat startup |
| Application startup | Lulus pada Java 25 / Spring Boot 4.1.1 |
| Health | HTTP 200, status UP |
| Endpoint terlindungi tanpa token | HTTP 401 |
| Cross-customer account read | HTTP 403 |
| Logout | 204, token lama kemudian 401 |
| Missing registration password | HTTP 400 |
| OpenAPI | 3.0.1, 13 path, bearer scheme |
| Redis | Container healthy, authenticated PING=PONG, session key ber-TTL terbentuk |
| Idempotency retry | Transfer ID sama, saldo hanya berubah satu kali |
| Idempotency payload conflict | HTTP 409 |
| RabbitMQ | Container healthy dan aplikasi terkoneksi ke port 5673 |
| Rabbit topology | Durable exchange, dua durable queue, dua bindings tersedia |

Smoke transfer menggunakan saldo awal 100.00. Request pertama memindahkan 25.00, retry dengan key/payload sama mengembalikan transfer ID yang sama, dan saldo akhir terbukti 75.00/25.00. Reuse key dengan amount berbeda menghasilkan 409.

## Implementasi security

- Register/login/logout dengan opaque bearer token.
- Password BCrypt strength 12 dan validasi 12-72 printable ASCII.
- Session Redis default delapan jam; Redis key memakai SHA-256 token, bukan token mentah.
- Role `CUSTOMER` dan `ADMIN`.
- Public registration tidak dapat memilih role.
- Optional bootstrap admin melalui environment.
- JSON 401 dan 403; HTTP Basic, form login, CSRF, dan server HTTP session dinonaktifkan.
- Ownership diperiksa di backend service untuk customer, account, deposit, withdrawal, transfer source, dan transaction history.
- Admin read-only untuk data operasional; mutasi uang hanya untuk CUSTOMER pemilik.

## Implementasi idempotency

- Header `Idempotency-Key` wajib pada transfer.
- Request fingerprint stabil mencakup actor, source, destination, normalized amount, dan description.
- Redis cache result ber-TTL dan lock `SET NX` ber-TTL.
- Lock release menggunakan compare-and-delete Lua script.
- PostgreSQL `transfer_idempotency` menjadi durable recovery record.
- Transfer, histori debit/credit, dan idempotency record berada dalam satu database transaction.
- Redis unavailable sebelum mutasi menghasilkan 503.
- Redis cache write gagal setelah commit tidak membatalkan transfer; retry dipulihkan dari PostgreSQL.

## Infrastructure

Compose menyediakan:

```text
postgres:18
redis:8.2-alpine
rabbitmq:4.1-management-alpine
postgres:18 profile test
```

Redis dan RabbitMQ project berjalan healthy pada verifikasi. PostgreSQL Compose project tidak dijalankan karena port 5432 sudah dipakai PostgreSQL 18.6 lokal; aplikasi benar-benar terkoneksi ke PostgreSQL lokal tersebut. Tidak ada process/database lokal yang dihentikan atau dihapus.

RabbitMQ topology:

```text
ledgerbank.events (topic, durable)
  transfer.* -> ledgerbank.notification (durable)
  #          -> ledgerbank.audit (durable)
```

Topology dideklarasikan melalui `RabbitAdmin.initialize()` pada application runner, sehingga verifikasi startup mencakup koneksi broker nyata.

## Test yang ditambahkan

- `OwnershipPolicyTest`
- `AuthServiceTest`
- `IdempotentTransferServiceTest`
- penyesuaian MVC exception test terhadap authentication principal

Test mencakup registration/hash/link customer, login dan generic credential failure, ownership rules, idempotent replay, payload conflict, dan concurrent duplicate-in-progress.

## Batas yang sengaja belum dikerjakan

Definition of Done penuh pada dokumen Milestone 2 masih memiliki tahap setelah RabbitMQ aktif. Sesuai instruksi cutoff, item berikut belum diimplementasikan:

- banking event model dan transfer event publisher;
- notification consumer dan notification persistence;
- audit log;
- environment profile split tambahan;
- Testcontainers dan integration test M2;
- observability/performance/CI Milestone 3.

Queue notification dan audit sudah tersedia, tetapi belum ada publisher/consumer. Dokumentasi tidak mengklaim event sudah diproses.

## Keselarasan milestone

Milestone 1 tetap utuh: transfer core, histori, PostgreSQL transaction, rollback, dan pessimistic locking dipertahankan. Milestone 2 menambah security/idempotency sebagai wrapper dan policy, bukan menduplikasi mutation logic.

Untuk Milestone 3:

- gunakan PostgreSQL Testcontainers, bukan MariaDB;
- tambahkan Redis dan RabbitMQ Testcontainers;
- lanjutkan concurrency test dari locking yang sudah ada;
- tambahkan event publication test hanya setelah publisher dikerjakan;
- pertahankan ownership dan idempotency sebagai area coverage kritis.
