# Database PostgreSQL 18

Schema dibuat oleh Flyway V1–V4. `spring.jpa.hibernate.ddl-auto=validate` mencegah Hibernate mengubah schema otomatis. Dependency `spring-boot-starter-flyway` dan `flyway-database-postgresql` mengikuti [panduan inisialisasi database Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

| Migration | Objek |
|---|---|
| V1 | `customers` |
| V2 | `accounts`, `ledgerbank_account_number_seq` |
| V3 | `transfers` |
| V4 | `account_transactions` |

Semua ID dan foreign key menggunakan UUID. Waktu menggunakan `TIMESTAMPTZ`, dipetakan ke Java `Instant`. Uang memakai `NUMERIC(19,2)`.

## Tabel

| Tabel | Kolom utama | Constraint dan indeks |
|---|---|---|
| `customers` | id, full_name, email, status, created_at, updated_at | Email unik dan sudah lowercase/trim; nama tidak kosong; status ACTIVE/SUSPENDED/CLOSED |
| `accounts` | id, customer_id, account_number, currency, balance, status, created_at, updated_at | FK customer, nomor unik, saldo >= 0, IDR, status ACTIVE/BLOCKED/CLOSED; indeks customer_id |
| `transfers` | id, reference_number, source_account_id, destination_account_id, amount, description, status, created_at, completed_at | Dua FK rekening, sumber berbeda dari tujuan, amount > 0, reference unik, status SUCCESS; indeks sumber dan tujuan |
| `account_transactions` | id, account_id, transfer_id, transaction_type, amount, balance_before, balance_after, created_at | FK rekening dan transfer; amount > 0; kedua saldo >= 0; delta saldo sesuai jenis transaksi |

Rekening baru memiliki saldo `0.00`, currency IDR, status ACTIVE. Nomor rekening berbentuk `LBK-<tahun>-<sequence minimal 8 digit>`. Sequence tidak di-reset setiap tahun, dapat melewati delapan digit, dan dapat memiliki gap setelah rollback.

## Hubungan histori

- `DEPOSIT`: `balance_after = balance_before + amount`, `transfer_id` NULL.
- `WITHDRAWAL`: `balance_after = balance_before - amount`, `transfer_id` NULL.
- `TRANSFER_DEBIT`: saldo berkurang, `transfer_id` wajib.
- `TRANSFER_CREDIT`: saldo bertambah, `transfer_id` wajib.

Unique partial index `(transfer_id, transaction_type)` membatasi satu sisi debit/credit per transfer. Service memastikan sisi debit dan credit memakai rekening serta amount yang sesuai dengan header transfer, dan menyimpan keduanya dalam transaksi yang sama.

Indeks `(account_id, created_at DESC, id DESC)` mendukung histori per rekening. Tidak ada cascading delete rekening atau histori.

## Lingkungan development dan test

Compose `postgres` memakai database `ledgerbank` pada port host 5432 dan named volume. `postgres-test` pada profile `test` memakai `ledgerbank_test`, port host 5433, dan tmpfs. Keduanya memakai image PostgreSQL 18.

Test integrasi berjalan menggunakan database khusus yang namanya berakhiran `_test`, memastikan server major 18, lalu melakukan TRUNCATE sebelum setiap test. Jangan mengarahkannya ke database berisi data yang ingin dipertahankan. URL/user/password test dapat diganti melalui `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`.

File SQL pada `src/main/resources/db/migration` adalah definisi schema lengkap. Tambahkan migration baru untuk evolusi schema yang sudah diterapkan.
