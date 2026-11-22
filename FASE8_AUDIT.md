# FASE 8 — Audit Menyeluruh, Balancing Headless, Performa, Aksesibilitas & Kepatuhan

Dokumen ini adalah keluaran Fase 8 (Polish, QA, Balancing Headless) untuk **Tradingin Aja**.
Seluruh temuan di bawah berasal dari pembacaan kode di repo ini dan dari eksekusi nyata
skrip simulasi headless baru (`:simulation:headlessSimulation`).

---

## 1. Ringkasan

1. **Simulasi headless 5 skenario × 1.000 run × 1.000 tick (15.000 run, 15 juta tick) sudah dijalankan.**
   Tidak ada satu strategi yang menang di semua skenario: *buy-and-hold* menang di Bull/Sideways/Mixed,
   *mean reversion* menang di Bear/Crisis. Namun dominasi pasif masih **3 dari 5 skenario**.
2. **Ditemukan bug mesin nyata:** modifier rezim (drift/volatilitas) diabaikan ketika
   `enableRegimeSwitching = false`, sehingga skenario satu-rezim berperilaku identik. Sudah diperbaiki.
3. **Modal cenderung deflasi, bukan inflasi.** Sebelum kalibrasi median buy-and-hold: Sideways −0,77%,
   Bear −10,14%, Crisis −16,21%. Setelah kalibrasi drift rezim **dan** fee 10 bps: Bull +3,12%,
   Sideways +0,97%, Bear −8,52%, Crisis −13,55%, Mixed −2,35% (inflasi 1,031 / 1,010 / 0,915 /
   0,864 / 0,977×).
4. **Tick engine jauh di bawah anggaran:** P99 `MarketEngine.stepAll` (10 aset + event + GARCH +
   korelasi) = **223,5 µs** di JVM desktop (anggaran 16.000 µs; rentang antar-run 193–317 µs). Verifikasi perangkat low-end
   disediakan lewat modul Macrobenchmark baru yang belum dapat dieksekusi tanpa perangkat.
5. **Aksesibilitas:** kontras teks lolos WCAG AA, target sentuh 48dp konsisten, tetapi chart Canvas
   sebelumnya **tanpa** deskripsi TalkBack dan kartu aset tanpa `onClickLabel`. Sudah diperbaiki,
   termasuk **palet ramah buta warna** (`ThemeMode.COLOR_BLIND_LIGHT/DARK`, biru–oranye) yang
   sebelumnya belum ada meski dijanjikan DESIGN.md §4.5 dan SKILL §6.
6. **Kepatuhan bagian 10 SKILL.md sebagian besar terpenuhi** (uang virtual, tanpa judi/gacha,
   tanpa notifikasi FOMO, aset fiktif, disclaimer di semua layar relevan). Blocker rilis yang
   tersisa bersifat dokumentasi: IARC/Data Safety/Kebijakan Privasi belum ada, dan `metadata.json`
   masih mengklaim kapabilitas Gemini padahal aplikasi 100% offline.

---

## 2. Cara Menjalankan Ulang

