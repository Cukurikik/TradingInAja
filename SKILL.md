---
name: trading-game-dev
description: Skill untuk agent yang membangun game simulasi trading di Android (Kotlin + Jetpack Compose). Gunakan setiap kali tugas menyentuh desain game, mesin pasar/harga, order & portofolio, ekonomi/balancing, arsitektur kode, chart/UI, save data, kepatuhan, pengujian, atau rilis game trading.
---

# [TRADINGIN AJA] — Skill Agent Pengembangan Game Trading

## 1. Ringkasaningkasan Produk

| Item | Nilai |
|---|---|
| Genre | Simulasi trading / manajemen portofolio (uang virtual) |
| Platform | Android (Kotlin, Jetpack Compose, Material 3) |
| Mode | Offline-first, single player; online opsional (leaderboard, event) |
| Fantasi inti | Pemain memulai dengan modal kecil, belajar membaca pasar, mengelola risiko, dan tumbuh dari trader pemula menjadi manajer dana besar |
| Usia target (asumsi) | 13+ (ubah sesuai keputusan; lihat bagian 10) |

**Pilar desain**
1. **Risiko bermakna** — profit besar selalu datang dengan risiko nyata (margin call, slippage, biaya).
2. **Belajar sambil bermain** — konsep (diversifikasi, stop-loss, position sizing) diajarkan lewat mekanik, bukan ceramah.
3. **Pasar yang terasa hidup** — tren, volatilitas, berita, dan korelasi antar aset, tetapi deterministik dan dapat diuji.
4. **Etis** — uang virtual saja, tanpa judi, tanpa tekanan psikologis berbahaya.

---

## 2. Peran & Perilaku Agent

Agent = **Game Designer + Quant/Economy Designer + Android Engineer + UI/UX + QA**.

**Aturan kerja wajib**
- **Audit sebelum klaim:** baca kode/berkas yang relevan sebelum menyatakan kondisi proyek.
- **Perbaikan terarah:** sebut path berkas spesifik + kode lengkap, bukan saran umum.
- **Jangan ubah format berkas** tanpa instruksi eksplisit.
- **Berkas utuh** yang siap pakai, bukan kerangka.
- **Satu fase per giliran;** jangan lompat fase sebelum Definition of Done terpenuhi.
- **Tanyakan hanya jika terblokir;** selain itu tulis asumsi lalu lanjut.
- Bahasa balasan: Bahasa Indonesia (istilah teknis boleh Inggris).

---

## 3. Tech Stack & Arsitektur

### 3.1 Stack
- Kotlin 100% · Jetpack Compose + Material 3
- MVVM + Clean Architecture, Unidirectional Data Flow
- Coroutines + Flow (`StateFlow`/`SharedFlow`)
- Hilt (DI) · Room (state game) · DataStore (pengaturan)
- kotlinx.serialization · Navigation Compose (type-safe)
- WorkManager (kemajuan offline terbatas, notifikasi event)
- Backend opsional: Cloudflare Workers + D1/KV (leaderboard, cloud save). **Tanpa Firebase.**
- Gradle Kotlin DSL + version catalog · Min SDK 24 · Target SDK terbaru syarat Play Store

### 3.2 Struktur modul

```
app/                       -> entry, navigasi, DI root
core/
  common/                  -> Result, DispatcherProvider, Money (value class)
  designsystem/            -> tema, komponen Compose (ChartView, PriceTag, dll.)
  database/                -> Room, DAO, entity, migrasi
  datastore/
feature/
  home/                    -> dashboard: ekuitas, P&L hari ini, notifikasi
  market/                  -> daftar aset, watchlist, detail & chart
  trade/                   -> tiket order (buy/sell, tipe order)
  portfolio/               -> posisi, riwayat, analitik
  news/                    -> berita & event pasar
  learn/                   -> tutorial & misi edukasi
  career/                  -> level, tantangan, pencapaian
  settings/
domain/
  engine/                  -> PURE KOTLIN: MarketEngine, OrderEngine, RiskEngine, EventEngine
  model/
  usecase/
data/
  repository/
```

**Aturan keras:** `domain/engine` **tanpa import Android**, deterministik, seeded RNG.

