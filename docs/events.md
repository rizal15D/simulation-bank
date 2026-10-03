# Banking events dan RabbitMQ

LedgerBank memakai event asynchronous internal tanpa memecah modular monolith menjadi microservices.

## Publication flow

```text
database transaction
  business change + banking event row (PENDING)
          |
        commit
          |
claim ready batch in a short database transaction
          |
publish JSON outside database transaction + wait for broker confirm
          |
ledgerbank.events (durable topic exchange)
          |
acknowledge PUBLISHED or schedule retry in a short transaction
```

`BankingEventPublisher` menyimpan event ke tabel `outbox_events` dalam transaction yang sama dengan perubahan bisnis. Karena itu consumer tidak pernah melihat transfer yang kemudian rollback. Idempotent replay mengambil transfer lama dan tidak membuat event completion kedua.

Outbox scheduler mengambil batch `PENDING` memakai row lock dan `SKIP LOCKED`, memajukan `next_attempt_at` sebagai claim lease, lalu commit sebelum melakukan network I/O. Publisher menunggu RabbitMQ confirm. Acknowledgement sukses mengubah status menjadi `PUBLISHED`; kegagalan menambah `attempt_count`, menyimpan error yang dipotong aman, dan menjadwalkan exponential backoff. Default delapan kegagalan memindahkan event ke `DEAD`.

Jika proses berhenti setelah publish tetapi sebelum acknowledgement PostgreSQL, claim lease akan kedaluwarsa dan event dikirim ulang. Oleh karena itu guarantee pipeline adalah at-least-once. Unique constraint pada consumer membuat redelivery aman; sistem tidak mengklaim exactly-once delivery.

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

## Recovery, retention, dan observability

- Claim lease default 30 detik memulihkan event yang ditinggalkan worker.
- Retry dimulai satu detik dan dibatasi maksimum satu menit.
- Publisher confirm timeout default lima detik.
- Event `PUBLISHED` dibersihkan setelah retention default tujuh hari; `DEAD` dipertahankan untuk investigasi.
- Metric mencakup published, retry, dead, delivery duration, serta backlog `PENDING` dan `DEAD`.
- Correlation ID HTTP tidak dimasukkan sebagai secret ke event; payload hanya membawa identifier/metadata operasional yang dibatasi.

Integration test menghentikan RabbitMQ, memastikan transaksi dan row outbox tetap committed, menyalakan broker kembali, lalu membuktikan event terkirim dan consumer tidak membuat duplikasi. Queue integration dibersihkan antar-test agar hasil tidak bergantung pada urutan eksekusi.
