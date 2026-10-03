# API: security dan idempotency

Base URL development: `http://localhost:8080/api/v1`. Request dan response menggunakan JSON. Dokumentasi interaktif tersedia di `/swagger-ui.html`; spesifikasi tersedia sebagai JSON di `/v3/api-docs` dan YAML di `/v3/api-docs.yaml`.

## Authentication

Registrasi publik membuat user `CUSTOMER` beserta customer yang terhubung:

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "fullName": "Budi Santoso",
  "email": "budi@example.com",
  "password": "correct horse battery"
}
```

Password harus 12-72 printable ASCII characters. Password disimpan sebagai BCrypt hash dan tidak pernah dikembalikan.

Login:

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "budi@example.com",
  "password": "correct horse battery"
}
```

Response memuat `accessToken`, `tokenType`, `expiresAt`, dan user. Gunakan token untuk endpoint terlindungi:

```http
Authorization: Bearer <accessToken>
```

Token adalah opaque random token, bukan JWT. Session disimpan di Redis dengan TTL. Logout menghapus session:

```http
POST /api/v1/auth/logout
Authorization: Bearer <accessToken>
```

Login dengan email atau password salah selalu menggunakan response generik 401 agar tidak membocorkan keberadaan akun.

## Endpoint dan akses

| Method dan path | Akses | Hasil sukses |
|---|---|---|
| `GET /health` | Publik | 200 health |
| `POST /auth/register` | Publik | 201 UserResponse |
| `POST /auth/login` | Publik | 200 TokenResponse |
| `POST /auth/logout` | Authenticated | 204 |
| `POST /customers` | ADMIN | 201 CustomerResponse |
| `GET /customers/{customerId}` | Pemilik atau ADMIN | 200 CustomerResponse |
| `POST /accounts` | CUSTOMER pemilik | 201 AccountResponse |
| `GET /accounts/{accountId}` | Pemilik atau ADMIN | 200 AccountResponse |
| `GET /customers/{customerId}/accounts` | Pemilik atau ADMIN | 200 array AccountResponse |
| `POST /accounts/{accountId}/deposit` | CUSTOMER pemilik | 201 TransactionResponse |
| `POST /accounts/{accountId}/withdraw` | CUSTOMER pemilik | 201 TransactionResponse |
| `POST /transfers` | CUSTOMER pemilik rekening sumber | 201 TransferResponse |
| `GET /accounts/{accountId}/transactions` | Pemilik atau ADMIN | 200 TransactionHistoryResponse |

Pemeriksaan role dilakukan pada security filter chain. Pemeriksaan ownership juga dilakukan kembali di service setelah entity/rekening diambil atau dikunci. Karena itu mengganti UUID pada path/body tidak memberikan akses ke rekening milik customer lain.

Admin opsional dibuat melalui `APP_ADMIN_EMAIL` dan `APP_ADMIN_PASSWORD` saat startup. Endpoint publik tidak menerima role dari client.

## Transfer idempotency

Transfer wajib memiliki header:

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

Key harus 8-128 karakter dari huruf, angka, titik, underscore, colon, atau hyphen. Scope key adalah user/actor, bukan global.

```http
POST /api/v1/transfers
Authorization: Bearer <accessToken>
Idempotency-Key: <unique-key>
Content-Type: application/json

{
  "sourceAccountId": "uuid",
  "destinationAccountId": "uuid",
  "amount": 250000.00,
  "description": "Internal transfer"
}
```

Perilaku retry:

- key + actor + payload sama: 201 dan TransferResponse yang sama;
- key + actor sama tetapi payload berbeda: 409 `IDEMPOTENCY_KEY_REUSED`;
- request key yang sama masih berjalan: 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS`;
- Redis tidak dapat digunakan sebelum mutasi uang: 503 `IDEMPOTENCY_SERVICE_UNAVAILABLE`.

Redis menyimpan lock sementara dan cache result. PostgreSQL menyimpan fingerprint request dan transfer ID dalam transaksi database yang sama dengan transfer, sehingga retry tetap dapat dipulihkan jika cache result hilang. Saldo tidak didebit dua kali.

Transfer baru menyimpan `TRANSFER_COMPLETED` ke transactional outbox dalam transaction yang sama dengan transfer. Setelah commit, publisher terjadwal mengirim event ke RabbitMQ dengan publisher confirm dan retry. Notification dan audit diproses asynchronous; response transfer tidak menunggu broker atau consumer. Retry idempotent yang mengembalikan transfer lama tidak membuat completion event kedua. Belum ada endpoint publik untuk membaca notification atau audit log.

Jika RabbitMQ offline, transfer yang valid tetap dapat sukses karena event sudah durable di PostgreSQL. Backlog akan dikirim setelah broker pulih. Delivery dapat berulang bila proses berhenti di antara broker confirm dan acknowledgement outbox; notification serta audit consumer idempotent terhadap event ID yang sama.

## Management endpoint

Actuator berada di luar prefix `/api/v1`:

| Path | Akses | Keterangan |
|---|---|---|
| `GET /actuator/health` | Publik | Status agregat; detail hanya ADMIN |
| `GET /actuator/health/liveness` | Publik | Application liveness |
| `GET /actuator/health/readiness` | Publik | Application state, PostgreSQL, Redis |
| `GET /actuator/info` | Publik | Info aman; environment detail dinonaktifkan |
| `GET /actuator/metrics/**` | ADMIN | Metric runtime dan bisnis |
| `GET /actuator/prometheus` | ADMIN | Prometheus scrape format |

Path Actuator lain ditolak. `X-Correlation-ID` yang valid dikembalikan pada seluruh response; bila tidak diberikan, server membuat UUID baru.

## Contoh PowerShell

```powershell
$base = 'http://localhost:8080/api/v1'

$user = Invoke-RestMethod -Method Post -Uri "$base/auth/register" `
  -ContentType 'application/json' `
  -Body (@{
    fullName = 'Budi Santoso'
    email = 'budi@example.com'
    password = 'correct horse battery'
  } | ConvertTo-Json)

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType 'application/json' `
  -Body (@{
    email = $user.email
    password = 'correct horse battery'
  } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.accessToken)" }

$source = Invoke-RestMethod -Method Post -Uri "$base/accounts" `
  -Headers $headers -ContentType 'application/json' `
  -Body (@{ customerId = $user.customerId } | ConvertTo-Json)

$destination = Invoke-RestMethod -Method Post -Uri "$base/accounts" `
  -Headers $headers -ContentType 'application/json' `
  -Body (@{ customerId = $user.customerId } | ConvertTo-Json)

Invoke-RestMethod -Method Post -Uri "$base/accounts/$($source.id)/deposit" `
  -Headers $headers -ContentType 'application/json' -Body '{"amount":1000000}'

$transferHeaders = @{
  Authorization = "Bearer $($login.accessToken)"
  'Idempotency-Key' = [guid]::NewGuid().ToString()
}

$transferBody = @{
  sourceAccountId = $source.id
  destinationAccountId = $destination.id
  amount = 250000
  description = 'Internal transfer'
} | ConvertTo-Json

$first = Invoke-RestMethod -Method Post -Uri "$base/transfers" `
  -Headers $transferHeaders -ContentType 'application/json' -Body $transferBody

$retry = Invoke-RestMethod -Method Post -Uri "$base/transfers" `
  -Headers $transferHeaders -ContentType 'application/json' -Body $transferBody

$first.id -eq $retry.id
```

Baris terakhir menghasilkan `True`; saldo sumber hanya berkurang sekali.

## Aturan input

- `fullName`: wajib, maksimum 150 karakter.
- `email`: wajib valid, maksimum 254 karakter, dinormalisasi lowercase dan unik.
- `amount`: positif, maksimum `99999999999999999.99`, maksimum dua angka pecahan.
- Deposit, withdrawal, serta kedua rekening transfer harus ACTIVE.
- Withdrawal/transfer tidak boleh membuat saldo negatif.
- Description transfer maksimum 255 karakter.
- Histori: `page >= 0`, `1 <= size <= 100`; default 0 dan 20.

## Error

Semua error aplikasi menggunakan bentuk:

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "Account balance is insufficient",
  "timestamp": "2026-09-19T03:00:00Z"
}
```

Status penting:

| Status | Contoh code |
|---|---|
| 400 | `VALIDATION_ERROR`, `INVALID_REQUEST`, `INVALID_AMOUNT`, `INVALID_IDEMPOTENCY_KEY`, `SAME_ACCOUNT` |
| 401 | `UNAUTHORIZED` |
| 403 | `FORBIDDEN` |
| 404 | `CUSTOMER_NOT_FOUND`, `ACCOUNT_NOT_FOUND` |
| 409 | `EMAIL_ALREADY_EXISTS`, `ACCOUNT_NOT_ACTIVE`, `INSUFFICIENT_BALANCE`, `ACCOUNT_BUSY`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_REQUEST_IN_PROGRESS` |
| 422 | Dicadangkan dan didokumentasikan pada OpenAPI |
| 503 | `AUTH_SERVICE_UNAVAILABLE`, `IDEMPOTENCY_SERVICE_UNAVAILABLE` |
| 500 | `INTERNAL_ERROR` |

Detail internal database, password, token, dan secret tidak dimasukkan ke error response. DTO authentication juga meredaksi password dan bearer token dari representasi log.
