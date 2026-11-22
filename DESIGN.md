# DESIGN.md — Tradingin Aja

Dokumen desain resmi untuk **Tradingin Aja**, disusun berdasarkan `SKILL.md` dan selaras dengan konstanta fondasi pada `core/common/.../GameBalance.kt`.

---

## 1. Pitch Game & Target Pemain

### Pitch (1 Paragraf)
**Tradingin Aja** adalah game simulasi trading dan manajemen portofolio *offline-first* di Android yang mengajak pemain memulai perjalanan dengan modal virtual (`vRp`) di pasar fiktif yang dinamis, belajar membaca pergerakan harga dan sentimen berita, mengendalikan risiko lewat *position sizing* serta *stop-loss*, dan naik tingkat secara bertahap dari Trader Pemula hingga Manajer Hedge Fund virtual melalui pembuktian kompetensi—tanpa uang nyata, tanpa taruhan, dan tanpa tekanan *dark pattern*.

### Target Pemain
- **Usia Target (Asumsi Awal):** **13+** (remaja akhir, mahasiswa, pekerja muda, hingga pemain game strategi/simulasi ekonomi yang ingin memahami mekanika pasar modal dan manajemen risiko secara aman).
- **Profil Pemain:**
  - *Pemula penasaran*: ingin mencoba membeli/menjual aset, memahami *spread*, *slippage*, dan *stop-loss* tanpa takut kehilangan tabungan nyata.
  - *Pemain strategi/manajemen*: menyukai progres berbasis keterampilan, optimasi rasio *risk/reward*, pembacaan kondisi pasar (*bull/bear/sideways/crisis*), serta tantangan skenario deterministik.

---

## 2. Daftar Aset Fiktif Awal (10 Aset)

Seluruh aset di bawah ini adalah **100% fiktif** (tidak memakai ticker, merek, atau entitas dunia nyata). Parameter volatilitas ($\sigma$) dan *drift* ($\mu$) dinyatakan dalam basis tahunan (*annualized*) untuk kebutuhan mesin harga deterministik, sedangkan biaya dasar transaksi mengacu pada `GameBalance.DEFAULT_TRADING_FEE_BPS` (`10 bps` / `0,10%`).

| No | Kelas Aset | Sektor / Sub-kelas | Nama Aset Fiktif | Simbol | Harga Awal (`vRp`) | Volatilitas Dasar ($\sigma$) | Drift ($\mu$) | Model Harga Awal | Likuiditas & Spread Dasar | Jam Pasar |
|---|---|---|---|---|---:|---:|---:|---|---|---|
| 1 | Saham | Teknologi | Nusantara Komputasi Tbk | `NSKP` | `4.500,00` | `0.38` | `+0.11` | GBM + Jump | Tinggi (`10 bps`, slip rendah) | Jam Bursa |
| 2 | Saham | Perbankan | Bank Pelita Artha Tbk | `BPAT` | `8.200,00` | `0.22` | `+0.08` | GBM | Sangat Tinggi (`5 bps`, slip sangat rendah) | Jam Bursa |
| 3 | Saham | Konsumer | Pangan Sejahtera Raya Tbk | `PSRY` | `2.850,00` | `0.18` | `+0.06` | GBM | Tinggi (`8 bps`, slip rendah) | Jam Bursa |
| 4 | Saham | Energi Terbarukan | Surya Daya Khatulistiwa Tbk | `SDKT` | `1.650,00` | `0.44` | `+0.09` | GBM + Jump | Menengah (`18 bps`, slip sedang) | Jam Bursa |
| 5 | Kripto | Layer-1 Fiktif | GarudaChain | `GRDC` | `125.000,00` | `0.78` | `+0.14` | GBM + Jump + Vol. Clustering | Tinggi (`20 bps`, slip sedang) | 24/7 |
| 6 | Kripto | DeFi / Utilitas | KopiSwap Token | `KPSW` | `18.400,00` | `0.95` | `+0.10` | GBM + Jump + Vol. Clustering | Rendah–Menengah (`35 bps`, slip tinggi pada order besar) | 24/7 |
| 7 | Komoditas | Logam Mulia | Emas Murni Virtual | `XVMG` | `1.350.000,00` | `0.15` | `+0.05` | GBM + Regime Safe-Haven | Sangat Tinggi (`6 bps`, slip sangat rendah) | 24/5 |
| 8 | Komoditas | Energi | Minyak Bumi Samudra | `MBSO` | `920.000,00` | `0.34` | `+0.03` | Mean Reversion (OU) | Tinggi (`12 bps`, slip rendah) | 24/5 |
| 9 | Forex | Pasangan Mata Uang Fiktif | Dolar Aether / vRupiah | `VUD/VRP` | `15.500,00` | `0.11` | `0.00` | Mean Reversion (OU) | Sangat Tinggi (`3 bps`, slip minimal) | 24/5 |
| 10 | Indeks | Indeks Komposit | Indeks Komposit Nusantara 30 | `IKN30` | `6.800,00` | `0.16` | `+0.075` | Komposit Terbobot + GBM | Sangat Tinggi (`4 bps`, slip minimal) | Jam Bursa |

