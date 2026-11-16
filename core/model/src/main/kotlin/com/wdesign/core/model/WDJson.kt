package com.wdesign.core.model

import kotlinx.serialization.json.Json

/** The one JSON configuration used for specs everywhere (files, database, links). */
val WDJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    prettyPrint = false
}

val WDJsonPretty: Json = Json(WDJson) { prettyPrint = true }

fun ThemeSpec.toJson(): String = WDJson.encodeToString(ThemeSpec.serializer(), this)
fun themeSpecFromJson(json: String): ThemeSpec = WDJson.decodeFromString(ThemeSpec.serializer(), json)

/** Short unique ids without java.util.UUID dashes. */
object Ids {
    private val rnd = java.security.SecureRandom()
    fun next(prefix: String = ""): String {
        val b = ByteArray(8).also(rnd::nextBytes)
        return prefix + b.joinToString("") { "%02x".format(it) }
    }
}
