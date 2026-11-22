package com.tradinginaja.core.common

/**
 * Pusat seluruh konstanta ekonomi, skala mata uang virtual, dan parameter balancing.
 * Semua nilai moneter disimpan dalam satuan terkecil (minor units / sen).
 */
object GameBalance {
    /** 1 unit mata uang utama = 100 satuan terkecil (sen). */
    const val MINOR_UNITS_PER_MAJOR: Long = 100L

    /** Jumlah digit desimal untuk tampilan mata uang standar. */
    const val CURRENCY_SCALE_DIGITS: Int = 2

    /** Basis points pembagi (10.000 bps = 100%). */
    const val BASIS_POINTS_DIVISOR: Long = 10_000L

    /** Saldo virtual awal pemain: 100.000.000,00 (100 juta unit virtual / 10_000_000_000L sen). */
    const val INITIAL_VIRTUAL_CASH_MINOR: Long = 10_000_000_000L

    /** Biaya simulasi transaksi standar dalam basis points (0,15% = 15 bps). */
    const val DEFAULT_TRADING_FEE_BPS: Long = 15L

    /** Biaya minimum transaksi (1 sen) untuk order bernilai > 0. */
    const val MIN_TRADING_FEE_MINOR: Long = 1L

    /** Batas atas slippage simulasi per eksekusi (500 bps = 5,00%). */
    const val MAX_SLIPPAGE_BPS: Long = 500L

    /** Tambahan slippage saat order stop terkena gap pasar (25 bps = 0,25%). */
    const val STOP_GAP_EXTRA_SLIPPAGE_BPS: Long = 25L

    /** Tambahan slippage saat eksekusi likuidasi paksa (35 bps = 0,35%). */
    const val LIQUIDATION_EXTRA_SLIPPAGE_BPS: Long = 35L

    /** Koefisien dampak ukuran order terhadap volume tick untuk model slippage. */
    const val SLIPPAGE_SIZE_IMPACT_FACTOR_BPS: Double = 60.0

    /** Ambang peringatan konsentrasi posisi tunggal (40% dari ekuitas, sesuai DESIGN.md). */
    const val POSITION_CONCENTRATION_WARNING_PCT: Double = 40.0

    /** Batas jumlah titik kurva ekuitas yang disimpan dan dirender. */
    const val MAX_EQUITY_CURVE_POINTS: Int = 400

    /** Simbol mata uang virtual default (fiktif, edukatif). */
    const val VIRTUAL_CURRENCY_SYMBOL: String = "vRp"

    /** Durasi waktu pasar virtual per 1 tick simulasi (dalam menit). */
    const val MINUTES_PER_TICK: Int = 15

    /** Jumlah tick per hari bursa reguler (6,5 jam = 26 tick x 15 menit). */
    const val TICKS_PER_TRADING_DAY: Int = 26

    /** Jumlah hari bursa per tahun untuk skala drift & volatilitas GBM. */
    const val TRADING_DAYS_PER_YEAR: Int = 252

    /** Total tick per tahun bursa (252 * 26 = 6.552 tick). */
    const val TICKS_PER_TRADING_YEAR: Double = (TRADING_DAYS_PER_YEAR * TICKS_PER_TRADING_DAY).toDouble()

    /** Batas bawah mutlak harga instrumen simulasi agar tidak pernah <= 0. */
    const val MIN_ASSET_PRICE_DOUBLE: Double = 0.01

    /** Batas bawah mutlak harga dalam satuan terkecil Money. */
    const val MIN_ASSET_PRICE_MINOR: Long = 1L

    /** Jumlah candle 15m bawaan yang digenerate saat inisialisasi aset. */
    const val DEFAULT_INITIAL_HISTORY_CANDLES: Int = 120

    /** Maksimum candle 15m yang disimpan dalam memori per aset. */
    const val MAX_STORED_HISTORY_CANDLES: Int = 2_400