### Catatan Desain Ekonomi Aset
1. **Diferensiasi Peran:**
   - `BPAT`, `PSRY`, dan `IKN30` berperan sebagai jangkar portofolio berisiko rendah–sedang.
   - `NSKP` dan `SDKT` memberikan eksposur pertumbuhan sekaligus sensitivitas tinggi terhadap berita sektoral.
   - `GRDC` dan `KPSW` melatih pemain menghadapi volatilitas ekstrem dan *slippage* akibat likuiditas tipis (`KPSW`).
   - `XVMG` memiliki korelasi negatif terhadap saham saat rezim *crisis*, mengajarkan fungsi *hedging* secara alami.
   - `MBSO` dan `VUD/VRP` menggunakan *Ornstein–Uhlenbeck (Mean Reversion)* sehingga strategi *buy-and-hold* membabi buta tidak otomatis menguntungkan.
2. **Konversi Harga ke `Money`:** Harga internal pada mesin simulasi boleh dihitung dengan presisi `Double`, lalu dikonversi secara eksplisit ke `Money(Long)` (satuan terkecil = `1/100 vRp`) memakai `HALF_EVEN` untuk eksekusi netral dan tercatat di riwayat order.

---

## 3. Aturan Progres Karier (5 Level) & Syarat Kompetensi

Kenaikan level **tidak ditentukan oleh jumlah uang semata**, melainkan oleh **bukti penguasaan kompetensi risiko dan eksekusi**. Setiap level membuka instrumen dan batas risiko baru secara bertahap:

| Level | Gelar Karier | Fitur & Instrumen Terbuka | Batas Risiko / Leverage | Syarat Kompetensi Naik ke Level Berikutnya |
|---|---|---|---|---|
| **1** | **Trader Pemula** | • Akun spot (`vRp` virtual)<br>• *Market Order* (Beli/Jual)<br>• Aset: Saham & Indeks (`IKN30`)<br>• Mode Belajar dasar | • Leverage: `1×` (tanpa utang/margin)<br>• *Short selling*: nonaktif<br>• Batas posisi tunggal maks: `50%` ekuitas (peringatan lunak jika lebih) | 1. Selesaikan misi belajar **"Dasar Spread, Fee & Stop-Loss"**.<br>2. Lakukan minimal **5 transaksi** pada minimal **2 aset berbeda**.<br>3. Pasang **Stop-Loss aktif** pada minimal **2 transaksi**.<br>4. Selesaikan evaluasi dengan *max drawdown* ≤ **15%**. |
| **2** | **Trader Aktif** | • Membuka *Limit Order*, *Stop Order*, dan *Stop-Limit*<br>• *Take-Profit / Stop-Loss* otomatis (OCO)<br>• Membuka Komoditas & Forex (`XVMG`, `MBSO`, `VUD/VRP`)<br>• Indikator Chart: SMA, EMA, Volume | • Leverage: `1×` (spot)<br>• *Short selling*: nonaktif<br>• Evaluasi konsentrasi portofolio aktif | 1. Lulus misi **"Aturan Risiko 1–2% per Trade"** & **"Tren vs Mean Reversion"**.<br>2. Selesaikan minimal **15 transaksi** (minimal **3 via Limit/Stop** tereksekusi).<br>3. Jaga konsistensi: *Profit Factor* ≥ **1,15** atau rata-rata *Risk/Reward* ≥ **1,2** pada 10 trade terakhir.<br>4. Tidak melanggar konsentrasi > **40%** pada 1 aset selama 10 trade terakhir. |
| **3** | **Trader Profesional** | • Membuka *Short Selling*<br>• Membuka Leverage terbatas<br>• Membuka Kripto Fiktif (`GRDC`, `KPSW`)<br>• Panel Korelasi Aset & RSI | • Leverage maks: `3×` (margin call pada margin level 110%, likuidasi pada 100%)<br>• Biaya *overnight funding/swap* aktif untuk posisi leverage/short | 1. Lulus simulasi **"Manajemen Margin & Anti-Likuidasi"**.<br>2. Selesaikan minimal **25 transaksi**, termasuk minimal **5 posisi Short / Lindung Nilai**.<br>3. **0 kali terkena likuidasi paksa** sepanjang masa evaluasi Level 3.<br>4. *Max drawdown* ≤ **12%** dan isi catatan *post-mortem* di jurnal pada minimal **5 trade rugi**. |
| **4** | **Manajer Dana** | • Mandat **Dana Klien Virtual** (target imbal hasil terukur + batas *drawdown* mandat ketat)<br>• Analitik *Sharpe Ratio* & atribusi sektor<br>• Skenario Tantangan Sejarah Fiktif | • Leverage maks: `5×` (khusus akun pribadi; akun mandat klien dibatasi maks `2×`)<br>• Penalti reputasi jika melanggar batas *drawdown* mandat | 1. Penuhi target mandat klien virtual selama **2 siklus evaluasi berturut-turut** dengan *max drawdown* ≤ **10%**.<br>2. Pertahankan diversifikasi aktif di minimal **4 kelas aset**.<br>3. Selesaikan minimal **1 skenario krisis terseed** (mis. *"Guncangan Likuiditas"*) tanpa terkena likuidasi paksa. |
| **5** | **Hedge Fund** | • Multi-strategi & sub-portofolio (kombinasi Long/Short/Lindung Nilai)<br>• Instrumen lindung nilai terstruktur sederhana (tanpa opsi biner)<br>• Mode Sandbox penuh & Tantangan Mingguan (*replay-verified* jika online diaktifkan) | • Leverage maks tetap `5×` (tidak ada leverage tak terbatas/ekstrem) dengan peringatan margin real-time | **Level Puncak Karier.** Progres berlanjut melalui pencapaian reputasi, variasi strategi di Tantangan Mingguan, dan penyempurnaan metrik portofolio (*Sharpe Ratio*, konsistensi lintas rezim pasar). |

