package com.tradinginaja.domain.model

/**
 * Mode tema tampilan yang dapat dipilih pengguna dan disimpan di DataStore.
 *
 * [COLOR_BLIND_LIGHT] dan [COLOR_BLIND_DARK] memakai palet biru-oranye (aman untuk
 * deuteranopia/protanopia) sehingga arah naik/turun tetap terbaca tanpa mengandalkan
 * kontras hijau-merah.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
    COLOR_BLIND_LIGHT,
    COLOR_BLIND_DARK
}
