# Status implementasi: Milestone 2 selesai

Verifikasi terakhir: 19 September 2026.

PostgreSQL 18 adalah satu-satunya relational database. MariaDB tidak ada pada dependency, migration, Compose, profile, atau rencana test berikutnya.

## Definition of Done

| Kriteria | Status dan bukti |
|---|---|
| Authentication | Register/login/logout aktif; BCrypt dan opaque Redis session |
| Authorization | Role CUSTOMER/ADMIN dan JSON 401/403 aktif |
| Account ownership | Read dan money mutation diperiksa di service; transfer source wajib milik actor |
| Swagger/OpenAPI | Swagger UI, JSON/YAML spec, bearer scheme, 13 API path |
| Redis | Container healthy; session, idempotency cache, dan lock ber-TTL |
| Transfer idempotency | Redis coordination + durable PostgreSQL record; replay tidak memindahkan uang dua kali |
| RabbitMQ | Durable topic exchange, notification queue, audit queue, dan JSON converter |
| Transfer event | `TRANSFER_COMPLETED` diterbitkan setelah database commit; failure event juga tersedia |
| Notification consumer | Event completion membuat notification idempotent untuk customer terkait |
| Audit log | Login, account creation, transfer completion/failure dicatat asynchronous |
| Docker infrastructure | PostgreSQL 18, Redis 8.2, RabbitMQ 4.1 management dan healthcheck |
| Environment config | `application.yml`, `application-local.yml`, `application-test.yml`, `.env.example` |
| Test utama | 75 unit/MVC test; 0 failure, 0 error, 0 skipped |

## Runtime verification

- Flyway memvalidasi tujuh migration dan menerapkan V7 pada PostgreSQL 18.6.
- Hibernate schema validation lulus.
- Redis dan RabbitMQ container healthy; aplikasi terkoneksi ke keduanya.
- Smoke flow membuat dua user, login, dua rekening, deposit, dan transfer sukses.
- Transfer event nyata menghasilkan `TRANSFER_COMPLETED:2` pada tabel notification.
- Audit nyata menghasilkan `USER_LOGIN:2`, `ACCOUNT_CREATED:2`, dan `TRANSFER_COMPLETED:1` untuk flow tersebut.
- Rabbit consumer menggunakan insert `ON CONFLICT DO NOTHING`, sehingga redelivery tidak menggandakan record.

## Security dan consistency

- Password 12-72 printable ASCII disimpan sebagai BCrypt strength 12.
- Token acak 32 byte tidak disimpan mentah; Redis key memakai SHA-256 token.
- DTO auth meredaksi password dan bearer token dari `toString()`/debug log.
- Public registration selalu CUSTOMER; admin hanya melalui bootstrap environment.
- Transfer, histori debit/credit, dan durable idempotency record berada dalam satu transaction.
- Completion event dikirim melalui listener `AFTER_COMMIT`; rollback tidak menghasilkan completion.
- Publish failure sesudah commit dicatat dan tidak mengubah transfer committed menjadi error palsu.
- Event dan audit tidak membawa password, token, atau infrastructure secret.

## RabbitMQ topology

```text
ledgerbank.events (topic, durable)
  transfer.* -> ledgerbank.notification (durable)
  #          -> ledgerbank.audit (durable)
```

Routing key aktif: `user.login`, `account.created`, `transfer.completed`, dan `transfer.failed`.

## Batas menuju Milestone 3

Milestone 2 selesai. Pekerjaan berikut tetap berada di Milestone 3:

- PostgreSQL, Redis, dan RabbitMQ Testcontainers;
- integration/concurrency test yang diperluas;
- durable transactional outbox sebagai peningkatan recovery publish;
- Actuator, observability, performance profiling, CI, dan quality tooling.

Milestone 3 harus tetap menggunakan PostgreSQL Testcontainers, bukan MariaDB.