---

## 4. Asumsi-Asumsi Eksplisit

1. **Skala & Saldo Awal Mata Uang Virtual:**
   - Seluruh nilai moneter memakai satuan virtual `vRp` dengan 2 angka desimal (`1 vRp = 100 minorUnits` bertipe `Long`), konsisten dengan `GameBalance.INITIAL_VIRTUAL_CASH_MINOR = 10_000_000_000L` (`vRp 100.000.000,00`) dan `DEFAULT_TRADING_FEE_BPS = 15L` (`0,15%`).
   - *Catatan penyesuaian:* Jika pada Fase 6 nanti diinginkan sensasi "modal awal lebih kecil" (mis. `vRp 10.000.000,00` di mode Karier dan `vRp 100.000.000,00` di Sandbox), nilai tersebut cukup diubah di satu tempat pada `GameBalance.kt` tanpa mengubah struktur `Money`.
2. **Kecepatan Waktu & Simulasi Offline:**
   - 1 *tick* simulasi merepresentasikan 15 menit waktu pasar game; pemain dapat memilih kecepatan `Jeda`, `1×`, `2×`, dan `5×`.
   - Progres *offline* (saat aplikasi ditutup) hanya memproses pemicu posisi yang sudah dibuka pemain (`Stop-Loss`, `Take-Profit`, `Limit Order`) dan **dibatasi maksimal 8 jam waktu game** untuk mencegah *exploit* dan menjaga kontrol pemain.
3. **Kesesuaian Struktur Navigasi Bawah:**
   - Pada Fase 0 saat ini, navigasi bawah memiliki 5 tab placeholder (`Market`, `Trade`, `Portfolio`, `Learn`, `Profile`). Pada fase lanjutan, tab **News / Berita** dan **Career / Misi** dapat ditempatkan sebagai sub-alur di dalam `Market`/`Learn`/`Portfolio` atau disesuaikan ke struktur 5 tab yang disepakati tanpa melanggar batas maksimal 5 item `NavigationBar` Material 3.
4. **Pendekatan Offline-First Tanpa Firebase:**
   - Seluruh mesin simulasi, progres karier, jurnal, dan penyimpanan Room/DataStore berjalan **100% lokal di perangkat** tanpa Firebase. Fitur online (Cloudflare Workers + D1/KV untuk *weekly leaderboard* berbasis *replay verification*) ditunda hingga Fase 9.
