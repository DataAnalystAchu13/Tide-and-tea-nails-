package com.tideandtea.owner.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime

/** The one Supabase client the app uses. Call [init] from Application.onCreate. */
object Supa {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = SupabaseConfig.URL, supabaseKey = SupabaseConfig.ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}

// Small helpers for reading PostgREST rows decoded as JsonObject.
internal fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""
internal fun JsonObject.strOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0
internal fun JsonObject.double(key: String): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0
internal fun JsonObject.obj(key: String): JsonObject? = this[key]?.takeUnless { it is JsonNull }?.let { runCatching { it.jsonObject }.getOrNull() }
internal fun JsonObject.list(key: String): List<JsonElement> = this[key]?.takeUnless { it is JsonNull }?.let { runCatching { it.jsonArray.toList() }.getOrNull() } ?: emptyList()
internal fun JsonObject.strings(key: String): List<String> = list(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
internal fun JsonObject.millis(key: String): Long =
    strOrNull(key)?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
internal fun JsonElement.asObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
internal fun JsonElement.asStringOrNull(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()
