package com.tradinginaja.simulation.catalog

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

internal fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray

internal fun JsonObject.optionalArray(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.optionalObject(key: String): JsonObject? = this[key]?.jsonObject
