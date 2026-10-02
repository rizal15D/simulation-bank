# LedgerBank Technical Debt

Inventory ini mencatat debt yang ditemukan selama Milestone 3 melalui integration/concurrency test, profiling, pengukuran database, dan review kode. Prioritas mempertimbangkan risiko terhadap konsistensi transaksi, security boundary, reliability, dan kemampuan operasi. Status diperbarui bersama implementasi agar dokumen ini tetap dapat diaudit.

## Ringkasan prioritas

| ID | Masalah | Prioritas | Status |
|---|---|---|---|
| TD-01 | Broker I/O dilakukan saat transaksi dan lock outbox PostgreSQL masih terbuka | Tinggi | Dipilih untuk Milestone 3 |
| TD-02 | Service banking memiliki overload internal tanpa authenticated principal | Tinggi | Dipilih untuk Milestone 3 |
| TD-03 | Workflow idempotensi transfer mencampur validasi/fingerprint dan koordinasi infrastructure | Sedang | Dipilih untuk Milestone 3 |
| TD-04 | Offset pagination dan count query histori bertambah mahal pada halaman sangat dalam | Sedang | Ditunda; belum menjadi bottleneck terukur |
| TD-05 | Kontensi rekening bersama dapat memenuhi connection pool dan menambah parked HTTP threads | Sedang | Ditunda; perlu target kapasitas/SLO |

## TD-01 — Broker I/O di dalam transaksi outbox

**Masalah dan bukti.** `OutboxDeliveryService.publishReadyBatch()` membuka transaksi, mengambil batch dengan `FOR UPDATE SKIP LOCKED`, lalu memanggil publisher RabbitMQ synchronous untuk setiap event sebelum transaksi selesai. Publisher confirm dapat menunggu sampai `OUTBOX_CONFIRM_TIMEOUT` (default lima detik). Selama itu koneksi database dan row lock batch tetap dipegang.

**Dampak/risiko.** Broker yang lambat dapat memperpanjang transaksi database, menahan koneksi Hikari, memperbesar waktu lock, dan mengurangi throughput delivery. Batch berisi 50 event secara teori dapat menahan satu transaksi jauh lebih lama daripada operasi database yang diperlukan. Reliability tetap at-least-once, tetapi resource coupling ini memperbesar blast radius outage RabbitMQ ke PostgreSQL.

**Prioritas.** Tinggi, karena jalur recovery reliability seharusnya tidak mempertahankan transaksi database selama network I/O.

**Rencana perbaikan.** Claim event ready dalam transaksi pendek dengan lease pada `next_attempt_at`, commit, publish di luar transaksi, lalu tandai sukses/gagal dalam transaksi pendek per event. Crash setelah publish tetapi sebelum acknowledgement database tetap menghasilkan redelivery yang aman karena consumer idempotent.

**Status.** Dipilih untuk Milestone 3. Test harus membuktikan claim, publish, retry/backoff, dan recovery lease tanpa mengubah kontrak event.

## TD-02 — Jalur service tanpa principal

**Masalah dan bukti.** `DepositService`, `WithdrawalService`, `TransferService`, dan `TransactionHistoryService` memiliki overload package-private tanpa `BankingPrincipal`. Implementasi meneruskan `null` sebagai actor dan melewati `OwnershipPolicy`; overload tersebut terutama dipakai unit test lama.

**Dampak/risiko.** Kode baru di package yang sama dapat tanpa sengaja memakai jalur tanpa ownership check. Nullable actor juga memaksa conditional security di dalam mutasi uang dan memungkinkan event transfer tanpa actor, sehingga security boundary tidak dinyatakan oleh type/API service.

**Prioritas.** Tinggi, karena defense in depth pada ownership lebih penting daripada kenyamanan fixture test.

**Rencana perbaikan.** Hapus overload tanpa principal, wajibkan `BankingPrincipal` non-null pada seluruh entry point banking, dan perbarui unit test menggunakan principal yang sesuai. Pertahankan kontrak HTTP dan response yang ada.