### 3.3 Konvensi kode
- Composable stateless; logika bisnis hanya di ViewModel/UseCase/Engine.
- `sealed interface` untuk UiState, UiEvent, UiEffect.
- **Uang:** simpan sebagai `Long` (satuan terkecil) dalam `@JvmInline value class Money`. **Dilarang `Double` untuk saldo/PnL yang disimpan.** Harga pasar boleh `Double` di mesin harga, tetapi dikonversi ke `Money` secara eksplisit saat eksekusi dengan aturan pembulatan terdokumentasi.
- String di `strings.xml` (id, en). Konstanta balancing di `GameBalance.kt`.
- Tidak ada magic number.

---

## 4. Desain Game

### 4.1 Core loop
```
Analisis pasar → Rencana trade → Eksekusi order → Kelola posisi/risiko
      ↑                                                   ↓
      └────── Reputasi, XP, modal baru, fitur terbuka ←───┘
```

### 4.2 Progres (karier)
1. **Trader Pemula** — modal kecil, hanya aset spot, tanpa leverage.
2. **Trader Aktif** — order limit/stop, watchlist, indikator dasar.
3. **Trader Profesional** — leverage terbatas, short selling, korelasi aset.
4. **Manajer Dana** — kelola dana klien virtual, target & batas drawdown.
5. **Hedge Fund** — derivatif sederhana (opsional), multi-strategi, lindung nilai.

Fitur terbuka lewat **kompetensi** (mis. lulus misi manajemen risiko), bukan sekadar uang.

### 4.3 Sistem utama

**A. Mesin Pasar (MarketEngine)**
- Aset fiktif per kelas: Saham (sektor), Kripto, Komoditas, Forex, Indeks.
- Model harga bertingkat (mulai sederhana, tambah bertahap):
  1. **Geometric Brownian Motion** (drift + volatilitas)
  2. **Mean reversion** (Ornstein–Uhlenbeck) untuk aset tertentu
  3. **Jump diffusion** untuk kejutan berita
  4. **Regime switching** (bull / bear / sideways / crisis) dengan matriks transisi
  5. **Korelasi antar aset** (matriks korelasi → Cholesky untuk shock terkorelasi)
  6. **Volatility clustering** (GARCH sederhana) agar chart terasa realistis
- Output per tick: harga, OHLC candle (timeframe 1m/5m/1h/1d game), volume sintetis, spread bid-ask.
- **Seeded RNG** (`input: seed, state → newState`) agar reproducible.

**B. Order & Eksekusi (OrderEngine)**
- Tipe order: Market, Limit, Stop, Stop-Limit; opsi Take-Profit / Stop-Loss (OCO).
- Realisme: **spread, slippage** (fungsi dari ukuran order & likuiditas), **komisi**, **swap/funding** untuk posisi bermalam.
- Posisi: long/short, rata-rata harga masuk, PnL belum/terealisasi.
- Aturan idempoten: order punya ID; eksekusi ulang tidak menggandakan efek.

**C. Risiko (RiskEngine)**
- Margin, leverage, **margin call & likuidasi paksa** (dijelaskan jelas ke pemain).
- Metrik: ekuitas, drawdown, Sharpe sederhana, win rate, risk/reward, exposure per aset.
- Batas risiko per level karier.

**D. Berita & Event (EventEngine)**
- Berita terjadwal (laporan keuangan, suku bunga, data makro) dan acak (krisis, regulasi, inovasi).
- Berita memengaruhi drift/volatilitas/jump dengan **arah yang tidak selalu jelas** (pemain harus menafsirkan).
- Semua event terdata (JSON ter-versi) → mudah ditambah tanpa ubah kode.

**E. Portofolio & Analitik**
- Alokasi, kurva ekuitas, riwayat trade, jurnal trade (catatan pemain).
- "Post-mortem" otomatis: kenapa trade rugi (tanpa stop-loss, over-leverage, dll.).

**F. Edukasi & Misi**
- Misi bertahap: pasang stop-loss, diversifikasi, ukuran posisi 1–2% risiko, baca candle.
- Tooltip istilah; mode "Belajar" tanpa risiko.

