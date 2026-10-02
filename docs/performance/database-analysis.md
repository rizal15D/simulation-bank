# Transaction History Database Analysis

Analisis ini memverifikasi query history Milestone 1 pada PostgreSQL 18. Tujuannya bukan menambah
index baru, melainkan membuktikan apakah index existing dipakai dan apakah ada bottleneck terukur.

## Query dan index baseline

`TransactionHistoryService` memvalidasi account/ownership lalu memakai Spring Data `Page` dengan
sort berikut:

```text
createdAt DESC, id DESC
```

Migration V4 sudah menyediakan:

```sql
CREATE INDEX idx_transactions_account_history
ON account_transactions(account_id, created_at DESC, id DESC);
```

Satu HTTP request page history menghasilkan tiga SQL query:

1. `accounts.findById` untuk existence dan ownership;
2. data page dengan `LIMIT/OFFSET`;
3. count query untuk `totalElements` dan `totalPages`.

Entity history menyimpan UUID/scalar tanpa association lazy. Mapping ke DTO tidak mengakses entity
lain, sehingga tidak ada N+1 query pada flow ini.

## Dataset dan reproduksi

Plan diambil pada database Compose PostgreSQL 18.6 setelah `ANALYZE account_transactions`:

```text
total account_transactions = 19.327
target account history     = 1.000
page size                  = 20
deep offset                = 900
```

Script reproduksi tersedia di
[`performance/sql/transaction-history-explain.sql`](../../performance/sql/transaction-history-explain.sql).
Contoh eksekusi dari container dapat dilakukan dengan menyalin script ke container development,
lalu:

```bash
psql -U ledgerbank -d ledgerbank \
  -v account_id=<synthetic-account-uuid> -v page_size=20 -v deep_offset=900 \
  -f transaction-history-explain.sql
```

Output plan yang dipakai untuk keputusan tersimpan di
[`transaction-history-plan.txt`](../assets/performance/transaction-history-plan.txt).

## Hasil

| Query | Plan | Row index/heap yang diproses | Shared hit | Planning | Execution |
|---|---|---:|---:|---:|---:|
| Page 0, limit 20 | Index Scan | 20 | 4 | 0,334 ms | 0,074 ms |
| Count | Index Only Scan, heap fetch 0 | 1.000 | 17 | 0,530 ms | 0,658 ms |
| Offset 900, limit 20 | Bitmap index/heap + quicksort | 1.000; sort 920 | 29 | 0,182 ms | 0,629 ms |

Page pertama memakai urutan index secara langsung dan berhenti setelah 20 row. Count memakai index
only scan tanpa heap fetch. Pada offset 900, planner memilih membaca seluruh 1.000 row account lalu
quicksort 920 row yang diperlukan sebelum limit; ini memperlihatkan biaya offset bertumbuh terhadap
kedalaman halaman walaupun waktunya masih kecil pada dataset ini.

## Perbandingan dengan latency API

Baseline HTTP history menghasilkan average 17,84 ms dan p95 27,77 ms. Execution page query 0,074 ms
dan count 0,658 ms hanya sebagian kecil dari latency end-to-end, yang juga mencakup Redis session,
ownership query, Hibernate mapping, serialization, filter/security, dan network loopback. Plan tidak
menunjukkan sequential scan atau sort pada page pertama.

## Write cost

Setiap insert history memperbarui primary key dan index account-history. Row transfer juga memenuhi
partial unique index `(transfer_id, transaction_type)`. Index history karena itu memiliki biaya write
nyata, tetapi melayani endpoint read utama dan urutan pagination. Menambahkan index lain dengan
prefix/order yang sama hanya memperbesar write amplification dan storage tanpa manfaat plan baru.

## Keputusan

Tidak ada migration index atau perubahan query pada Milestone 3 karena:

- page pertama sudah memakai `idx_transactions_account_history` secara optimal;
- count memakai index-only scan;
- execution time seluruh query di bawah 1 ms pada dataset representatif lokal;
- tidak ada N+1;
- latency API tidak didominasi query data page;
- index duplikat akan memperlambat mutasi saldo/transfer.

Keyset/cursor pagination tetap masuk technical debt terencana untuk account dengan history sangat
besar. Perubahan itu memengaruhi kontrak response dan belum dibenarkan oleh dataset 1.000 row per
account. Evaluasi ulang memerlukan dataset jauh lebih besar serta perbandingan API yang sama sebelum
dan sesudah perubahan.

## Keterbatasan

- Semua buffer adalah cache hit; cold-cache disk I/O tidak diukur.
- Dataset per account 1.000 row belum mewakili histori multi-tahun.
- Satu mesin Docker Desktop tidak mewakili storage/CPU production.
- Offset 900 membuktikan bentuk scaling, bukan ambang kapan keyset wajib digunakan.
