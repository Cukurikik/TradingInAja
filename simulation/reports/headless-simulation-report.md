# Laporan Simulasi Headless — Fase 8

Dibuat: 2026-11-22T19:31:41.914929261

## Parameter Run

| Parameter | Nilai |
|---|---|
| Katalog | `data/src/main/assets/market_catalog_v1.json` |
| Event | `data/src/main/assets/market_events_v1.json` |
| Run per skenario | 1000 |
| Tick per run | 1000 |
| Seed dasar | 20261121 |
| Filter skenario | ALL |
| Total run | 15000 |
| Total tick simulasi | 15000 × 1000 |

Strategi yang diuji: `buy_and_hold`, `momentum_trend`, `mean_reversion`

## Distribusi Hasil per Skenario

### Bull Run (`BULL_RUN`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Buy & Hold (equal-weight) | 3.07% | 4.39% | -12.82% | -4.03% | 10.92% | 26.08% | 60.60% | 10.82% | 10 | 0.15% |
| Mean Reversion 20t (2σ) + SL 6% | -3.42% | -3.54% | -13.91% | -7.92% | 0.80% | 7.36% | 28.70% | 8.27% | 113 | 3.63% |
| Trend Following SMA20/60 + SL 6% | -3.42% | -1.88% | -20.11% | -11.17% | 5.64% | 21.50% | 39.00% | 13.79% | 115 | 3.71% |

### Bear Crash (`BEAR_CRASH`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Mean Reversion 20t (2σ) + SL 6% | -5.46% | -4.42% | -22.68% | -12.79% | 2.43% | 16.45% | 33.90% | 14.61% | 119 | 3.78% |
| Buy & Hold (equal-weight) | -8.57% | -0.29% | -31.80% | -20.29% | 8.99% | 57.15% | 35.20% | 28.68% | 10 | 0.15% |
| Trend Following SMA20/60 + SL 6% | -14.03% | -6.23% | -42.00% | -27.41% | 8.25% | 54.57% | 31.60% | 32.35% | 153 | 4.63% |

### Sideways Drift (`SIDEWAYS_DRIFT`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Buy & Hold (equal-weight) | 0.49% | 2.02% | -12.62% | -5.02% | 7.70% | 21.13% | 52.00% | 9.90% | 10 | 0.15% |
| Mean Reversion 20t (2σ) + SL 6% | -4.21% | -4.18% | -13.41% | -8.16% | -0.36% | 5.81% | 23.40% | 7.92% | 114 | 3.66% |
| Trend Following SMA20/60 + SL 6% | -5.12% | -3.38% | -18.97% | -11.35% | 3.44% | 17.19% | 34.00% | 13.09% | 111 | 3.54% |

### Crisis Shock (`CRISIS_SHOCK`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Mean Reversion 20t (2σ) + SL 6% | -6.18% | -4.75% | -25.71% | -14.13% | 3.34% | 18.27% | 32.20% | 16.07% | 116 | 3.69% |
| Buy & Hold (equal-weight) | -13.60% | -1.69% | -37.13% | -26.05% | 8.56% | 68.41% | 31.90% | 34.96% | 10 | 0.15% |
| Trend Following SMA20/60 + SL 6% | -16.49% | -7.08% | -49.67% | -32.33% | 7.53% | 62.98% | 30.60% | 37.95% | 167 | 4.92% |

### Mixed Regime (Markov aktif) (`MIXED_REGIME`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Buy & Hold (equal-weight) | -2.56% | 1.88% | -23.87% | -12.99% | 10.21% | 42.33% | 44.20% | 19.97% | 10 | 0.15% |
| Mean Reversion 20t (2σ) + SL 6% | -4.89% | -3.99% | -19.86% | -11.16% | 2.18% | 14.21% | 31.90% | 12.24% | 119 | 3.78% |
| Trend Following SMA20/60 + SL 6% | -8.66% | -3.89% | -31.64% | -19.26% | 6.33% | 40.37% | 33.70% | 23.07% | 130 | 4.05% |

## Deteksi Strategi Dominan

| Skenario | Strategi median tertinggi | Median | Selisih vs Buy & Hold |
|---|---|---:|---:|
| Bull Run | Buy & Hold (equal-weight) | 3.07% | 0.00% |
| Bear Crash | Mean Reversion 20t (2σ) + SL 6% | -5.46% | 3.11% |
| Sideways Drift | Buy & Hold (equal-weight) | 0.49% | 0.00% |
| Crisis Shock | Mean Reversion 20t (2σ) + SL 6% | -6.18% | 7.42% |
| Mixed Regime (Markov aktif) | Buy & Hold (equal-weight) | -2.56% | 0.00% |

**TEMUAN: buy-and-hold dominan di 3/5 skenario.** Perlu penyesuaian balancing agar strategi aktif kompetitif.

## Inflasi Uang Virtual

Inflasi uang = median ekuitas akhir buy-and-hold ÷ modal awal.

| Skenario | Indeks inflasi | Median return B&H | Status |
|---|---:|---:|---|
| Bull Run | 1.031× | 3.07% | Wajar |
| Bear Crash | 0.914× | -8.57% | Deflasi — median pemain kehilangan modal |
| Sideways Drift | 1.005× | 0.49% | Wajar |
| Crisis Shock | 0.864× | -13.60% | Deflasi — median pemain kehilangan modal |
| Mixed Regime (Markov aktif) | 0.974× | -2.56% | Wajar |


## Rasio Buy-and-Hold vs Strategi Aktif

| Skenario | Median B&H | Median aktif terbaik | Rasio aktif/B&H | Run di mana aktif > B&H |
|---|---:|---:|---:|---:|
| Bull Run | 3.07% | -3.42% | n/a | 41.30% |
| Bear Crash | -8.57% | -5.46% | n/a | 69.10% |
| Sideways Drift | 0.49% | -4.21% | n/a | 43.10% |
| Crisis Shock | -13.60% | -6.18% | n/a | 73.30% |
| Mixed Regime (Markov aktif) | -2.56% | -4.89% | n/a | 60.50% |


## Latensi Tick Engine (proxy JVM desktop)

| Metrik | Nilai |
|---|---:|
| Sampel | 6000 |
| P50 | 133.2 µs |
| P95 | 151.7 µs |
| P99 | 192.6 µs |
| Max | 8168.2 µs |
| Mean | 128.5 µs |

P99 di bawah anggaran 16 ms/tick. Angka ini dari JVM desktop (x86_64, JIT hangat) sehingga hanya batas bawah; verifikasi perangkat low-end wajib lewat macrobenchmark.