**G. Mode Permainan**
- **Karier** (progres jangka panjang) · **Tantangan** (skenario terseed: "Krisis 2008-gaya", "Bull run") · **Sandbox** (modal & pasar bebas) · **Mingguan** (seed sama untuk semua pemain → leaderboard adil).

### 4.4 Aturan balancing
- Semua formula di `MarketFormulas.kt`/`GameBalance.kt`, terdokumentasi.
- Hindari "strategi dominan" (mis. selalu beli lalu tahan = menang). Uji dengan **simulasi headless 1.000+ run** per skenario.
- Pemain baru harus mengalami trade pertama < 3 menit dan pelajaran risiko pertama < 15 menit.
- Pasar tidak boleh bisa "diprediksi" dari seed oleh pemain (seed tidak ditampilkan di mode karier).

---

## 5. Mesin Simulasi

- **Tick-based:** 1 tick = satuan waktu game; kecepatan 1× / 2× / 5× / jeda.
- Deterministik: `(state, action, seed) → newState`.
- Pisahkan: `GameClock`, `MarketEngine`, `OrderEngine`, `RiskEngine`, `EventEngine`.
- **Offline progress:** hanya untuk pengelolaan posisi yang sudah ada (SL/TP/limit terisi) dan dibatasi maksimum (mis. 8 jam game-equivalent); tanpa bonus yang memaksa membuka app terus.
- Seluruh state serializable → save/load/migrasi mudah.
- Target < 16 ms per tick pada perangkat low-end (RAM 3 GB).

---

## 6. UI/UX

- **Material 3**, tema terang/gelap/AMOLED.
- Navigasi bawah: Home · Market · Portfolio · News · More.
- **Chart (Compose Canvas):** candlestick, line, area; zoom/pan; crosshair; overlay indikator (SMA/EMA, RSI, volume). Downsample data besar; cache path.
- **Tiket order** ringkas: ukuran, tipe, SL/TP, ringkasan biaya & risiko **sebelum** konfirmasi.
- Warna naik/turun **tidak bergantung warna saja** (ikon panah, tanda +/−); sediakan palet ramah buta warna & opsi konvensi warna (hijau-naik atau merah-naik).
- Ukuran sentuh ≥ 48dp, kontras WCAG AA, `contentDescription` semua ikon.
- 60 fps: `LazyColumn` dengan `key`, hindari recomposition berlebih.

---

## 7. Data, Save, Keamanan

- **Room:** akun, posisi, order, riwayat, kemajuan misi; migrasi skema terversi & teruji.
- **Autosave** pada peristiwa penting + interval; slot ganda; ekspor/impor JSON ter-versi.
- Integritas save: checksum/HMAC ringan (anti-korupsi, bukan anti-cheat berat).
- Leaderboard online: **validasi di server** (Cloudflare Worker) dengan memutar ulang seed + log aksi (replay verification) — jangan percaya skor dari klien.
- Tidak ada rahasia di APK; HTTPS only; R8/ProGuard aktif untuk rilis.

---

## 8. Pengujian & Kualitas

| Level | Alat | Cakupan |
|---|---|---|
| Unit | JUnit5, MockK, Turbine | Formula harga, OrderEngine, RiskEngine (≥ 80% `domain/engine`) |
| Properti | kotest-property / manual | Invarian: saldo tidak negatif tanpa likuidasi, PnL = Σ(fill) |
| Determinisme | Unit | Seed sama ⇒ hasil identik |
| Integrasi | Room in-memory, Hilt test | Repository, migrasi DB |
| UI | Compose UI Test | Alur: buka aset → order → posisi → tutup |
| Simulasi | Skrip headless | 1.000+ run: deteksi eksploit, strategi dominan, inflasi uang |
| Performa | Macrobenchmark | Startup, scroll chart, tick engine |

**Definition of Done (per fitur)**
- [ ] ktlint/detekt bersih
- [ ] Tes unit/UI terkait lulus
- [ ] Tidak ada crash/ANR di alur utama
- [ ] String id/en lengkap
- [ ] Aksesibilitas dasar terpenuhi
- [ ] Disclaimer "simulasi" ada di layar relevan
- [ ] Dokumentasi singkat diperbarui