**Status.** Dipilih untuk Milestone 3. Unit dan integration test ownership wajib tetap lulus.

## TD-03 — Tanggung jawab berlebih pada workflow idempotensi transfer

**Masalah dan bukti.** `IdempotentTransferService` melakukan pengukuran, validasi header, validasi amount/account, canonical encoding, SHA-256 fingerprint, cache lookup, distributed locking, durable lookup, transaksi transfer, failure event, dan cache write. Validasi amount/account juga diulang oleh `TransferService`.

**Dampak/risiko.** Perubahan canonical request atau aturan validasi berisiko mengubah replay semantics secara tidak sengaja. Test coordinator perlu mengetahui detail kriptografi, sementara duplicate validation membuat urutan error dan fingerprint lebih sulit dipahami.

**Prioritas.** Sedang, karena perilaku sekarang benar tetapi konsentrasi tanggung jawab memperbesar risiko perubahan pada jalur uang yang kritis.

**Rencana perbaikan.** Ekstrak validator/fingerprinter deterministik yang menghasilkan request tervalidasi beserta hash canonical. Uji null/empty description, normalisasi decimal, invalid key, dan perubahan payload secara terisolasi. Coordinator tetap fokus pada Redis, transaksi, durable replay, event, dan metrics.

**Status.** Dipilih untuk Milestone 3. Kontrak `Idempotency-Key` dan response API tidak berubah.

## TD-04 — Skalabilitas pagination histori

**Masalah dan bukti.** Endpoint memakai `Page` berbasis offset sehingga setiap request menjalankan content query dan count query. Halaman dalam harus membaca lalu membuang row sebelum offset. Analisis pada 19.327 total transaksi dan 1.000 transaksi target mencatat page pertama 0,074 ms, count 0,658 ms, dan offset 900 sebesar 0,629 ms; seluruhnya masih memakai index yang tepat. Bukti lengkap ada di `docs/performance/database-analysis.md`.

**Dampak/risiko.** Pada rekening dengan jutaan transaksi, offset dan exact count dapat tumbuh mahal walaupun index tersedia.

**Prioritas.** Sedang.

**Rencana perbaikan.** Saat dataset/SLO menunjukkan regresi, tambah endpoint cursor/keyset berbasis `(created_at, id)` dan pertimbangkan `Slice` untuk menghindari exact count. Perubahan kontrak harus versioned dan dibenchmark dengan dataset representatif.

**Status/alasan ditunda.** Ditunda karena baseline tidak menunjukkan bottleneck; optimasi sekarang akan menambah kompleksitas dan mengubah kontrak tanpa bukti kebutuhan.

## TD-05 — Backpressure saat rekening sangat terkontensi

**Masalah dan bukti.** Eksperimen 40 virtual users pada pasangan rekening yang sama menghasilkan Hikari active peak 10, pending peak 63, PostgreSQL menunggu `Lock:tuple` sampai delapan session dan `Lock:transactionid` satu session, serta 31 HTTP thread menunggu connection pool. Detail JFR dan thread dump ada di `docs/performance/jvm-analysis.md`.

**Dampak/risiko.** Hot account dapat menaikkan tail latency dan menghabiskan worker/connection sebelum operasi lain mendapat resource, walaupun invariant saldo tetap benar.

**Prioritas.** Sedang; test ini sengaja ekstrem dan baseline normal tidak menunjukkan error.

**Rencana perbaikan.** Tetapkan SLO dan kapasitas target terlebih dahulu, lalu evaluasi bounded Tomcat workers/accept queue, transaction timeout, pool sizing, rate limit per actor/account, atau queueing. Bandingkan error rate serta p95/p99 sebelum dan sesudah.

**Status/alasan ditunda.** Ditunda karena tidak ada target deployment atau SLO yang membenarkan angka konfigurasi produksi. Menebak pool size dari laptop development berisiko memindahkan bottleneck.
