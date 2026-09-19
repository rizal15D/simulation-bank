# Database PostgreSQL 18

PostgreSQL 18 adalah satu-satunya relational database LedgerBank. Referensi MariaDB pada rancangan awal Milestone 2/3 tidak digunakan. Integration test Milestone 3 juga harus memakai PostgreSQL Testcontainers.

Schema dibuat oleh Flyway dari `src/main/resources/db/migration/postgresql`. Hibernate menggunakan `spring.jpa.hibernate.ddl-auto=validate`, sehingga aplikasi tidak membuat atau mengubah schema secara otomatis.

## Migration

| Migration | Objek/perubahan |
|---|---|
| V1 | `customers` |
| V2 | `accounts`, account number sequence |
| V3 | `transfers` |
| V4 | `account_transactions` dan indeks histori |
| V5 | `app_users`, `transfer_idempotency`, constraint dan indeks |
| V6 | Menyelaraskan tipe `request_hash` menjadi `VARCHAR(64)` |

V6 sengaja migration baru, bukan edit pada V5 yang sudah dapat diterapkan, agar checksum dan histori Flyway tetap aman.

## Tabel utama

| Tabel | Fungsi dan constraint penting |
|---|---|
| `customers` | Profil customer; email lowercase/trim dan unik; status ACTIVE/SUSPENDED/CLOSED |
| `accounts` | Rekening milik customer; nomor unik; IDR; saldo `NUMERIC(19,2)` tidak negatif; status ACTIVE/BLOCKED/CLOSED |
| `transfers` | Header transfer sukses; source berbeda dari destination; amount positif; reference unik |
| `account_transactions` | Histori DEPOSIT/WITHDRAWAL/TRANSFER_DEBIT/TRANSFER_CREDIT dengan invariant delta saldo |
| `app_users` | Credential BCrypt dan role; email normalized unik; CUSTOMER wajib memiliki customer, ADMIN tidak memiliki customer |
| `transfer_idempotency` | Mapping durable actor + idempotency key ke request hash dan transfer ID |

Semua primary key dan foreign key domain menggunakan UUID. Timestamp memakai `TIMESTAMPTZ` dan dipetakan ke Java `Instant`.

## Security data

`app_users` memiliki constraint:

- role hanya `CUSTOMER` atau `ADMIN`;
- status hanya `ACTIVE` atau `DISABLED`;
- CUSTOMER harus memiliki `customer_id` yang unik dan tidak null;
- ADMIN harus memiliki `customer_id` null;
- email disimpan normalized dan unik;
- password disimpan sebagai BCrypt hash, bukan plaintext.

Authentication session tidak disimpan di PostgreSQL. Redis menyimpan session ber-TTL menggunakan key dari hash token. PostgreSQL tetap menyimpan identitas user dan credential hash.

## Idempotency data

`transfer_idempotency` menyimpan:

```text
id
actor_id
idempotency_key
request_hash
transfer_id
created_at
expires_at
```

Unique constraint `(actor_id, idempotency_key)` memastikan satu makna untuk satu key milik actor. `transfer_id` juga unik dan memiliki foreign key ke `transfers`. Record dibuat dalam database transaction yang sama dengan transfer, sehingga tidak ada kondisi transfer committed tanpa durable mapping idempotency.

`expires_at` mendokumentasikan retention/TTL logis dan memiliki indeks. Redis cache dapat kedaluwarsa lebih cepat atau hilang; database record tetap menjadi recovery source. Cleanup persistent record belum dijadwalkan pada cutoff Milestone 2 ini.

## Histori dan uang

- `DEPOSIT`: `balance_after = balance_before + amount`, tanpa transfer ID.
- `WITHDRAWAL`: `balance_after = balance_before - amount`, tanpa transfer ID.
- `TRANSFER_DEBIT`: saldo berkurang dan transfer ID wajib.
- `TRANSFER_CREDIT`: saldo bertambah dan transfer ID wajib.

Unique partial index `(transfer_id, transaction_type)` membatasi satu sisi debit dan credit per transfer. Indeks `(account_id, created_at DESC, id DESC)` mendukung pagination histori rekening.

Service mengunci account dengan pessimistic write lock sebelum mutasi. Dua account pada transfer dikunci dalam urutan UUID stabil. Constraint saldo non-negatif menjadi defense-in-depth selain pengecekan service.

## Environment

Compose development menyediakan PostgreSQL 18 pada port host default 5432 dengan named volume. Profile `test` menyediakan PostgreSQL 18 database `ledgerbank_test` pada port 5433 dan tmpfs.

Konfigurasi:

```text
DB_URL
DB_PORT
DB_USERNAME
DB_PASSWORD
```

Jika memakai PostgreSQL lokal, aplikasi dapat diarahkan lewat `DB_URL`; Redis dan RabbitMQ tetap dapat dijalankan terpisah melalui Compose.

Jangan mengedit migration yang sudah diterapkan. Tambahkan migration versi berikutnya untuk setiap evolusi schema.