```bash
# Simulasi headless (task Gradle terpisah dari `test`)
./gradlew :simulation:headlessSimulation
./gradlew :simulation:headlessSimulation -PsimRuns=1000 -PsimTicks=1000 -PsimScenario=ALL

# Kompilasi modul benchmark + APK benchmark (tanpa perangkat)
./gradlew :benchmark:compileBenchmarkSources
./gradlew :benchmark:assembleBenchmark

# Macrobenchmark (butuh perangkat/emulator; tidak dijalankan di audit ini)
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Keluaran simulasi: `simulation/build/reports/headless-simulation/` (Markdown + CSV).
Salinan hasil audit ini disimpan di `simulation/reports/headless-simulation-report.md`.

---

## 3. Temuan Simulasi Headless

### 3.1 Setup

| Item | Nilai |
|---|---|
| Katalog | `data/src/main/assets/market_catalog_v1.json` (10 aset, matriks korelasi produksi) |
| Event | `data/src/main/assets/market_events_v1.json` (12 event + matriks transisi) |
| Run | 1.000 per skenario (5 skenario = 5.000 run; 15.000 hasil strategi) |
| Horizon | 1.000 tick (~38 hari bursa) setelah 120 candle warm-up |
| Biaya | komisi 15 bps (`GameBalance.DEFAULT_TRADING_FEE_BPS`) + bid/ask spread per tier likuiditas |
| Strategi | Buy & Hold equal-weight · Trend Following SMA20/60 + SL 6% · Mean Reversion 20t (2σ) + SL 6% |
| Seed | `GameBalance.DEFAULT_SIMULATION_SEED` + stride 7919 per run (deterministik) |

### 3.2 Distribusi hasil (setelah kalibrasi)

| Skenario | Strategi | Median | P5 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Bull Run | Buy & Hold | **+3,12%** | −12,77% | +26,13% | 60,6% | 10,81% | 10 | 0,10% |
| Bull Run | Trend Following | −2,22% | −18,87% | +22,90% | 43,1% | 13,17% | 115 | 2,49% |
| Bull Run | Mean Reversion | −2,26% | −12,76% | +8,52% | 35,3% | 7,66% | 113 | 2,44% |
| Bear Crash | Mean Reversion | **−4,16%** | −21,56% | +18,11% | 37,6% | 14,03% | 119 | 2,54% |
| Bear Crash | Buy & Hold | −8,52% | −31,75% | +57,20% | 35,2% | 28,67% | 10 | 0,10% |
| Bear Crash | Trend Following | −12,74% | −40,85% | +56,92% | 33,2% | 31,58% | 153 | 3,11% |
| Sideways Drift | Buy & Hold | **+0,97%** | −12,23% | +21,72% | 54,4% | 9,79% | 10 | 0,10% |
| Sideways Drift | Mean Reversion | −2,94% | −12,20% | +7,20% | 30,6% | 7,24% | 113 | 2,44% |
| Sideways Drift | Trend Following | −3,58% | −17,39% | +18,90% | 38,6% | 12,41% | 111 | 2,38% |
| Crisis Shock | Mean Reversion | **−5,02%** | −24,70% | +20,10% | 36,9% | 15,47% | 116 | 2,47% |
| Crisis Shock | Buy & Hold | −13,55% | −37,08% | +68,46% | 32,0% | 34,95% | 10 | 0,10% |
| Crisis Shock | Trend Following | −14,93% | −48,63% | +65,31% | 32,1% | 37,16% | 167 | 3,31% |
| Mixed Regime | Buy & Hold | **−2,35%** | −23,73% | +42,61% | 44,7% | 19,92% | 10 | 0,10% |
| Mixed Regime | Mean Reversion | −3,57% | −18,78% | +15,84% | 36,4% | 11,60% | 118 | 2,53% |
| Mixed Regime | Trend Following | −7,24% | −30,03% | +41,21% | 35,9% | 22,40% | 130 | 2,72% |

Tabel lengkap (Markdown + latensi + deteksi dominasi) ada di
`simulation/reports/headless-simulation-report.md`.

### 3.3 Deteksi strategi dominan

| Skenario | Pemenang median | Selisih vs Buy & Hold |
|---|---|---:|
| Bull Run | Buy & Hold | 0,00% |
| Bear Crash | Mean Reversion | +4,37% |
| Sideways Drift | Buy & Hold | 0,00% |
| Crisis Shock | Mean Reversion | +8,53% |
| Mixed Regime (produksi) | Buy & Hold | 0,00% |

**Verdict: `BUY_AND_HOLD_DOMINAN_PARSIAL` (3/5).** Buy-and-hold menang di pasar naik/datar,
mean reversion menang di pasar turun — struktur yang sehat, tetapi strategi aktif **belum pernah**
mengalahkan pasif pada konfigurasi produksi (Mixed).

### 3.4 Rasio buy-and-hold vs aktif

| Skenario | Median B&H | Median aktif terbaik | Run di mana aktif > B&H |
|---|---:|---:|---:|
| Bull Run | +3,12% | −2,22% | 47,7% |
| Bear Crash | −8,52% | −4,16% | 71,2% |
| Sideways Drift | +0,97% | −2,94% | 49,3% |
| Crisis Shock | −13,55% | −5,02% | 75,1% |
| Mixed Regime | −2,35% | −3,57% | 64,0% |

Akar masalahnya **biaya turnover**, bukan sinyal: strategi aktif membayar komisi median
**2,4–3,3%** dari modal (111–167 trade) versus **0,10%** untuk buy-and-hold (10 trade).
Setelah fee 15 → 10 bps, fee median turun ~31% (3,5–4,9% → 2,4–3,3%), median strategi aktif
naik 1,2–1,4 pp, dan proporsi run di mana strategi aktif mengalahkan B&H naik 5–7 pp
(41,3% → 47,7% di Bull, 43,1% → 49,3% di Sideways, 69,1% → 71,2% di Bear, 73,3% → 75,1% di
Crisis, 60,5% → 64,0% di Mixed). Dominasi median belum berubah: edge sinyal 20/60-tick masih
tidak menutup spread + fee.

### 3.5 Inflasi uang

| Skenario | Indeks inflasi (median ekuitas akhir ÷ modal awal) | Status |
|---|---:|---|
| Bull Run | 1,031× | Wajar |
| Sideways Drift | 1,010× | Wajar |
| Bear Crash | 0,915× | Deflasi (sesuai desain rezim bear) |
| Crisis Shock | 0,864× | Deflasi (sesuai desain rezim crisis) |
| **Mixed Regime (produksi)** | **0,977×** | **Deflasi ringan — sisa pekerjaan** |

Tidak ada inflasi uang (± 0,86–1,03×), jadi tidak ada "uang gratis" dari hold pasif.
Sisa masalah: pada mode produksi (Markov aktif) median pembeli-pasif masih kehilangan ~2,35% per
38 hari bursa (−2,56% → −2,35%). Menaikkan drift Sideways ke 0,14 hanya memperbaiki Mixed sebesar
0,003×, jadi sisa deflasi **tidak** dapat ditutup dari drift rezim: sumbernya komposisi & waktu
tinggal di rezim bear/crisis. Opsi yang tersisa: menurunkan σ `GRDC`/`KPSW` di katalog atau
menggeser matriks transisi Markov — keduanya perubahan data, bukan konstanta `GameBalance`.

---

## 4. Rekomendasi & Perubahan `GameBalance.kt`

### 4.1 Sudah diterapkan (divalidasi dengan run ulang)

| Konstanta | Lama | Baru | Dasar data |
|---|---:|---:|---|
| `REGIME_BULL_DRIFT_DELTA` | 0,10 | **0,30** | median B&H Bull +0,91% → target positif; hasil +3,07% |
| `REGIME_SIDEWAYS_DRIFT_DELTA` | −0,02 | **0,10** | Sideways −0,77% → target datar; hasil +0,49% |
| `REGIME_BEAR_DRIFT_DELTA` | −0,24 | **−0,06** | Bear −10,14% → target −8%; hasil −8,57% (sisa −0,6 pp) |
| `REGIME_CRISIS_DRIFT_DELTA` | −0,52 | **−0,20** | Crisis −16,21% → target −12%; hasil −13,55% (sisa −1,6 pp) |
| `DEFAULT_TRADING_FEE_BPS` | 15 | **10** | fee median aktif 3,5–4,9% modal → 2,4–3,3%; median aktif +1,2–1,4 pp; run aktif>B&H +5–7 pp |
| `REGIME_SIDEWAYS_DRIFT_DELTA` | 0,10 | **0,14** | Sideways 0,49% → 0,97%, inflasi 1,005× → 1,010×; efek ke Mixed hanya 0,974× → 0,977× |

Urutan risiko tetap benar (Bull > Sideways > Bear > Crisis) dan tidak ada rezim yang memberi
return gratis. Volatilitas rezim (`*_VOL_MULT`) dan drift safe-haven **tidak** diubah karena
belum ada bukti data yang menuntutnya.

### 4.2 Direkomendasikan, belum diterapkan (perlu keputusan produk)

| Konstanta | Lama | Usulan | Alasan dari data |
|---|---:|---:|---|
| `ANNUAL_BORROW_FEE_BPS` | 1.200 | **900** | Belum terukur di harness (long-only). Perlu harness leverage sebelum diubah — **jangan** diubah tanpa data. |

Rekomendasi tingkat katalog (bukan `GameBalance.kt`): `GRDC` (σ 0,78) dan `KPSW` (σ 0,95)
menyumbang variance drag terbesar; menurunkan σ ke 0,62/0,75 akan menaikkan median seluruh
portofolio tanpa menambah drift. Ini perubahan `market_catalog_v1.json` + versi skema.

---

## 5. Performa (Macrobenchmark & Tick Engine)

### 5.1 Yang dibangun

- Modul `benchmark/` (`com.android.test`, target `:app`) dengan 3 benchmark:
  - `StartupBenchmark.kt` — cold & warm start (`StartupTimingMetric` + `FrameTimingMetric`).
  - `ChartScrollBenchmark.kt` — pan/zoom chart 120 candle (`FrameTimingMetric`).
  - `TickEngineBenchmark.kt` — 40× step tick di layar detail.
- Varian `benchmark` di `app/build.gradle.kts` (non-debuggable, signature debug) +
  `app/src/benchmark/AndroidManifest.xml` berisi `<profileable android:shell="true"/>`.
- `Modifier.semantics { testTagsAsResourceId = true }` di `TradingNavGraph.kt` agar
  `By.res("candlestick_chart")` / `By.res("sim_step_once")` dapat ditemukan UiAutomator.

### 5.2 Hasil terukur (proxy JVM, bukan perangkat)

| Metrik `MarketEngine.stepAll` | Nilai |
|---|---:|
| Sampel | 6.000 |
| P50 | 133,2 µs |
| P95 | 151,7 µs |
| P99 | 192,6 µs |
| Max | 8.168,2 µs (outlier JIT/cold pertama) |
| Mean | 128,5 µs |

Anggaran 16 ms/tick → P99 memakai **±1,2%** anggaran di JVM x86_64. Ini **batas bawah**:
perangkat ARM low-end bisa 5–15× lebih lambat, masih di bawah 16 ms secara teoretis, tetapi
**belum diverifikasi** karena modul benchmark tidak dapat dijalankan tanpa perangkat/emulator
di lingkungan audit ini.

### 5.3 Catatan performa UI

- Chart melakukan `remember(...)` + downsampling (`DEFAULT_CHART_MAX_RENDER_POINTS = 120`) dan
  `Path` yang di-reuse — baik untuk 60 fps.
- `MarketListScreen` sudah memakai `LazyColumn` + `key`.
- Risiko: `MarketSimulationRepositoryImpl` memancarkan state baru tiap tick ke semua kolektor;
  `SimulationSpeed.SPEED_5X` = interval 200 ms (5 tick/detik) dan chart di layar detail ikut
  recompose setiap tick. Rekomendasi lanjutan: turunkan frekuensi emisi chart (mis. 1–2 Hz)
  atau `derivedStateOf` per-field bila profil perangkat menunjukkan jank (belum terbukti di data).

---

## 6. Audit Aksesibilitas

| Aspek | Status | Bukti / tindakan |
|---|---|---|
| Target sentuh ≥ 48dp | **Lulus** | Semua kontrol interaktif memakai `minimumInteractiveComponentSize()` atau komponen Material (`IconButton`, `FilterChip`, `NavigationBarItem`, `Button`, `Switch`). |
| Kontras WCAG AA (teks ≥ 4,5:1) | **Lulus** | Dihitung: bullish `#007A53`/`#EEF3F8` 4,81:1; bearish `#D12442`/`#EEF3F8` 4,65:1; warning `#9E5A00`/`#F9FBFD` 5,17:1; dark onSurfaceVariant `#94A3B8`/`#152133` 6,31:1; AMOLED 8,19:1. Satu-satunya pasangan < 4,5 adalah `#9E5A00` pada `#FFDDB7` (4,16:1), dipakai hanya untuk ikon dekoratif (ambang 3:1) — aman. |
| `contentDescription` semua ikon | **Lulus setelah perbaikan** | Chart & sparkline kini punya semantics; ikon dekoratif `null`; ikon aksi berlabel string terlokalisasi. |
| Arah naik/turun bukan hanya warna | **Lulus** | Ikon panah + tanda `+`/`−` + teks persentase di kartu aset, header detail, dan ringkasan portofolio. |
| Palet ramah buta warna | **Lulus setelah perbaikan** | `ThemeMode.COLOR_BLIND_LIGHT/DARK` memakai palet biru–oranye (#0072B2/#B34700 terang, #56B4E9/#F0A030 gelap); teks pada surface 4,64–4,93:1 (terang) dan 7,02–7,54:1 (gelap). Pasangan container (diukur, WCAG AA teks normal 4,5:1) — `onPrimaryContainer/primaryContainer`: 9,66:1 (terang) & 9,79:1 (gelap); `onErrorContainer/errorContainer`: 8,69:1 (terang) & 10,03:1 (gelap); `onPrimary/primary`: 5,19:1 & 6,57:1; `onError/error`: 5,50:1 & 7,21:1. **Dua pasangan versi pertama gagal dan sudah diperbaiki**: `#0072B2` pada `#D4E9F7` hanya 4,15:1 dan `#B34700` pada `#FCE3CD` hanya 4,45:1, diganti `onPrimaryContainer` `#00395C` dan `onErrorContainer` `#6B2A00`. |
| Font scale / layar kecil | **Risiko** | Label sumbu chart `10.sp` dan tinggi chart tetap (`290.dp` candle, `210.dp` equity) berpotensi terpotong pada font scale 200%. Belum diuji. |
| RTL | **Lulus sebagian** | Ikon back/chart memakai `AutoMirrored`; namun `PendingOrderCard` menyusun teks "Stop … -> Limit …" hardcoded LTR. |

### 6.1 Perbaikan aksesibilitas yang diterapkan

| Berkas | Perubahan |
|---|---|
| `core/designsystem/.../CandlestickChart.kt` | `semantics { contentDescription = ... }` berisi penutupan terakhir, perubahan %, high, low + petunjuk interaksi; string `chart_a11y_summary` (id/en). |
| `core/designsystem/.../EquityCurveChart.kt` | `semantics` ringkasan ekuitas terkini/puncak/terendah; string `equity_chart_a11y_summary` (id/en). |
| `core/designsystem/.../SparklineChart.kt` | `clearAndSetSemantics {}` — grafik dekoratif, arah tren sudah dibacakan teks harga. |
| `core/designsystem/.../SimulationComponents.kt` | Ikon header `contentDescription = null` (sebelumnya menduplikasi judul → TalkBack membaca dua kali). |
| `feature/market/.../MarketListScreen.kt` | Kartu aset `clickable(onClickLabel = "Buka detail NSKP")`; string `market_open_asset` (id/en). |
| `feature/trade/.../TradeScreen.kt` | String hardcoded `"OK"` pada banner feedback → `trade_feedback_ack` (id/en). |

### 6.2 Rekomendasi belum diterapkan

1. **Palet buta warna — sudah diterapkan**: `ThemeMode.COLOR_BLIND_LIGHT/COLOR_BLIND_DARK`
   (biru–oranye Okabe–Ito), token `bullish/bearish` dan `colorScheme.primary/error` diturunkan dari
   palet itu. Pemetaan DataStore berbasis `name` sehingga nilai baru langsung tersimpan tanpa migrasi.
   Berkas: `domain/model/ThemeMode.kt`, `core/designsystem/theme/Color.kt`, `Theme.kt`,
   `feature/profile/ProfileScreen.kt`, string profil (id/en).
2. Ganti `10.sp` label chart dengan `MaterialTheme.typography.labelSmall` agar mengikuti font scale.

---

## 7. Audit Kepatuhan `SKILL.md` Bagian 10

| Butir | Status | Temuan per path |
|---|---|---|
| Uang virtual saja, tanpa top-up/penarikan | **Lulus** | Tidak ada dependensi billing/IAP di `app/build.gradle.kts` maupun `gradle/libs.versions.toml`; tidak ada `INTERNET` permission di `app/src/main/AndroidManifest.xml`. |
| Tanpa loot box/gacha/taruhan arah | **Lulus** | Pencarian `gacha/loot/taruhan/bet/countdown` tidak menemukan mekanik terkait; satu-satunya kecocokan adalah istilah "Risk/Reward" & tutorial "tanpa FOMO". |
| Disclaimer "simulasi — bukan saran investasi" | **Lulus** | `core/designsystem/.../SimulationComponents.kt` (`SimulationDisclaimerBanner`) dipasang di `MarketListScreen.kt`, `MarketScreen.kt`, `NewsScreen.kt`, `TradeScreen.kt`, `PortfolioScreen.kt`, `LearnScreen.kt`, `ProfileScreen.kt`. Badge "SIMULASI VIRTUAL" di `TradingNavGraph.kt`. |
| Leverage tidak dinormalisasi; batas per level | **Lulus** | `CareerLevel.maxLeverage` (1/1/3/5/5) + chip leverage di `TradeRiskComponents.kt`; peringatan likuidasi `trade_warning_liquidation`, margin call banner, estimasi harga likuidasi di `TradeTicketComponents.kt`. |
| Tanpa notifikasi menekan/FOMO | **Lulus** | Tidak ada `WorkManager`/`NotificationManager`/`POST_NOTIFICATIONS` di seluruh repo; tidak ada timer hitung mundur. |
| Aset fiktif | **Lulus** | 10 simbol di `data/src/main/assets/market_catalog_v1.json` seluruhnya fiktif (NSKP, BPAT, PSRY, SDKT, GRDC, KPSW, XVMG, MBSO, VUD/VRP, IKN30); tidak ada ticker/merek nyata. |
| IAP hanya kosmetik + konfirmasi | **N/A** | Tidak ada IAP. |
| Kebijakan Play (gambling/simulated gambling), IARC, Data Safety | **Belum** | Tidak ada berkas kebijakan/IARC/Data Safety di repo. Ini **blocker rilis** (dokumentasi, bukan kode). Usia target 13+ di `DESIGN.md` harus diverifikasi ulang saat rilis. |
| UU PDP: data minimal + Kebijakan Privasi | **Sebagian** | Aplikasi offline, tanpa permission jaringan, tidak mengumpulkan data — baik. Namun **belum ada** Kebijakan Privasi. Klaim kapabilitas server sudah dibersihkan: `metadata.json` kini `majorCapabilities: []` dan `.env.example` tidak lagi menyebut `GEMINI_API_KEY`, sehingga Data Safety konsisten dengan aplikasi yang offline. |
| Seed tidak ditampilkan di mode karier | **Lulus** | `DEFAULT_SIMULATION_SEED` hanya dipakai di `MarketSimulationRepositoryImpl.kt`; UI hanya menampilkan tick counter. |

### 7.1 Temuan kepatuhan tambahan (di luar bagian 10)

| Path | Temuan |
|---|---|
| `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` | Masih stub bawaan (hanya cek package). SKILL §8 mensyaratkan Compose UI test alur buka aset → order → posisi → tutup; belum ada. |
| `app/src/test/java/com/example/ExampleRobolectricTest.kt` | Stub cek nama aplikasi. |
| `feature/*/src/test/...` | Test ViewModel ada (market, trade, portfolio, learn, profile) — baik. |
| Struktur modul vs SKILL §3.2 | `news` berada di `feature/market`, tidak ada `feature/home`, `feature/career`, `feature/settings`, `domain/usecase`. Penyimpangan struktur yang perlu dicatat di DESIGN.md. |
| `feature/trade/.../TradeTicketComponents.kt` | Dulu ada teks Inggris hardcoded (`"Limit: "`, `"Stop: "`, `"-> Limit"`, `"unit"`). **Sudah diperbaiki**: `trade_confirm_summary`, `trade_pending_title`, `trade_pending_price_limit/stop/stop_limit` (id/en). |
| `feature/trade/.../TradeScreen.kt` | Dulu nama enum `OrderSide`/`OrderType` tampil mentah di dialog konfirmasi & banner feedback. **Sudah diperbaiki** lewat helper `orderSideLabel()`/`orderTypeLabel()` yang memakai string `trade_side_*` dan `trade_type_*`. |
| `core/common/.../GameBalance.kt` | Semua konstanta balancing terpusat — lulus aturan "tidak ada magic number" untuk balancing. |

---

## 8. Daftar Perubahan per Berkas

### 8.1 Mesin & balancing

**📄 `domain/src/main/kotlin/com/tradinginaja/domain/engine/MarketEngine.kt`**
- Alasan: bug — modifier rezim tidak pernah diterapkan saat transisi Markov dimatikan, sehingga
  skenario satu-rezim tampak identik (Bear/Sideways sama persis di run baseline).
- Perubahan: `combineAssetParameters(..., applyRegimeModifiers = true)` (sebelumnya mengikuti
  `state.enableRegimeSwitching`) + komentar alasannya.

**📄 `core/common/src/main/kotlin/com/tradinginaja/core/common/GameBalance.kt`**
- Alasan: kalibrasi drift per rezim dari data simulasi agar modal tidak menyusut di semua skenario.
- Perubahan: `REGIME_BULL_DRIFT_DELTA` 0,10→0,30; `REGIME_SIDEWAYS_DRIFT_DELTA` −0,02→0,10;
  `REGIME_BEAR_DRIFT_DELTA` −0,24→−0,06; `REGIME_CRISIS_DRIFT_DELTA` −0,52→−0,20;
  KDoc memuat angka sebelum/sesudah kalibrasi.

### 8.2 Skrip simulasi headless (baru)

- **📄 `simulation/build.gradle.kts`** — modul JVM + task `headlessSimulation` (parameter
  `-PsimRuns`, `-PsimTicks`, `-PsimScenario`, `-PsimSeed`), menulis ke `build/reports/headless-simulation`.
- **📄 `simulation/src/main/kotlin/com/tradinginaja/simulation/HeadlessSimulation.kt`** — entry point + parsing argumen.
- **📄 `.../catalog/CatalogLoader.kt`, `EventCatalogLoader.kt`, `JsonScalarExtensions.kt`, `JsonStructureExtensions.kt`** — membaca JSON produksi tanpa plugin serialization.
- **📄 `.../engine/ScenarioRunner.kt`** — 5 skenario, warm-up, pengukuran latensi tick.
- **📄 `.../engine/Strategies.kt`** — Buy & Hold, Trend Following SMA20/60, Mean Reversion 2σ (cooldown 24 tick).
- **📄 `.../engine/PaperPortfolio.kt`, `PriceHistory.kt`** — akuntansi Long minor units, komisi 15 bps, bid/ask per tier.
- **📄 `.../report/Statistics.kt`, `ReportWriter.kt`, `RawReportWriter.kt`** — persentil, dominasi, inflasi, CSV, konsol.
- **📄 `settings.gradle.kts`** — `include(":simulation")`, `include(":benchmark")`.
- **📄 `simulation/reports/headless-simulation-report.md`** — arsip hasil run final.

### 8.3 Macrobenchmark (baru)

- **📄 `benchmark/build.gradle.kts`** — `com.android.test`, `targetProjectPath = ":app"`, build type `benchmark`.
- **📄 `benchmark/src/main/java/com/tradinginaja/benchmark/StartupBenchmark.kt`** — cold & warm start.
- **📄 `benchmark/src/main/java/com/tradinginaja/benchmark/ChartScrollBenchmark.kt`** — pan/zoom chart.
- **📄 `benchmark/src/main/java/com/tradinginaja/benchmark/TickEngineBenchmark.kt`** — 40 step tick + frame timing.
- **📄 `app/build.gradle.kts`** — build type `benchmark` + `androidx.profileinstaller`.
- **📄 `app/src/benchmark/AndroidManifest.xml`** — `<profileable android:shell="true"/>` khusus varian benchmark.
- **📄 `gradle/libs.versions.toml`** — `android-test`, `kotlin-android`, `benchmark-macro-junit4 1.3.4`, `profileinstaller 1.4.1`, `uiautomator 2.3.0`.
- **📄 `app/src/main/java/com/example/navigation/TradingNavGraph.kt`** — `testTagsAsResourceId` agar benchmark dapat menemukan elemen.

### 8.4 Aksesibilitas

- **📄 `core/designsystem/.../CandlestickChart.kt`**, **`EquityCurveChart.kt`**, **`SparklineChart.kt`**,
  **`SimulationComponents.kt`** — semantics chart, sparkline dekoratif, ikon header tidak duplikat.
- **📄 `core/designsystem/src/main/res/values{,-in}/strings.xml`** — `chart_a11y_summary`, `equity_chart_a11y_summary`.
- **📄 `feature/market/.../MarketListScreen.kt`** + **`feature/market/src/main/res/values{,-in}/strings.xml`** — `onClickLabel` kartu aset (`market_open_asset`).
- **📄 `feature/trade/.../TradeScreen.kt`** + **`feature/trade/src/main/res/values{,-in}/strings.xml`** — tombol feedback memakai `trade_feedback_ack`.

### 8.5 Fase 8.5 — penutupan balancing, kepatuhan, a11y

- **📄 `core/common/.../GameBalance.kt`** — `DEFAULT_TRADING_FEE_BPS` 15→**10**,
  `REGIME_SIDEWAYS_DRIFT_DELTA` 0,10→**0,14**, KDoc memuat angka sebelum/sesudah dan batas
  interpretasinya (efek ke Mixed hanya 0,003×).
- **📄 `domain/.../model/ThemeMode.kt`**, **`core/designsystem/theme/Color.kt`**, **`Theme.kt`** —
  mode `COLOR_BLIND_LIGHT/DARK` + palet biru–oranye Okabe–Ito, extended colors, dan color scheme.
- **📄 `feature/profile/.../ProfileScreen.kt`** + strings (id/en) — dua opsi tema baru.
- **📄 `feature/trade/.../TradeTicketComponents.kt`**, **`TradeScreen.kt`** — helper
  `orderSideLabel()`/`orderTypeLabel()`; semua teks tiket/pending order/feedback jadi resource.
- **📄 `feature/trade`, `feature/learn` strings** — komisi 15→10 bps; teks order baru (id/en).
- **📄 `metadata.json`**, **`.env.example`** — klaim Gemini dihapus; tidak ada secret yang dideklarasikan.
- **📄 `DESIGN.md`** — basis biaya diperbarui ke 10 bps / 0,10%.
- **📄 `simulation/reports/headless-simulation-report.md`** — arsip run v5.

---

## 9. Verifikasi yang Dijalankan

| Pemeriksaan | Hasil |
|---|---|
| Kompilasi seluruh sumber `core:common` + `domain` + `simulation` (kotlinc 2.1.20, JDK 17) | **Lulus** |
| Unit test `domain` + `core:common` (12 kelas, 51 test, JUnit 4.13.2) | **Lulus** (51/51) |
| ktlint 1.5.0 pada berkas baru/diubah | **Lulus** (0 temuan) |
| Simulasi v5 (fee 10 bps + Sideways 0,14) lewat Gradle | **Lulus**, `BUILD SUCCESSFUL in 3m 37s`, 15.000 run, 200,1 s |
| `:domain:test` + `:core:common:test` lewat Gradle | **Lulus** |
| `ktlintCheck` Gradle pada 8 modul (v5) | **Lulus** |
| detekt 2.0.0-alpha.6 (`--build-upon-default-config` + `config/detekt/detekt.yml`) pada `simulation`, `benchmark`, `MarketEngine.kt`, `GameBalance.kt` | **Lulus** (0 temuan) |
| Simulasi headless final (5 × 1.000 run × 1.000 tick) | **Lulus**, 210,6 s |
| Paritas string `values` vs `values-in` (7 modul) | **Lulus** (0 key hilang) |
| Rasio kontras WCAG | **Dihitung manual** (lihat §6) |
| `:simulation:headlessSimulation` lewat **Gradle 9.3.1 + AGP 9.1.1 + JDK 17 + SDK 36.1** | **Lulus**, `BUILD SUCCESSFUL in 3m 34s`, 15.000 run, 201,3 s; median per strategi **identik** dengan run audit |
| `:app:compileBenchmarkKotlin` (app + 11 modul, KSP/Hilt/Compose) | **Lulus**, 111 task |
| `:benchmark:compileBenchmarkSources` | **Lulus** |
| `:benchmark:assembleBenchmark` | **Lulus**, `benchmark-benchmark.apk` 36,7 MB |
| `ktlintCheck` Gradle pada `:app`, `:core:designsystem`, `:feature:market`, `:feature:trade`, `:domain`, `:core:common`, `:simulation`, `:benchmark` | **Lulus** (42 task) |
| Macrobenchmark di perangkat | **Tidak dijalankan** — tidak ada perangkat/emulator di lingkungan ini |
| `assembleDebug` / `connectedCheck` | **Tidak dijalankan** — butuh keystore debug/rilis AI Studio dan perangkat |

### 9.1 Wiring yang diperbaiki saat build Gradle nyata

Build pertama dengan SDK asli menyingkap lima cacat build/kode plus satu higiene repo yang tidak terlihat pada kompilasi kotlinc:

| Berkas | Masalah | Perbaikan |
|---|---|---|
| `build.gradle.kts` (root) | `com.android.test` dipakai `:benchmark` dengan versi eksplisit padahal AGP sudah di classpath → `Error resolving plugin ... already on the classpath with an unknown version` | Tambah `alias(libs.plugins.android.test) apply false` |
| `benchmark/build.gradle.kts` | `org.jetbrains.kotlin.android` ditolak AGP 9 (`no longer required ... since AGP 9.0`) | Hapus plugin tersebut (Kotlin bawaan AGP) |
| `simulation/build.gradle.kts` | `doFirst` + `project.findProperty` di `CommandLineArgumentProvider` merusak configuration cache (aktif di `gradle.properties`) → task gagal saat graph disimpan | Argumen dari `providers.gradleProperty`, `outputs.dir(...)`, `outputs.upToDateWhen { false }`, tanpa `doFirst` |
| `benchmark/.../ChartScrollBenchmark.kt` | `openFirstAssetDetail()` dipanggil tanpa receiver → `UNRESOLVED_REFERENCE_WRONG_RECEIVER` | Panggil `device.openFirstAssetDetail()` + import `UiDevice` |
| `app/.../TradingNavGraph.kt` | `testTagsAsResourceId` adalah API eksperimental → `OPT_IN_USAGE_ERROR` | `@OptIn(..., ExperimentalComposeUiApi::class)` |
| `simulation/.gitignore`, `benchmark/.gitignore` | Output `build/` modul baru tidak ter-ignore (root `.gitignore` hanya `/build`) | Tambah `/build` (mengikuti `app/.gitignore`) |

---

## 10. Risiko & Catatan

1. **Angka simulasi bergantung pada model biaya harness.** Harness memakai komisi + spread,
   tanpa slippage ukuran order dan tanpa leverage/funding. Hasil untuk strategi aktif adalah
   batas bawah biaya; leverage bisa memperburuk drawdown.
2. **Kalibrasi drift adalah perubahan global.** Semua mode (Karier/Sandbox/Tantangan) ikut berubah.
   Dampak ke progres misi (mis. `LV1_MAX_DRAWDOWN_PCT = 15`) perlu smoke test manual.
3. **Mode produksi (Mixed) masih deflasi ringan** (0,977×, dari 0,974×). Fee 10 bps dan Sideways
   0,14 sudah diterapkan dan hanya menutup ~0,003×; sumber sisa deflasi adalah komposisi rezim,
   jadi menutupnya butuh perubahan data (σ `GRDC`/`KPSW` atau matriks transisi), bukan tuning
   konstanta yang lebih agresif.
4. **Macrobenchmark belum terbukti berjalan.** Nama `testTag`/resource-id, waktu tunggu, dan
   `CompilationMode.Partial` perlu diverifikasi pada perangkat pertama; jangan mengutip angka
   16 ms sebagai lulus sebelum itu.
5. **Masih ada string hardcoded** di tiket order yang perlu dipindahkan ke `strings.xml` sebelum
   rilis (id/en) dan sebelum uji RTL final.
