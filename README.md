# LedgerBank

Simulator core banking edukasional berbasis Java: customer, rekening IDR, deposit, withdrawal, transfer internal atomik, dan histori transaksi. Aplikasi ini merupakan proyek pembelajaran/portfolio, tidak terhubung dengan bank atau BI-FAST dan tidak ditujukan untuk transaksi finansial produksi.

## Stack

- Java 25 LTS, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Bean Validation.
- PostgreSQL 18, Flyway, Maven Wrapper (Maven 3.9.16).
- JUnit Jupiter 6 dari dependency management Spring Boot, Mockito, Spring MVC Test.

Java 25 berada dalam rentang kompatibilitas [Spring Boot 4.1.1](https://docs.spring.io/spring-boot/system-requirements.html). Spesifikasi awal menyebut JUnit 5; implementasi memakai JUnit 6 karena [SpringExtension pada Spring Framework 7 memerlukan JUnit Jupiter 6+](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html). Versi dependency test mengikuti Spring Boot.

## Menjalankan di Windows / PowerShell

Prasyarat: JDK 25 tersedia pada PATH atau JAVA_HOME, Docker Desktop dengan backend Linux berjalan, serta akses internet untuk unduhan pertama Maven/dependency/image.

```powershell
java -version
docker version
docker compose up -d --wait postgres
.\mvnw.cmd spring-boot:run
```

Pada terminal kedua:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
```

Respons health: `{"status":"UP"}`. Flyway menjalankan migration otomatis saat aplikasi mulai; Hibernate hanya memvalidasi schema. Gunakan `Ctrl+C` untuk menghentikan aplikasi, dan `docker compose stop postgres` untuk menghentikan database tanpa menghapus volume.

macOS/Linux menggunakan perintah Docker yang sama, lalu `./mvnw spring-boot:run`.

## Swagger UI dan OpenAPI

Setelah aplikasi berjalan, buka:

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- OpenAPI YAML: [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml)

Swagger mengelompokkan 10 endpoint ke Customers, Accounts, Transactions, Transfers, dan Health. Pilih endpoint lalu **Try it out** untuk mengisi request dan **Execute** untuk menjalankannya. Buat customer dan rekening terlebih dahulu, kemudian gunakan UUID hasil response pada request selanjutnya. Contoh UUID di schema merupakan placeholder.

Operasi deposit, withdrawal, dan transfer dari Swagger mengubah database aplikasi. Endpoint belum memakai authentication dan setiap request uang yang berhasil menghasilkan transaksi baru.

Definisi OpenAPI 3.0 dihasilkan dari controller/DTO menggunakan [springdoc-openapi 3.1.1 untuk Spring Boot 4](https://springdoc.org/getting-started.html). Model error dan metadata API berada di `common/config/OpenApiConfig.java`. Jika `PORT` diubah, sesuaikan port pada URL di atas.

## Konfigurasi

| Variabel aplikasi | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ledgerbank` |
| `DB_USERNAME` | `ledgerbank` |
| `DB_PASSWORD` | `ledgerbank` |
| `PORT` | `8080` |

Compose memakai `DB_PORT` (default `5432`) dan `DB_PASSWORD`. Kredensial default hanya untuk development. Port PostgreSQL dipublikasikan ke loopback. Image `postgres:18` menggunakan volume pada `/var/lib/postgresql`, sesuai [dokumentasi image PostgreSQL](https://hub.docker.com/_/postgres).

`.env.example` dapat disalin menjadi `.env` untuk Compose dan aplikasi. Saat dijalankan dari direktori proyek, Spring Boot membaca `.env` melalui `spring.config.import` sebagai file Java properties. Gunakan format `KEY=value` tanpa tanda kutip pembungkus atau komentar di akhir nilai; tulis komentar pada baris tersendiri. Environment variable pada terminal mengalahkan nilai `.env`. `DB_PORT` juga dipakai pada URL database default aplikasi; `DB_URL` dapat diisi untuk mengganti URL secara lengkap. Contoh override melalui terminal:

```powershell
$env:DB_PORT = '55432'
$env:DB_URL = 'jdbc:postgresql://localhost:55432/ledgerbank'
$env:DB_PASSWORD = 'local-development-password'
docker compose up -d --wait postgres
.\mvnw.cmd spring-boot:run
```

Mengubah password pada environment Compose tidak mengganti password database yang sudah terinisialisasi dalam volume lama. Untuk PostgreSQL yang sudah tersedia di mesin, cukup isi `DB_URL`, `DB_USERNAME`, dan `DB_PASSWORD`, lalu jalankan aplikasi.

## Menjalankan test

Unit dan test MVC tidak memerlukan PostgreSQL atau Docker:

```powershell
.\mvnw.cmd test
```

Test integrasi memerlukan PostgreSQL 18 dan menjalankan HTTP, Flyway, JPA, constraint, rollback, serta concurrent withdrawal:

```powershell
docker compose --profile test up -d --wait postgres-test
.\mvnw.cmd -Pintegration verify
docker compose --profile test stop postgres-test
```

Service test memakai port `5433`, database `ledgerbank_test`, user/password `ledgerbank`, dan storage sementara. Test mengosongkan tabel pada database test; ada pemeriksaan nama database berakhiran `_test` sebelum operasi tersebut. Test tidak dilewati ketika database tidak tersedia: profile integrasi akan gagal.

Untuk PostgreSQL 18 yang sudah tersedia:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:55432/ledgerbank_test'
$env:TEST_DB_USERNAME = 'ledgerbank'
$env:TEST_DB_PASSWORD = 'ledgerbank'
.\mvnw.cmd -Pintegration verify
```

Pada macOS/Linux, gunakan `./mvnw` serta `export NAMA_VARIABEL=nilai`. `./mvnw package` menghasilkan `target/ledgerbank-0.0.1-SNAPSHOT.jar`, yang dapat dijalankan dengan `java -jar target/ledgerbank-0.0.1-SNAPSHOT.jar`.

## Struktur dan dokumentasi

```text
src/main/java/com/example/ledgerbank/
  customer/       customer dan validasi email unik
  account/        pembukaan rekening dan aturan saldo
  transaction/    deposit, withdrawal, histori per rekening
  transfer/       transfer internal atomik
  common/         validasi uang, health, penanganan error
src/main/resources/db/migration/   V1 sampai V4
src/test/                         unit, MVC, dan integrasi PostgreSQL
```

- [Arsitektur dan batas transaksi](docs/architecture.md)
- [Schema database](docs/database.md)
- [Kontrak API dan contoh alur](docs/api.md)
- [Status implementasi dan hasil verifikasi](docs/IMPLEMENTATION_STATUS.md)

Fokus Milestone 1 adalah konsistensi saldo dan histori. Authentication, authorization, idempotency, audit operator, dan pembayaran antarbank merupakan pekerjaan milestone berikutnya. Endpoint saat ini tidak memakai authentication. Setiap request finansial yang berhasil menjalankan operasi baru; pengulangan request belum memiliki perlindungan idempotency.
