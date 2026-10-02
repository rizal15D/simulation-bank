# LedgerBank performance workload

Workload k6 ini mengukur dua flow terautentikasi secara terpisah:

- `history`: `GET /api/v1/accounts/{accountId}/transactions?page=0&size=20`
- `transfer`: `POST /api/v1/transfers`

Script membuat user dan rekening sintetis melalui API publik. Ia menolak `TARGET_ENV` selain
`local`, `development`, atau `test`, tetapi target tetap harus diperiksa sebelum command dijalankan.
Jangan arahkan workload ke production atau database berisi data nyata.

## Menjalankan

Jalankan LedgerBank dan seluruh dependency lokal terlebih dahulu. Hasil JSON disimpan di
`target/performance/`, yang perlu dibuat sebelum container dijalankan.

Bash (Linux, macOS, Git Bash, atau WSL):

```bash
mkdir -p target/performance
docker run --rm --add-host=host.docker.internal:host-gateway \
  -e TARGET_ENV=local -e BASE_URL=http://host.docker.internal:8080 \
  -e WORKLOAD=history -e VUS=4 -e WARMUP_DURATION=15s -e DURATION=30s \
  -e HISTORY_ENTRIES=1000 -v "$PWD:/work" -w /work grafana/k6:2.3.0 \
  run --summary-mode=full --summary-export=/work/target/performance/history-summary.json \
  /work/performance/k6/banking.js
```

PowerShell:

```powershell
New-Item -ItemType Directory -Force target/performance | Out-Null
docker run --rm --add-host=host.docker.internal:host-gateway `
  -e TARGET_ENV=local -e BASE_URL=http://host.docker.internal:8080 `
  -e WORKLOAD=transfer -e VUS=4 -e WARMUP_DURATION=15s -e DURATION=30s `
  -v "${PWD}:/work" -w /work grafana/k6:2.3.0 `
  run --summary-mode=full --summary-export=/work/target/performance/transfer-summary.json `
  /work/performance/k6/banking.js
```

Gunakan konfigurasi yang sama untuk perbandingan sebelum/sesudah. Parameter yang didukung:

| Variable | Default | Fungsi |
|---|---:|---|
| `WORKLOAD` | `history` | `history` atau `transfer` |
| `BASE_URL` | `http://host.docker.internal:8080` | Target API |
| `TARGET_ENV` | `local` | Guard environment non-production |
| `VUS` | `4` | Virtual user untuk warm-up dan pengukuran |
| `WARMUP_DURATION` | `15s` | Durasi warm-up |
| `DURATION` | `30s` | Durasi pengukuran |
| `HISTORY_ENTRIES` | `1000` | Jumlah entry pada rekening history |
| `INITIAL_BALANCE` | `1000000000` | Saldo sintetis awal setiap rekening |
| `REQUEST_TIMEOUT` | `30s` | Timeout setiap request |

Gunakan metrik khusus berikut untuk baseline karena metrik HTTP bawaan juga mencakup setup dan
warm-up:

- `ledgerbank_latency`: average, p50, p95, p99, maksimum, dan jumlah request measurement.
- `ledgerbank_requests`: jumlah request measurement; hitung throughput sebagai `count / DURATION`
  karena rate counter pada summary dibagi terhadap seluruh lifecycle k6, termasuk setup dan warm-up.
- `ledgerbank_errors`: error rate response measurement.

Setup membuat dua pasangan rekening per VU. Masing-masing VU transfer mendapat pasangan sendiri
agar baseline mengukur alur write normal, bukan kontensi lock yang sudah memiliki concurrency test
terpisah. Dataset sengaja tidak dihapus agar query plan dan profiling terhadap run yang sama dapat
dianalisis; gunakan hanya database development yang dapat dibuang.
