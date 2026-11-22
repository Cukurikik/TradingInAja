package com.tradinginaja.domain.model

enum class SimulationSpeed(val multiplier: Int, val intervalMillis: Long) {
    PAUSED(multiplier = 0, intervalMillis = 0L),
    SPEED_1X(multiplier = 1, intervalMillis = 1_000L),
    SPEED_2X(multiplier = 2, intervalMillis = 500L),
    SPEED_5X(multiplier = 5, intervalMillis = 200L)
}
