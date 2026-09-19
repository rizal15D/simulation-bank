# Security

LedgerBank menggunakan Spring Security dengan opaque bearer session. API tidak memakai HTTP Basic, form login, JWT, atau server HTTP session.

## Authentication flow

1. `POST /api/v1/auth/register` membuat `Customer` dan `AppUser` role `CUSTOMER` dalam satu transaksi PostgreSQL.
2. Password harus 12-72 printable ASCII dan disimpan sebagai BCrypt strength 12.
3. `POST /api/v1/auth/login` memverifikasi password dan membuat token acak 32 byte.
4. Client mengirim `Authorization: Bearer <token>`.
5. Filter melakukan SHA-256 terhadap token, membaca principal dari Redis, lalu mengisi `BankingPrincipal`.
6. `POST /api/v1/auth/logout` menghapus session Redis.

Token mentah hanya dikirim kepada client. PostgreSQL tidak menyimpan token dan Redis hanya menerima key turunan SHA-256. Login yang gagal selalu memakai pesan generik dan tetap menjalankan BCrypt verification terhadap dummy hash bila email tidak ditemukan.

DTO password dan token mengoverride `toString()` dengan `[REDACTED]`, sehingga logging Spring MVC pada level debug tidak mencetak credential.

## Authorization

| Operasi | CUSTOMER | ADMIN |
|---|---|---|
| Membaca profil/rekening/histori | Hanya milik sendiri | Data operasional |
| Membuka rekening | Hanya untuk diri sendiri | Tidak |
| Deposit/withdrawal | Rekening sendiri | Tidak |
| Transfer | Hanya dari rekening sendiri | Tidak |
| Membuat customer via endpoint admin | Tidak | Ya |

Security filter chain memeriksa role dan service memeriksa ownership objek. Untuk mutasi uang, ownership diperiksa setelah rekening dikunci dari database sehingga tidak ada celah time-of-check/time-of-use.

Registrasi publik tidak menerima role. Admin opsional hanya dibuat saat startup jika `APP_ADMIN_EMAIL` dan `APP_ADMIN_PASSWORD` diisi.

## Data yang tidak boleh masuk audit

Audit event tidak memuat password, bearer token, Redis key, atau secret infrastructure. Metadata event hanya berisi identifier operasional, nominal transfer, currency, dan failure code yang sudah disanitasi.
