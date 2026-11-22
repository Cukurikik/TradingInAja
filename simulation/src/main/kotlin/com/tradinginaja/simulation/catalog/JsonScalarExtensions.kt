package com.tradinginaja.simulation.catalog

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

internal fun JsonObject.optionalString(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

internal fun JsonObject.stringOr(key: String, fallback: String): String = optionalString(key) ?: fallback

internal fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.content.toDouble()

internal fun JsonObject.doubleOr(key: String, fallback: Double): Double =
    this[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: fallback

internal fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.content.toLong()

internal fun JsonObject.longOr(key: String, fallback: Long): Long =
    this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: fallback

internal fun JsonObject.optionalLong(key: String): Long? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.toLongOrNull()

internal fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.content.toInt()

internal fun JsonObject.intOr(key: String, fallback: Int): Int =
    this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: fallback

internal fun JsonObject.booleanOr(key: String, fallback: Boolean): Boolean =
    this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: fallback