    /** Batas maksimum titik/candle yang dirender langsung sebelum downsampling di Canvas. */
    const val DEFAULT_CHART_MAX_RENDER_POINTS: Int = 120

    /** Seed bawaan untuk sesi simulasi aset perdana. */
    const val DEFAULT_SIMULATION_SEED: Long = 20_261_121L

    /** Ambang margin level untuk peringatan Margin Call (110%, sesuai DESIGN.md). */
    const val MARGIN_CALL_LEVEL_PCT: Double = 110.0

    /** Ambang margin level untuk likuidasi paksa (100%, sesuai DESIGN.md). */
    const val LIQUIDATION_MARGIN_LEVEL_PCT: Double = 100.0

    /** Biaya pinjam/funding tahunan untuk posisi leverage & short selling (12% = 1.200 bps/tahun). */
    const val ANNUAL_BORROW_FEE_BPS: Double = 1_200.0

    /** Leverage maksimum Level 1 (Trader Pemula) & Level 2 (Trader Aktif): 1x (spot). */
    const val MAX_LEVERAGE_SPOT: Int = 1

    /** Leverage maksimum Level 3 (Trader Profesional): 3x. */
    const val MAX_LEVERAGE_PRO: Int = 3

    /** Leverage maksimum Level 4 (Manajer Dana) & Level 5 (Hedge Fund): 5x. */
    const val MAX_LEVERAGE_FUND: Int = 5

    /** Jumlah minimum sampel return non-nol sebelum Sharpe sederhana dihitung. */
    const val MIN_SHARPE_RETURN_SAMPLES: Int = 3

    /** Rasio Risk/Reward batas atas tampilan ketika rata-rata rugi nol. */
    const val MAX_DISPLAY_RISK_REWARD: Double = 99.9

    /** Parameter GARCH(1,1) default: bobot varians jangka panjang, guncangan, dan persistensi. */
    const val DEFAULT_GARCH_OMEGA_WEIGHT: Double = 0.06
    const val DEFAULT_GARCH_ALPHA: Double = 0.14
    const val DEFAULT_GARCH_BETA: Double = 0.80

    /** Batas pengali volatilitas kondisional GARCH terhadap volatilitas dasar aset. */
    const val GARCH_MIN_VOL_RATIO: Double = 0.45
    const val GARCH_MAX_VOL_RATIO: Double = 3.20

    /** Kapasitas maksimum riwayat berita yang disimpan deterministik di state simulasi. */
    const val MAX_NEWS_HISTORY: Int = 120

    /** Salt PRNG khusus subsistem event agar aliran RNG terpisah bersih. */
    const val EVENT_ENGINE_RNG_SALT: Long = 50_050L

    /** Baris probabilitas transisi Markov default [BULL, BEAR, SIDEWAYS, CRISIS]. */
    val REGIME_ROW_BULL = doubleArrayOf(0.940, 0.015, 0.040, 0.005)
    val REGIME_ROW_BEAR = doubleArrayOf(0.015, 0.920, 0.045, 0.020)
    val REGIME_ROW_SIDEWAYS = doubleArrayOf(0.030, 0.030, 0.930, 0.010)
    val REGIME_ROW_CRISIS = doubleArrayOf(0.010, 0.080, 0.030, 0.880)

    /** Pengaruh drift tahunan & pengali volatilitas per rezim untuk aset berisiko standar. */
    const val REGIME_BULL_DRIFT_DELTA: Double = 0.10
    const val REGIME_BULL_VOL_MULT: Double = 0.92
    const val REGIME_BULL_JUMP_MULT: Double = 0.85

    const val REGIME_SIDEWAYS_DRIFT_DELTA: Double = -0.02
    const val REGIME_SIDEWAYS_VOL_MULT: Double = 0.82
    const val REGIME_SIDEWAYS_JUMP_MULT: Double = 0.70

