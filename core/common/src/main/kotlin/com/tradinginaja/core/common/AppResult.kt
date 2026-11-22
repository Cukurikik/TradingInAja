package com.tradinginaja.core.common

/**
 * Pembungkus hasil operasi domain/data yang eksplisit tanpa melempar exception tak terduga ke UI.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}
