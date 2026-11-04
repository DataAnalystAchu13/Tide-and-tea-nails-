package com.tideandtea.owner.shop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.tideandtea.owner.data.Supa
import com.tideandtea.owner.data.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Everything the Orders and Website tabs read and write.
 * Uses the app's shared Supabase client; row level security limits writes to owners.
 */
object ShopRepository {
    private val client get() = Supa.client

    /* ---------------- Orders ---------------- */

    suspend fun orders(): List<Order> =
        client.from("orders").select {
            order("created_at", SortOrder.DESCENDING)
            limit(300)
        }.decodeList<JsonObject>().map(Order::from)

    /** The order list now and again after every change. Removes its realtime channel when collection stops. */
    fun liveOrders(): Flow<List<Order>> = channelFlow {
        val ch = client.channel("orders-${UUID.randomUUID()}")
        val changes = ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "orders" }
        launch { changes.conflate().collect { send(orders()) } }
        ch.subscribe()
        send(orders())
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                withContext(NonCancellable) { runCatching { client.realtime.removeChannel(ch) } }
            }
        }
    }.conflate()

    suspend fun updateOrder(id: String, fields: JsonObject) {
        client.from("orders").update(fields) { filter { eq("id", id) } }
    }

    /** Confirms a UPI or cash payment: the shop function takes stock and records the payment. */
    suspend fun confirmPayment(orderId: String, method: String) {
        callFunction("shop", buildJsonObject { put("action", "confirm"); put("orderId", orderId); put("method", method) })
    }

    /* ---------------- Catalogue ---------------- */

    suspend fun products(): List<Product> =
        client.from("products").select { order("sort", SortOrder.ASCENDING); order("created_at", SortOrder.DESCENDING) }
            .decodeList<JsonObject>().map(Product::from)

    suspend fun saveProduct(p: Product): Product {
        val row = if (p.id.isNullOrBlank()) {
            client.from("products").insert(p.copy(id = null).toJson()) { select() }.decodeSingle<JsonObject>()
        } else {
            client.from("products").update(p.toJson()) { filter { eq("id", p.id) }; select() }.decodeSingle<JsonObject>()
        }
        return Product.from(row)
    }

    suspend fun deleteProduct(id: String) { client.from("products").delete { filter { eq("id", id) } } }

    suspend fun posts(): List<Post> =
        client.from("posts").select { order("created_at", SortOrder.DESCENDING) }.decodeList<JsonObject>().map(Post::from)

    suspend fun savePost(p: Post): Post {
        val row = if (p.id.isNullOrBlank()) client.from("posts").insert(p.copy(id = null).toJson()) { select() }.decodeSingle<JsonObject>()
        else client.from("posts").update(p.toJson()) { filter { eq("id", p.id) }; select() }.decodeSingle<JsonObject>()
        return Post.from(row)
    }

    suspend fun deletePost(id: String) { client.from("posts").delete { filter { eq("id", id) } } }

    suspend fun reviews(): List<Review> =
        client.from("reviews").select { order("created_at", SortOrder.DESCENDING) }.decodeList<JsonObject>().map(Review::from)

    suspend fun saveReview(r: Review): Review {
        val row = if (r.id.isNullOrBlank()) client.from("reviews").insert(r.copy(id = null).toJson()) { select() }.decodeSingle<JsonObject>()
        else client.from("reviews").update(r.toJson()) { filter { eq("id", r.id) }; select() }.decodeSingle<JsonObject>()
        return Review.from(row)
    }

    suspend fun deleteReview(id: String) { client.from("reviews").delete { filter { eq("id", id) } } }

    suspend fun coupons(): List<Coupon> =
        client.from("coupons").select { order("created_at", SortOrder.DESCENDING) }.decodeList<JsonObject>().map(Coupon::from)

    suspend fun saveCoupon(c: Coupon) { client.from("coupons").upsert(c.toJson()) }

    suspend fun deleteCoupon(code: String) { client.from("coupons").delete { filter { eq("code", code) } } }

    /* ---------------- Site content and prices ---------------- */

    /** site_settings.data merged over the defaults bundled with the app. */
    suspend fun site(context: Context): JsonObject {
        val rows = client.from("site_settings").select { filter { eq("id", 1) } }.decodeList<JsonObject>()
        return deepMerge(defaults(context, "default-site.json"), rows.firstOrNull()?.obj("data"))
    }

    suspend fun saveSite(data: JsonObject) {
        client.from("site_settings").update(buildJsonObject { put("data", data) }) { filter { eq("id", 1) } }
    }

    /** The booking price list (config 'main'), merged over the bundled defaults. */
    suspend fun prices(context: Context): JsonObject {
        val rows = client.from("config").select { filter { eq("id", "main") } }.decodeList<JsonObject>()
        return deepMerge(defaults(context, "default-config.json"), rows.firstOrNull()?.obj("data"))
    }

    suspend fun savePrices(data: JsonObject) {
        client.from("config").upsert(buildJsonObject { put("id", "main"); put("data", data) })
    }

    /** Asks GitHub to rebuild and deploy the website. Returns a message to show. */
    suspend fun publish(): String {
        val res = callFunction("publish-site", JsonObject(emptyMap()))
        return if (res.b("ok")) "Publishing. The website updates in about 2 minutes."
        else res.s("error").ifBlank { "Couldn't start publishing. Try again in a minute." }
    }

    suspend fun lastPublished(): Long = runCatching {
        client.from("site_publishes").select {
            filter { eq("status", "dispatched") }
            order("requested_at", SortOrder.DESCENDING)
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()?.millis("requested_at") ?: 0L
    }.getOrDefault(0L)

    /* ---------------- Photos ---------------- */

    /** Shrinks a picked photo (longest side 1600 px, JPEG 85) and uploads it. Returns its public URL. */
    suspend fun uploadPhoto(context: Context, uri: Uri, folder: String): String {
        val bytes = withContext(Dispatchers.IO) { shrink(context, uri) }
        val path = "$folder/${UUID.randomUUID()}.jpg"
        val bucket = client.storage.from("site")
        bucket.upload(path, bytes) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        return bucket.publicUrl(path)
    }

    private fun shrink(context: Context, uri: Uri, max: Int = 1600): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max || bounds.outHeight / (sample * 2) >= max) sample *= 2
        val raw = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("That file isn't a photo.")
        val rotation = runCatching {
            resolver.openInputStream(uri).use { ExifInterface(it!!).rotationDegrees }
        }.getOrDefault(0)
        val scale = minOf(1f, max.toFloat() / maxOf(raw.width, raw.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
    }

    /* ---------------- Helpers ---------------- */

    private val json = Json { ignoreUnknownKeys = true }

    private fun defaults(context: Context, asset: String): JsonObject =
        runCatching { json.parseToJsonElement(context.assets.open(asset).bufferedReader().readText()) as JsonObject }
            .getOrDefault(JsonObject(emptyMap()))

    /** Calls a Supabase edge function as the signed-in owner. Throws with a readable message on failure. */
    private suspend fun callFunction(name: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val token = client.auth.currentAccessTokenOrNull() ?: error("Sign in again.")
        val conn = (URL("${SupabaseConfig.URL.trimEnd('/')}/functions/v1/$name").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty("authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val ok = conn.responseCode in 200..299
            val text = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            val out = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
            if (!ok) error(out.s("error").ifBlank { "Request failed (${conn.responseCode})." })
            out
        } finally {
            conn.disconnect()
        }
    }
}
