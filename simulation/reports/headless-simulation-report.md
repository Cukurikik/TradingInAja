# Laporan Simulasi Headless — Fase 8

Dibuat: 2026-11-22T20:46:55.84808274

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
| Buy & Hold (equal-weight) | 3.12% | 4.44% | -12.77% | -3.98% | 10.97% | 26.13% | 60.60% | 10.81% | 10 | 0.10% |
| Trend Following SMA20/60 + SL 6% | -2.22% | -0.64% | -18.87% | -10.01% | 7.00% | 22.90% | 43.10% | 13.17% | 115 | 2.49% |
| Mean Reversion 20t (2σ) + SL 6% | -2.26% | -2.34% | -12.76% | -6.84% | 2.07% | 8.52% | 35.30% | 7.66% | 113 | 2.44% |

### Bear Crash (`BEAR_CRASH`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Mean Reversion 20t (2σ) + SL 6% | -4.16% | -3.17% | -21.56% | -11.70% | 3.70% | 18.11% | 37.60% | 14.03% | 119 | 2.54% |
| Buy & Hold (equal-weight) | -8.52% | -0.24% | -31.75% | -20.24% | 9.03% | 57.20% | 35.20% | 28.67% | 10 | 0.10% |
| Trend Following SMA20/60 + SL 6% | -12.74% | -4.67% | -40.85% | -26.10% | 9.89% | 56.92% | 33.20% | 31.58% | 153 | 3.11% |

### Sideways Drift (`SIDEWAYS_DRIFT`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Buy & Hold (equal-weight) | 0.97% | 2.51% | -12.23% | -4.59% | 8.21% | 21.72% | 54.40% | 9.79% | 10 | 0.10% |
| Mean Reversion 20t (2σ) + SL 6% | -2.94% | -2.87% | -12.20% | -6.95% | 1.14% | 7.20% | 30.60% | 7.24% | 113 | 2.44% |
| Trend Following SMA20/60 + SL 6% | -3.58% | -1.91% | -17.39% | -9.87% | 4.84% | 18.90% | 38.60% | 12.41% | 111 | 2.38% |

### Crisis Shock (`CRISIS_SHOCK`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Mean Reversion 20t (2σ) + SL 6% | -5.02% | -3.53% | -24.70% | -12.98% | 4.69% | 20.10% | 36.90% | 15.47% | 116 | 2.47% |
| Buy & Hold (equal-weight) | -13.55% | -1.65% | -37.08% | -26.00% | 8.61% | 68.46% | 32.00% | 34.95% | 10 | 0.10% |
| Trend Following SMA20/60 + SL 6% | -14.93% | -5.42% | -48.63% | -31.12% | 9.56% | 65.31% | 32.10% | 37.16% | 167 | 3.31% |

### Mixed Regime (Markov aktif) (`MIXED_REGIME`)

1000 run × 1000 tick.

| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | Trade median | Fee median |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Buy & Hold (equal-weight) | -2.35% | 2.09% | -23.73% | -12.83% | 10.43% | 42.61% | 44.70% | 19.92% | 10 | 0.10% |
| Mean Reversion 20t (2σ) + SL 6% | -3.57% | -2.71% | -18.78% | -9.96% | 3.48% | 15.84% | 36.40% | 11.60% | 118 | 2.53% |
| Trend Following SMA20/60 + SL 6% | -7.24% | -2.40% | -30.03% | -17.94% | 8.29% | 41.21% | 35.90% | 22.40% | 130 | 2.72% |

## Deteksi Strategi Dominan

| Skenario | Strategi median tertinggi | Median | Selisih vs Buy & Hold |
|---|---|---:|---:|
| Bull Run | Buy & Hold (equal-weight) | 3.12% | 0.00% |
| Bear Crash | Mean Reversion 20t (2σ) + SL 6% | -4.16% | 4.37% |
| Sideways Drift | Buy & Hold (equal-weight) | 0.97% | 0.00% |
| Crisis Shock | Mean Reversion 20t (2σ) + SL 6% | -5.02% | 8.53% |
| Mixed Regime (Markov aktif) | Buy & Hold (equal-weight) | -2.35% | 0.00% |

**TEMUAN: buy-and-hold dominan di 3/5 skenario.** Perlu penyesuaian balancing agar strategi aktif kompetitif.

## Inflasi Uang Virtual

Inflasi uang = median ekuitas akhir buy-and-hold ÷ modal awal.

| Skenario | Indeks inflasi | Median return B&H | Status |
|---|---:|---:|---|
| Bull Run | 1.031× | 3.12% | Wajar |
| Bear Crash | 0.915× | -8.52% | Deflasi — median pemain kehilangan modal |
| Sideways Drift | 1.010× | 0.97% | Wajar |
| Crisis Shock | 0.864× | -13.55% | Deflasi — median pemain kehilangan modal |
| Mixed Regime (Markov aktif) | 0.977× | -2.35% | Wajar |


## Rasio Buy-and-Hold vs Strategi Aktif

| Skenario | Median B&H | Median aktif terbaik | Rasio aktif/B&H | Run di mana aktif > B&H |
|---|---:|---:|---:|---:|
| Bull Run | 3.12% | -2.22% | n/a | 47.70% |
| Bear Crash | -8.52% | -4.16% | n/a | 71.20% |
| Sideways Drift | 0.97% | -2.94% | n/a | 49.30% |
| Crisis Shock | -13.55% | -5.02% | n/a | 75.10% |
| Mixed Regime (Markov aktif) | -2.35% | -3.57% | n/a | 64.00% |


## Latensi Tick Engine (proxy JVM desktop)

| Metrik | Nilai |
|---|---:|
| Sampel | 6000 |
| P50 | 132.7 µs |
| P95 | 152.4 µs |
| P99 | 223.5 µs |
| Max | 3228.8 µs |
| Mean | 122.5 µs |

P99 di bawah anggaran 16 ms/tick. Angka ini dari JVM desktop (x86_64, JIT hangat) sehingga hanya batas bawah; verifikasi perangkat low-end wajib lewat macrobenchmark.