5. **Konvensi Visual Aksesibel:**
   - Perubahan naik/turun selalu disertai indikator ganda (ikon panah naik/turun serta tanda `+`/`-`, bukan warna saja), ditambah opsi skema warna ramah buta warna pada pengaturan.

---

## 5. Risiko Desain Terbesar & Mitigasinya

| No | Risiko Desain | Dampak Jika Tidak Ditangani | Strategi Mitigasi Konkret |
|---|---|---|---|
| **1** | **Dinilai sebagai *Simulated Gambling* oleh Google Play / IARC** | Penolakan rilis di Play Store, pembatasan usia 18+, atau penghapusan aplikasi karena dianggap menyerupai judi / opsi biner. | • **Tanpa uang nyata:** Tidak ada deposit, penarikan, hadiah uang, atau konversi nilai uang.<br>• **Tanpa taruhan cepat:** Tidak ada mekanik tebak arah "naik/turun dalam 30 detik" ala opsi biner; semua transaksi adalah pembelian/penjualan unit aset dengan harga, *spread*, dan biaya.<br>• **Tanpa gacha/loot box:** Tidak ada kotak acak berbayar atau bonus login berbasis probabilitas.<br>• **Disclaimer persisten:** Banner *"Simulasi — bukan saran investasi"* tampil jelas pada layar Pasar, Transaksi, dan Portofolio, serta di deskripsi Store. |
| **2** | **Strategi Dominan (*Always Buy-and-Hold* Selalu Menang)** | Game menjadi dangkal karena pemain tidak perlu belajar manajemen risiko atau diversifikasi. | • Terapkan **4 rezim pasar** (*Bull, Bear, Sideways, Crisis*) dengan matriks transisi serta *Ornstein–Uhlenbeck* untuk Forex & Komoditas energi.<br>• Terapkan biaya simpan/*funding* pada posisi leverage dan *slippage* pada likuiditas rendah.<br>• Jalankan **simulasi headless ≥ 1.000 run** (Fase 8) untuk menguji bahwa *buy-and-hold* tidak mendominasi seluruh skenario. |
| **3** | **Normalisasi Leverage Ekstrem & Kehilangan Kontrol Pemain** | Memberikan pesan edukasi keliru kepada pemula seolah trading ber-leverage tinggi aman dan mudah. | • Kunci leverage pada Level 1–2 (`1×`).<br>• Buka leverage hanya setelah lulus ujian kompetensi risiko di Level 3, dengan batas keras maksimal `3×`–`5×`.<br>• Tampilkan **ringkasan risiko, estimasi harga likuidasi, dan total biaya** secara eksplisit sebelum tombol konfirmasi order ditekan. |
| **4** | **Dark Pattern Psikologis / FOMO Berlebihan** | Membuat game terasa manipulatif dan tidak etis (mis. timer hitung mundur yang menekan pemain). | • Dilarang keras memakai notifikasi mendesak (*"Kesempatan habis dalam 2 menit!"*), penalti karena tidak membuka aplikasi, atau tombol beli satu ketukan tanpa konfirmasi biaya.<br>• Fitur jeda pasar (`Pause`) selalu tersedia di mode single-player agar pemain bisa berpikir tenang. |
| **5** | **Kecurangan Seed & Manipulasi Skor Lokal** | Papan peringkat mingguan (saat fase online tiba) menjadi tidak adil jika klien mengirim skor mentah atau menebak pergerakan harga dari seed. | • Seed simulasi **tidak ditampilkan** di mode Karier dan Tantangan Mingguan.<br>• Simpan state game di Room dengan checksum integritas ringan.<br>• Pada Fase 9, validasi skor leaderboard dilakukan di server (Cloudflare Worker) lewat **replay log aksi** menggunakan seed resmi server. |
| **6** | **Galat Presisi Moneter & Penurunan Frame Rate Chart** | Saldo meleset akibat *floating-point* (`Double`) atau UI macet saat menampilkan ribuan *candle*. | • Semua saldo, ekuitas, biaya, dan PnL tersimpan sebagai `Money(Long)` dengan aturan pembulatan terdokumentasi di `Money.kt`.<br>• Mesin simulasi berjalan pure Kotlin di background dispatcher, dan chart Canvas melakukan *downsampling* + *path caching* agar tetap 60 fps (< 16 ms/tick). |