---

## 9. Format Jawaban Agent

1. **Ringkasan** (1–3 kalimat)
2. **Temuan audit** (path nyata)
3. **Perubahan** per berkas:
   ```
   📄 feature/trade/src/main/java/.../TradeViewModel.kt
   - Alasan: ...
   - [kode lengkap]
   ```
4. **Cara uji** (perintah Gradle / langkah manual)
5. **Risiko & catatan**

**Larangan:** klaim tanpa baca kode · saran tanpa path · ubah format berkas tanpa izin · `Double` untuk saldo · merek/ticker nyata tanpa izin · fitur di luar fase.

---

## 10. Kepatuhan, Etika & Kebijakan

> Bagian ini penting: game trading rentan dinilai sebagai "simulated gambling" oleh store.

- **Uang virtual saja.** Tidak ada top-up yang menghasilkan uang nyata, tidak ada penarikan, tidak ada hadiah bernilai uang.
- **Tanpa loot box/gacha**; tanpa mekanik taruhan; tanpa taruhan arah ("naik/turun dalam 30 detik") yang menyerupai opsi biner/judi.
- Disclaimer jelas: *"Simulasi edukatif — bukan saran investasi."*
- Jangan menormalisasi leverage ekstrem: tampilkan peringatan risiko, batas leverage per level.
- Tidak ada notifikasi menekan/FOMO ("Peluang hilang 2 menit lagi!").
- Aset fiktif (bukan merek/ticker nyata) kecuali data berlisensi.
- Pembelian dalam aplikasi (jika ada): hanya kosmetik/tema/hapus iklan/paket skenario, dengan konfirmasi jelas.
- **Cek kebijakan Google Play** soal *Real-Money Gambling* & *Simulated Gambling*, kuesioner **IARC**, dan **Data Safety**. Pilih target usia setelah membaca kebijakan terbaru (verifikasi di dokumentasi resmi sebelum rilis).
- Hormati UU PDP Indonesia; kumpulkan data minimal; siapkan Kebijakan Privasi.

---

## 11. Roadmap

| Fase | Fokus | Output |
|---|---|---|
| 0 | Fondasi proyek | Build hijau, modul, DI, tema, CI |
| 1 | Mesin harga + chart dasar | 1 aset berjalan, candle tergambar |
| 2 | Multi-aset + watchlist | Daftar aset, detail, korelasi dasar |
| 3 | Order & portofolio | Market/limit, posisi, PnL |
| 4 | Risiko lanjutan | Stop/TP, leverage, margin call |
| 5 | Berita & event | Event data-driven, regime |
| 6 | Karier, misi, edukasi | Progres & tutorial |
| 7 | Save, offline, pengaturan | Room, autosave, ekspor |
| 8 | Polish, QA, balancing headless | Beta tertutup |
| 9 | Online opsional | Leaderboard tervalidasi (Cloudflare) |
| 10 | Rilis & live ops | Play Store, event berkala |

---

## 12. Checklist Rilis Play Store

- [ ] AAB ditandatangani, R8 aktif
- [ ] Data Safety akurat · IARC selesai · Kebijakan Privasi aktif
- [ ] Verifikasi kebijakan "simulated gambling" terbaru
- [ ] Uji di layar kecil/besar & perangkat low-end
- [ ] Closed testing sesuai persyaratan akun developer
- [ ] Aset store (ikon, screenshot, deskripsi id/en) dengan disclaimer simulasi

---

## 13. Prompt Cepat (Contoh)

- *"Audit `domain/engine/` lalu buat `MarketEngine.kt` (GBM + seeded RNG) lengkap dengan unit test determinisme."*
- *"Buat `CandlestickChart.kt` di `core/designsystem` dengan Compose Canvas: zoom, pan, crosshair, downsample."*
- *"Jalankan simulasi headless 1.000 run skenario 'bull run' dan laporkan apakah buy-and-hold mendominasi."*
- *"Tinjau seluruh layar terhadap checklist kepatuhan bagian 10."*

---

*Dokumen hidup: perbarui bagian 4, 8, dan 11 seiring proyek berkembang.*
