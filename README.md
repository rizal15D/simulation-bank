# LedgerBank

LedgerBank adalah simulator core banking edukasional berbasis Java. Aplikasi menyediakan registrasi dan login, rekening IDR, deposit, withdrawal, transfer internal atomik, histori transaksi, ownership enforcement, dan perlindungan idempotency. Aplikasi tidak terhubung ke bank atau payment rail dan tidak ditujukan untuk transaksi finansial produksi.

Implementasi mencakup Milestone 1 dan Milestone 2 lengkap: authentication, authorization, account ownership, Swagger/OpenAPI, Redis, transfer idempotency, RabbitMQ event publication, notification consumer, audit trail, dan environment profiles.

## Stack

- Java 25, Spring Boot 4.1.1, Maven Wrapper.
- Spring MVC, Spring Data JPA, Bean Validation, Spring Security.
- PostgreSQL 18 dan Flyway. PostgreSQL adalah satu-satunya relational database; tidak ada MariaDB.
- Redis 8.2 untuk opaque authentication session dan transfer idempotency.
- RabbitMQ 4.1 untuk banking event, notification, dan audit processing asynchronous.
- springdoc-openapi 3.1.1, JUnit Jupiter 6, Mockito, Spring MVC Test.

## Menjalankan aplikasi

Prasyarat: JDK 25 dan Docker Desktop.

```powershell
Copy-Item .env.example .env
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

Default port development:

| Service | Port host |
|---|---:|
| API | 8080 |
| PostgreSQL | 5432 |
| Redis | 6380 |
| RabbitMQ AMQP | 5673 |
| RabbitMQ Management | 15673 |

Port Redis dan RabbitMQ sengaja tidak memakai port umum 6379/5672 agar tidak mudah bentrok dengan service lokal lain. Semua port Compose dipublikasikan hanya ke loopback.

Jika PostgreSQL 18 sudah tersedia secara lokal, isi `DB_URL`, `DB_USERNAME`, dan `DB_PASSWORD`, lalu cukup jalankan Redis dan RabbitMQ:

```powershell
docker compose up -d --wait redis rabbitmq
.\mvnw.cmd spring-boot:run
```

Flyway menjalankan migration V1-V7 saat startup dan Hibernate hanya memvalidasi schema.

## Authentication dan authorization

Registrasi selalu membuat user `CUSTOMER` yang terhubung satu-ke-satu dengan customer. Password 12-72 karakter printable ASCII disimpan sebagai BCrypt hash. Login menghasilkan opaque bearer token acak; hanya hash token dan metadata session yang disimpan di Redis dengan TTL default delapan jam.

```powershell
$base = 'http://localhost:8080/api/v1'

$user = Invoke-RestMethod -Method Post -Uri "$base/auth/register" `
  -ContentType 'application/json' `
  -Body '{"fullName":"Budi Santoso","email":"budi@example.com","password":"correct horse battery"}'

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType 'application/json' `
  -Body '{"email":"budi@example.com","password":"correct horse battery"}'

$headers = @{ Authorization = "Bearer $($login.accessToken)" }
```

Semua banking endpoint selain health memerlukan bearer token. Customer hanya dapat membaca customer/rekening/histori miliknya dan hanya dapat mengubah saldo atau transfer dari rekening miliknya. Admin dapat membaca data operasional dan membuat customer, tetapi tidak dapat melakukan mutasi uang sebagai customer. Admin opsional dibuat saat startup melalui `APP_ADMIN_EMAIL` dan `APP_ADMIN_PASSWORD`; role tidak dapat dipilih dari endpoint publik.

Logout menghapus session Redis:

```powershell
Invoke-RestMethod -Method Post -Uri "$base/auth/logout" -Headers $headers
```

## Transfer idempotency

`POST /api/v1/transfers` wajib membawa `Idempotency-Key` unik sepanjang 8-128 karakter.

```powershell
$transferHeaders = @{
  Authorization = "Bearer $($login.accessToken)"
  'Idempotency-Key' = [guid]::NewGuid().ToString()
}
```

Request yang sama dengan key dan actor yang sama mengembalikan transfer sebelumnya tanpa mengubah saldo lagi. Key yang sama dengan payload berbeda menghasilkan 409. Redis menyediakan lock singkat dan cache TTL; tabel PostgreSQL `transfer_idempotency` disimpan atomik bersama transfer sebagai recovery durable. Jika Redis tidak tersedia sebelum transfer dimulai, API menolak request dengan 503.

