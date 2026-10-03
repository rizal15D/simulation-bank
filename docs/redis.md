# Redis

Redis adalah dependency operasional untuk authentication session dan koordinasi idempotency. PostgreSQL tetap menjadi source of truth transaksi.

## Authentication session

```text
auth:session:<sha256-token>
```

Value berisi principal minimum: user ID, customer ID, email, dan role. TTL default `PT8H`. Token mentah tidak menjadi bagian key atau value. Logout menghapus key session.

Jika Redis tidak tersedia ketika login, API mengembalikan 503 dan tidak menerbitkan session setengah jadi.

## Transfer idempotency

Redis menyimpan dua jenis state:

- lock `SET NX` dengan TTL default `PT30S` untuk mengoordinasikan request simultan;
- cache hasil dengan TTL default `PT24H` agar retry tidak perlu membaca PostgreSQL.

Lock dilepas dengan Lua compare-and-delete agar request lain tidak dapat menghapus lock yang bukan miliknya. Scope key mencakup actor dan `Idempotency-Key`.

Record durable `(actor_id, idempotency_key, request_hash, transfer_id)` tetap disimpan atomik di PostgreSQL. Bila cache result hilang, retry dipulihkan dari database. Bila Redis gagal sebelum mutasi, transfer ditolak 503 agar idempotency tidak pernah dibypass. Bila cache write gagal setelah commit, transfer tetap sukses karena record PostgreSQL sudah tersedia.

Readiness memasukkan Redis karena authentication dan koordinasi idempotency tidak dapat bekerja aman tanpanya. Integration test memakai Redis 8.2 Testcontainers dan membuktikan cache loss/durable replay, concurrent key yang sama, payload conflict, serta fail-closed 503 sebelum mutasi.

## Development

Compose menjalankan Redis authenticated pada port host `6380`, memakai AOF dan named volume. Konfigurasi ada di `application-local.yml` dan dapat dioverride melalui `REDIS_HOST`, `REDIS_PORT`, serta `REDIS_PASSWORD`.

Testcontainers integration tidak memakai port 6380 atau data Compose; container dan property dibuat dinamis agar suite terisolasi serta dapat berjalan paralel dengan environment development.
