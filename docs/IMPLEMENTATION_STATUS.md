# Status implementasi Milestone 1

Verifikasi terakhir: 13 September 2026 (termasuk Swagger/OpenAPI).

Implementasi aplikasi dan pengujian PostgreSQL selesai. Satu kriteria lingkungan pada Definition of Done masih belum terverifikasi: menjalankan PostgreSQL melalui Docker di mesin ini, karena backend Docker/WSL tidak dapat dimulai pada verifikasi awal (`Wsl/CallMsi/Install/REGDB_E_CLASSNOTREG`). WSL 2.7.14 kemudian berhasil dipasang; verifikasi container setelah restart Windows belum dicatat. File Compose sudah lulus validasi konfigurasi. Test database dijalankan pada PostgreSQL 18.6 portabel yang benar-benar berjalan, bukan database in-memory atau mock.

## Hasil

| Pemeriksaan | Hasil |
|---|---|
| Build `mvnw -Pintegration verify` | Lulus, JAR berhasil dibuat |
| Unit dan MVC | 60 test, 0 gagal, 0 error, 0 dilewati |
| Integrasi PostgreSQL 18.6 dan OpenAPI | 14 test, 0 gagal, 0 error, 0 dilewati |
| Flyway pada database development kosong | V1–V4 berhasil diterapkan |
| Hibernate schema validation | Lulus saat startup aplikasi dan test |
| JAR mandiri, health endpoint | HTTP 200, `{"status":"UP"}` |
| `docker compose --profile test config --quiet` | Lulus |
| Menjalankan container PostgreSQL 18 | Belum terverifikasi: Docker/WSL lokal bermasalah |

## Cakupan yang selesai

- Bootstrap Java 25/Spring Boot 4.1.1, Maven Wrapper, health endpoint.
- PostgreSQL 18, konfigurasi datasource, Compose development/test, Flyway V1–V4.
- Customer: create/get, validasi, email unik yang dinormalisasi.
- Account: create/get/list, nomor otomatis, IDR, saldo awal nol, aturan status/saldo.
- Deposit dan withdrawal dengan histori atomik.
- Transfer internal dengan satu header dan dua catatan debit/credit.
- Histori per rekening dengan pagination dan urutan deterministik.
- Penanganan error terpusat dan validasi request/uang.
- README, arsitektur, schema database, kontrak API, dan histori commit per perubahan logis.

`AccountTransaction` mengimplementasikan entitas Transaction dalam spesifikasi. Cakupan repository yang disebut sebagai `TransactionRepositoryTest` diverifikasi terhadap PostgreSQL melalui `BankingApiIT` (pemisahan rekening, urutan/pagination, foreign key dan histori yang tersimpan), sehingga tidak ditambahkan mock test yang hanya mengulang implementasi repository.

Rollback test memakai trigger PostgreSQL yang menolak credit history setelah header transfer dan debit history di-flush. Sebuah sequence memastikan titik kegagalan tersebut benar-benar tercapai. Setelah exception, test memeriksa saldo kedua rekening serta jumlah header dan histori kembali seperti sebelum transfer.

## Swagger dan OpenAPI

Swagger UI tersedia di `/swagger-ui.html`; definisi JSON di `/v3/api-docs` dan YAML di `/v3/api-docs.yaml`. Library springdoc-openapi 3.1.1 menghasilkan OpenAPI 3.0.1 dengan metadata LedgerBank, lima kelompok fitur, contoh request, schema response/error, serta batas amount dan pagination.

Format OpenAPI 3.0 dipilih karena verifikasi pada stack ini menemukan schema numerik anotasi tidak sesuai saat memakai format 3.1. Test memeriksa amount dan balance sebagai number, parameter size sebagai integer dengan default 20 dan batas 1–100, serta HTTP 201 untuk operasi pembuatan/transaksi.

`OpenApiIT` menambahkan dua test integrasi untuk kontrak 10 endpoint, schema error, Swagger UI, aset JavaScript/CSS, konfigurasi URL lokal, dan keluaran YAML. Total verifikasi kini 74 test: 60 unit/MVC ditambah 14 integrasi. Log verifikasi terbaru berada di `.tools/swagger-verify.log`.

## Penyesuaian dependency test

JUnit Jupiter 6.0.3 mengikuti dependency management Spring Boot 4.1.1. Pin JUnit 5 dari spesifikasi awal menghasilkan `NoSuchMethodError` saat SpringExtension berjalan. Versi dibiarkan dikelola Spring Boot karena [Spring Framework 7 memerlukan JUnit Jupiter 6+](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html). Catatan stack M1 lokal telah diselaraskan.

## Reproduksi verifikasi

Jalur normal setelah Docker/WSL siap ada di [README](../README.md). Untuk verifikasi sesi ini, PostgreSQL 18.6 dari [paket binary Zonky](https://github.com/zonkyio/embedded-postgres-binaries) diunduh ke `.tools/` melalui Maven Central. Direktori runtime, data dan cache tidak masuk Git. Database development `ledgerbank` dan test `ledgerbank_test` berada pada cluster port 55432 yang hanya menerima koneksi loopback.

Aplikasi uji dan cluster portabel dihentikan setelah verifikasi. Jika memakai workspace sesi ini, jalankan kembali:

```powershell
& .\.tools\postgres\bin\pg_ctl.exe -D "$PWD\.tools\pgdata" -l "$PWD\.tools\postgres.log" -o '-h 127.0.0.1 -p 55432' -w start
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55432/ledgerbank'
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/ledgerbank_test'
.\mvnw.cmd -Pintegration verify
.\mvnw.cmd spring-boot:run
```

User/password lokal tetap `ledgerbank`. Setelah menghentikan aplikasi dengan Ctrl+C:

```powershell
& .\.tools\postgres\bin\pg_ctl.exe -D "$PWD\.tools\pgdata" -m fast -w stop
```

Bukti sesi lokal tersedia pada `target/surefire-reports/`, `target/failsafe-reports/`, `.tools/integration-test.log`, dan `.tools/app-smoke.log`; semuanya merupakan output lokal yang tidak masuk Git.

## Batas milestone

Authentication, authorization, idempotency, audit operator, account freeze API, dan workflow interbank belum termasuk implementasi M1. Tidak ada klaim penggunaan produksi atau koneksi BI-FAST. Kriteria menjalankan PostgreSQL dalam Docker perlu diulang setelah masalah WSL mesin diselesaikan.