## Swagger dan OpenAPI

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- OpenAPI YAML: [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml)

Ketiga endpoint dokumentasi bersifat publik. OpenAPI berformat 3.0.1 dan mendokumentasikan 13 path serta bearer security scheme. Di Swagger UI, login terlebih dahulu lalu masukkan nilai `accessToken` melalui tombol **Authorize**.

## Redis dan RabbitMQ

Redis menyimpan:

- `auth:session:<sha256-token>` dengan TTL session;
- cache dan distributed lock idempotency transfer dengan key yang diturunkan dari actor dan idempotency key.

RabbitMQ mendeklarasikan topology durable saat aplikasi startup:

| Objek | Konfigurasi |
|---|---|
| Exchange | `ledgerbank.events`, topic, durable |
| Queue | `ledgerbank.notification`, durable |
| Queue | `ledgerbank.audit`, durable |
| Binding | `transfer.*` ke notification |
| Binding | `#` ke audit |

Event `USER_LOGIN`, `ACCOUNT_CREATED`, `TRANSFER_COMPLETED`, dan `TRANSFER_FAILED` dipublish sebagai JSON. Event transactional dikirim setelah database commit. Notification consumer menyimpan notification transfer untuk customer sumber dan tujuan; audit consumer menyimpan seluruh event. Kedua consumer memakai insert idempotent agar redelivery tidak menggandakan record.

## Konfigurasi

Lihat `.env.example`. Variabel utama:

| Variabel | Default |
|---|---|
| `DB_URL` | Dibentuk dari `DB_HOST`, `DB_PORT`, dan `DB_NAME` |
| `DB_USERNAME` / `DB_PASSWORD` | `ledgerbank` |
| `REDIS_PORT` / `REDIS_PASSWORD` | `6380` / `ledgerbank` |
| `RABBITMQ_PORT` | `5673` |
| `RABBITMQ_MANAGEMENT_PORT` | `15673` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `ledgerbank` |
| `AUTH_SESSION_TTL` | `PT8H` |
| `TRANSFER_IDEMPOTENCY_TTL` | `PT24H` |
| `TRANSFER_IDEMPOTENCY_LOCK_TTL` | `PT30S` |
| `PORT` | `8080` |

Jangan commit secret. Mengubah password PostgreSQL pada Compose tidak mengubah password volume database yang sudah terinisialisasi.

Konfigurasi umum berada di `application.yml`, koneksi development di `application-local.yml`, dan koneksi test di `application-test.yml`. Profile default adalah `local`; aktifkan profile lain melalui `SPRING_PROFILES_ACTIVE`.

## Test

Unit dan MVC test:

```powershell
.\mvnw.cmd test
```

Verifikasi terakhir: 75 test, 0 failure, 0 error, 0 skipped. Smoke test runtime memakai PostgreSQL 18, Redis, dan RabbitMQ untuk membuktikan auth, ownership, logout, OpenAPI, idempotency, event publication, dua notification transfer, serta audit login/account/transfer. Integration test berbasis Testcontainers untuk PostgreSQL, Redis, dan RabbitMQ merupakan target Milestone 3; tidak ada rencana menambahkan MariaDB.

## Struktur

```text
src/main/java/com/example/ledgerbank/
  auth/           user, login, session Redis, principal, ownership policy
  customer/       customer dan endpoint administrasi/read
  account/        rekening dan ownership-aware query
  transaction/    deposit, withdrawal, histori
  transfer/       transfer atomik, idempotency Redis/PostgreSQL
  event/          banking event model dan publisher pasca-commit
  notification/   RabbitMQ consumer dan notification persistence
  audit/          RabbitMQ consumer dan audit trail
  common/config/  Spring Security, OpenAPI, RabbitMQ topology
src/main/resources/db/migration/postgresql/
  V1-V7
```

Dokumentasi lanjutan:

- [Arsitektur](docs/architecture.md)
- [Database](docs/database.md)
- [Kontrak API](docs/api.md)
- [Security](docs/security.md)
- [Banking events](docs/events.md)
- [Redis](docs/redis.md)
- [Status implementasi](docs/IMPLEMENTATION_STATUS.md)
