# Banking events dan RabbitMQ

LedgerBank memakai event asynchronous internal tanpa memecah modular monolith menjadi microservices.

## Publication flow

```text
database transaction
  transfer + debit/credit history + idempotency record
          |
        commit
          |
TransactionalEventListener(AFTER_COMMIT)
          |
ledgerbank.events (durable topic exchange)
```

Event sukses dibuat di dalam transaction tetapi baru dikirim ke RabbitMQ setelah commit. Karena itu consumer tidak pernah melihat transfer yang kemudian rollback. Idempotent replay mengambil transfer lama dan tidak membuat event completion kedua.

Jika broker gagal sesudah commit, kegagalan dicatat tanpa mengubah transfer sukses menjadi respons gagal palsu. Durable outbox untuk recovery publish lintas process crash adalah kandidat reliability lanjutan, bukan bagian Milestone 2.

## Event model

`BankingEvent` membawa:

- `eventId` unik;
- `eventType`;
- actor dan customer terkait bila tersedia;
- resource type dan resource ID;
- metadata non-secret;
- `occurredAt`.

Event yang diterbitkan:

| Event | Routing key | Pemicu |
|---|---|---|
| `USER_LOGIN` | `user.login` | Login berhasil |
| `ACCOUNT_CREATED` | `account.created` | Rekening committed |
| `TRANSFER_COMPLETED` | `transfer.completed` | Transfer committed |
| `TRANSFER_FAILED` | `transfer.failed` | Pemrosesan transfer rollback/gagal |

Payload dikonversi ke JSON oleh `JacksonJsonMessageConverter`.

## Topology dan consumer

```text
ledgerbank.events
  transfer.* --> ledgerbank.notification
  #          --> ledgerbank.audit
```

Semua exchange dan queue durable. `NotificationConsumer` hanya memproses `TRANSFER_COMPLETED` dan membuat satu notification per customer berbeda yang terlibat. `AuditConsumer` mencatat seluruh event.

Consumer menggunakan PostgreSQL `ON CONFLICT DO NOTHING` dengan unique key event, sehingga redelivery RabbitMQ tidak menggandakan notification atau audit log.
