# Status implementasi: Milestone 3 selesai

Verifikasi lokal terakhir: 3 Oktober 2026.

PostgreSQL 18 adalah satu-satunya relational database. MariaDB tidak ada pada dependency, migration, Compose, profile, atau rencana test berikutnya.

## Definition of Done

| Kriteria | Status dan bukti |
|---|---|
| Baseline M1/M2 | Core banking, authentication, ownership, idempotency, notification, dan audit dipertahankan |
| Integration foundation | PostgreSQL 18, Redis 8.2, RabbitMQ 4.1 Testcontainers dengan dynamic property dan Flyway V1-V8 |
| Banking/security flow | Authenticated flow, ownership, rollback, error contract, OpenAPI, dan secret redaction diuji lintas stack |
| Concurrency | Invariant saldo, concurrent idempotency, serta deterministic account lock ordering diuji |
| Transactional outbox | Event durable satu transaction dengan bisnis; claim lease, publisher confirm, retry/backoff, DEAD, retention |
| Consumer idempotency | Notification/audit memakai unique key dan `ON CONFLICT DO NOTHING`; redelivery tidak menggandakan data |
| Observability | Actuator aman, liveness/readiness, correlation ID, transfer/outbox metrics, dan backlog gauge |
| Performance | Workload reproducible, HTTP baseline, JFR/thread profiling, serta PostgreSQL query plan terdokumentasi |
| Technical debt | TD-01, TD-02, TD-03 selesai; TD-04 dan TD-05 ditunda dengan bukti dan trigger evaluasi |
| Automation/Linux | Script dev/test/integration/health/reset aman; shebang, executable bit, dan LF diverifikasi |
| CI/quality | Java 25 GitHub Actions, wrapper checksum, unit/integration job, compiler lint, Checkstyle, SpotBugs, JaCoCo |
| Test utama | 95 unit/MVC + 24 integration; 0 failure, 0 error, 0 skipped |

## Runtime verification

- Flyway menerapkan delapan migration sampai V8 dari database kosong PostgreSQL 18.
- Hibernate schema validation lulus.
- Testcontainers memulai PostgreSQL, Redis, dan RabbitMQ terisolasi pada port dinamis.
- Authentication, account, deposit/withdrawal, history, transfer, ownership, logout, dan OpenAPI flow lulus.
- RabbitMQ outage tetap meninggalkan event `PENDING`; recovery mempublish backlog dan consumer tetap idempotent.
- Concurrent transfer menjaga total saldo, mencegah saldo negatif/lost update, dan tidak menggandakan transfer.
- `clean verify -Pintegration,quality` lulus: Surefire 95 dan Failsafe 24 test, Checkstyle 0 violation, SpotBugs 0 bug, compiler tanpa warning.

## Security dan consistency

- Password 12-72 printable ASCII disimpan sebagai BCrypt strength 12.
- Token acak 32 byte tidak disimpan mentah; Redis key memakai SHA-256 token.
- DTO auth meredaksi password dan bearer token dari `toString()`/debug log.
- Public registration selalu CUSTOMER; admin hanya melalui bootstrap environment.
- Transfer, histori debit/credit, dan durable idempotency record berada dalam satu transaction.
- Completion event disimpan ke outbox dalam transaction transfer; rollback tidak meninggalkan completion.
- Publish failure tidak mengubah transfer committed menjadi error palsu dan dipulihkan melalui retry durable.
- Event dan audit tidak membawa password, token, atau infrastructure secret.

## RabbitMQ topology

```text
ledgerbank.events (topic, durable)
  transfer.* -> ledgerbank.notification (durable)
  #          -> ledgerbank.audit (durable)
```

Routing key aktif: `user.login`, `account.created`, `transfer.completed`, dan `transfer.failed`.

## Performance dan keputusan optimasi

- History baseline: 218,60 req/s, p95 27,77 ms, error 0%.
- Transfer baseline: 65,13 req/s, p95 87,76 ms, error 0%.
- Query page pertama 0,074 ms, count 0,658 ms, dan offset 900 0,629 ms pada dataset analisis; index yang ada dipakai.
- Tidak dibuat index baru karena tidak ada bottleneck database terukur. Risiko pagination sangat dalam dicatat sebagai TD-04.
- Hot-account experiment menunjukkan Hikari pending peak 63 dan lock contention; invariant tetap aman. Tuning ditunda sampai ada SLO/deployment target (TD-05).

## Release state

Versi Maven telah disiapkan sebagai `1.0.0` dan repository siap sebagai release candidate Milestone 3 setelah verifikasi lokal lengkap. Workflow CI sudah dikonfigurasi, tetapi tag `v1.0.0` tidak dibuat atau dipush otomatis. Tag hanya boleh dibuat secara sadar setelah commit final berada pada working tree bersih, remote GitHub Actions untuk commit tersebut lulus, migration database kosong berhasil, dan pemeriksaan secret selesai.