    const val REGIME_BEAR_DRIFT_DELTA: Double = -0.24
    const val REGIME_BEAR_VOL_MULT: Double = 1.32
    const val REGIME_BEAR_JUMP_MULT: Double = 1.35

    const val REGIME_CRISIS_DRIFT_DELTA: Double = -0.52
    const val REGIME_CRISIS_VOL_MULT: Double = 1.85
    const val REGIME_CRISIS_JUMP_MULT: Double = 1.90

    /** Pengaruh rezim khusus aset safe-haven (mis. Emas Virtual XVMG). */
    const val SAFE_HAVEN_BULL_DRIFT_DELTA: Double = -0.01
    const val SAFE_HAVEN_BEAR_DRIFT_DELTA: Double = 0.08
    const val SAFE_HAVEN_CRISIS_DRIFT_DELTA: Double = 0.18
    const val SAFE_HAVEN_CRISIS_VOL_MULT: Double = 1.25

    /** Faktor redaman pengaruh rezim pada pasangan forex mean-reverting. */
    const val FOREX_REGIME_DRIFT_SCALE: Double = 0.25
    const val FOREX_CRISIS_VOL_MULT: Double = 1.30

    /** Ambang batas aman untuk parameter event dan volatilitas gabungan. */
    const val MIN_EFFECTIVE_VOLATILITY: Double = 0.02
    const val MAX_EFFECTIVE_VOLATILITY: Double = 3.50

    /** Ambang slippage tinggi untuk peringatan preview & analisis post-mortem (25 bps). */
    const val HIGH_SLIPPAGE_WARNING_BPS: Long = 25L

    /** Aturan manajemen risiko disiplin per trade: maksimal 2% dari ekuitas pada Stop-Loss. */
    const val MAX_DISCIPLINED_RISK_PER_TRADE_PCT: Double = 2.0
    const val IDEAL_RISK_PER_TRADE_PCT: Double = 1.0

    /** Parameter simulator position sizing bebas risiko di Mode Belajar. */
    const val LEARN_SANDBOX_MIN_RISK_PCT: Double = 0.5
    const val LEARN_SANDBOX_DEFAULT_RISK_PCT: Double = 1.5
    const val LEARN_SANDBOX_MAX_RISK_PCT: Double = 5.0

    /** Jumlah langkah tutorial interaktif pada peluncuran awal / mode ulangi. */
    const val INTERACTIVE_TUTORIAL_STEP_COUNT: Int = 5

    /** Target misi kompetensi Level 1 -> Level 2. */
    const val LV1_TARGET_TRADES: Int = 5
    const val LV1_TARGET_DISTINCT_ASSETS: Int = 2
    const val LV1_TARGET_STOP_LOSSES: Int = 2
    const val LV1_MAX_DRAWDOWN_PCT: Double = 15.0

    /** Target misi kompetensi Level 2 -> Level 3. */
    const val LV2_TARGET_TRADES: Int = 15
    const val LV2_TARGET_LIMIT_STOP_FILLS: Int = 3
    const val LV2_TARGET_DISCIPLINED_RISK_ORDERS: Int = 2
    const val LV2_TARGET_CANDLE_DRILLS: Int = 2
    const val LV2_MIN_RISK_REWARD: Double = 1.2

    /** Target misi kompetensi Level 3 -> Level 4. */
    const val LV3_TARGET_TRADES: Int = 25
    const val LV3_TARGET_SHORT_OR_HEDGE_FILLS: Int = 5
    const val LV3_MAX_DRAWDOWN_PCT: Double = 12.0

    /** Target misi kompetensi Level 4 -> Level 5. */
    const val LV4_TARGET_ASSET_CATEGORIES: Int = 4
    const val LV4_TARGET_CANDLE_DRILLS: Int = 4
    const val LV4_MAX_DRAWDOWN_PCT: Double = 10.0
    const val LV4_MIN_WIN_RATE_PCT: Double = 50.0
}
