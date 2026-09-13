# API Milestone 1

Base URL development: `http://localhost:8080/api/v1`. Request dan response memakai JSON. ID customer/rekening/transaksi adalah UUID; path rekening memakai `accountId`, bukan nomor rekening. Semua endpoint belum memakai authentication.

Dokumentasi interaktif tersedia pada `/swagger-ui.html`, dengan definisi OpenAPI pada `/v3/api-docs` (JSON) dan `/v3/api-docs.yaml` (YAML). Swagger memuat contoh request, schema response, batas pagination/amount, dan format error. Gunakan UUID customer/rekening yang benar dari response aplikasi saat mencoba endpoint.

## Endpoint

| Method dan path | Input | Hasil sukses |
|---|---|---|
| `GET /health` | — | 200: `{ "status": "UP" }` |
| `POST /customers` | fullName, email | 201: CustomerResponse |
| `GET /customers/{customerId}` | UUID | 200: CustomerResponse |
| `POST /accounts` | customerId | 201: AccountResponse |
| `GET /accounts/{accountId}` | UUID | 200: AccountResponse |
| `GET /customers/{customerId}/accounts` | UUID | 200: array AccountResponse |
| `POST /accounts/{accountId}/deposit` | amount | 201: TransactionResponse |
| `POST /accounts/{accountId}/withdraw` | amount | 201: TransactionResponse |
| `POST /transfers` | sourceAccountId, destinationAccountId, amount, description opsional | 201: TransferResponse |
| `GET /accounts/{accountId}/transactions` | page default 0, size default 20 | 200: TransactionHistoryResponse |

`fullName` wajib, maksimal 150 karakter; `email` wajib valid dan maksimal 254 karakter. Email disimpan lowercase dan unik. Customer harus ACTIVE untuk membuka rekening. Rekening dibuat otomatis dengan saldo 0, currency IDR dan status ACTIVE.

Amount wajib positif, maksimum `99999999999999999.99`, dapat direpresentasikan tepat dengan dua angka pecahan. Input tidak dibulatkan. Deposit, withdrawal dan kedua rekening pada transfer harus ACTIVE. Withdrawal/transfer tidak boleh membuat saldo negatif. Description maksimal 255 karakter. Page minimal 0 dan size 1–100.

## Contoh alur PowerShell

Script berikut membuat customer, dua rekening, lalu melakukan deposit, withdrawal, dan transfer:

```powershell
$base = 'http://localhost:8080/api/v1'
$customer = Invoke-RestMethod -Method Post -Uri "$base/customers" -ContentType 'application/json' -Body (@{
    fullName = 'Budi Santoso'
    email = 'budi@example.com'
} | ConvertTo-Json)

$accountBody = @{ customerId = $customer.id } | ConvertTo-Json
$source = Invoke-RestMethod -Method Post -Uri "$base/accounts" -ContentType 'application/json' -Body $accountBody
$destination = Invoke-RestMethod -Method Post -Uri "$base/accounts" -ContentType 'application/json' -Body $accountBody

Invoke-RestMethod -Method Post -Uri "$base/accounts/$($source.id)/deposit" -ContentType 'application/json' -Body '{"amount":1000000}'
Invoke-RestMethod -Method Post -Uri "$base/accounts/$($source.id)/withdraw" -ContentType 'application/json' -Body '{"amount":100000}'

$transfer = Invoke-RestMethod -Method Post -Uri "$base/transfers" -ContentType 'application/json' -Body (@{
    sourceAccountId = $source.id
    destinationAccountId = $destination.id
    amount = 250000
    description = 'Transfer internal'
} | ConvertTo-Json)

Invoke-RestMethod "$base/accounts/$($source.id)"
Invoke-RestMethod "$base/accounts/$($destination.id)"
Invoke-RestMethod "$base/accounts/$($source.id)/transactions?page=0&size=20"
```

Saldo akhir sumber 650000 dan tujuan 250000. Gunakan email berbeda saat mengulang contoh; email yang sudah terdaftar menghasilkan 409. Setiap request uang sukses melakukan operasi baru karena idempotency belum tersedia.

## Bentuk response

CustomerResponse: `id`, `fullName`, `email`, `status`, `createdAt`, `updatedAt`.

AccountResponse: `id`, `customerId`, `accountNumber`, `currency`, `balance`, `status`, `createdAt`, `updatedAt`.

TransactionResponse:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "accountId": "22222222-2222-2222-2222-222222222222",
  "transferId": null,
  "transactionType": "DEPOSIT",
  "amount": 100000.00,
  "balanceBefore": 0.00,
  "balanceAfter": 100000.00,
  "createdAt": "2026-09-13T09:00:00Z"
}
```

TransferResponse: `id`, `referenceNumber` (`TRF-<UUID>`), `sourceAccountId`, `destinationAccountId`, `amount`, `description`, `status` (`SUCCESS`), `createdAt`, `completedAt`.

TransactionHistoryResponse: `content` (array TransactionResponse), `page`, `size`, `totalElements`, `totalPages`. Urutan terbaru dahulu (`createdAt DESC`, lalu `id DESC`). Rekening yang ada tetapi belum memiliki histori menghasilkan content kosong; rekening yang tidak ada menghasilkan 404.

## Error

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "Account balance is insufficient",
  "timestamp": "2026-09-13T09:00:00Z"
}
```

| Status | Code |
|---|---|
| 400 | `INVALID_AMOUNT`, `SAME_ACCOUNT`, `INVALID_PAGINATION`, `VALIDATION_ERROR`, `INVALID_REQUEST` |
| 404 | `CUSTOMER_NOT_FOUND`, `ACCOUNT_NOT_FOUND` |
| 409 | `EMAIL_ALREADY_EXISTS`, `CUSTOMER_NOT_ACTIVE`, `ACCOUNT_NOT_ACTIVE`, `INSUFFICIENT_BALANCE`, `BALANCE_LIMIT_EXCEEDED`, `CURRENCY_MISMATCH`, `ACCOUNT_BUSY`, `DATA_CONFLICT` |
| 404/405/415 dan error request framework lain | `REQUEST_ERROR` |
| 500 | `INTERNAL_ERROR` |

Error validasi DTO dapat muncul sebelum validasi domain. Detail internal database tidak dikirim kepada client. Operasi finansial yang gagal di dalam service me-rollback seluruh perubahan saldo dan histori.
