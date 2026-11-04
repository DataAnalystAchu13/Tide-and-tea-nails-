package com.tideandtea.owner.data

import android.content.Context
import android.net.Uri
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class NotOwnerException : Exception(
    "This account isn't set up as a studio owner yet. Add its user ID to the owners table in Supabase (see SETUP.md)."
)

/**
 * All reads and writes the owner app makes. Row-level security in supabase/migrations only lets
 * owner accounts do these. Payments, taken slots and chat summaries are kept up to date by
 * database triggers, so the app only changes bookings and sends messages.
 */
class OwnerRepository(private val supabase: SupabaseClient = Supa.client) {
    val signedInEmail: String? get() = supabase.auth.currentUserOrNull()?.email

    suspend fun isOwner(): Boolean {
        supabase.auth.awaitInitialization()
        val uid = supabase.auth.currentUserOrNull()?.id ?: return false
        return runCatching {
            supabase.from("owners").select { filter { eq("user_id", uid) } }.decodeList<JsonObject>().isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        if (!isOwner()) {
            val uid = supabase.auth.currentUserOrNull()?.id
            supabase.auth.signOut()
            throw NotOwnerException().also { android.util.Log.w("TideOwner", "Not an owner. uid=$uid") }
        }
    }

    suspend fun signOut() = supabase.auth.signOut()

    /**
     * Emits [fetch] now and again after every change to [table]. Realtime only delivers rows
     * this account may read, and a refetch keeps the logic identical to the first load.
     */
    private fun <T> live(table: String, fetch: suspend () -> T): Flow<T> = channelFlow {
        val channel = supabase.channel("$table-${UUID.randomUUID()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { this.table = table }
        launch { changes.conflate().collect { send(fetch()) } }
        channel.subscribe()
        send(fetch())
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                withContext(NonCancellable) { runCatching { supabase.realtime.removeChannel(channel) } }
            }
        }
    }.conflate()

    /** Live list of the latest bookings, newest first. */
    fun bookings(): Flow<List<Booking>> = live("bookings") {
        supabase.from("bookings").select {
            order("created_at", Order.DESCENDING)
            limit(300)
        }.decodeList<JsonObject>().map(Booking::from)
    }

    private suspend fun update(b: Booking, values: JsonObject) {
        supabase.from("bookings").update(values) { filter { eq("id", b.id) } }
    }

    /** Approve with the final price. The database marks the slot as taken. */
    suspend fun approve(b: Booking, total: Int, advance: Int, note: String) {
        update(b, buildJsonObject {
            put("status", BookingStatus.APPROVED.key)
            put("price", buildJsonObject {
                put("base", b.price.base)
                put("length", b.price.length)
                put("addOns", b.price.addOns)
                put("travel", b.price.travel)
                put("total", total)
                put("advance", advance)
            })
            put("owner_note", note)
        })
    }

    /** Declines; if the slot had been held, the database frees it. */
    suspend fun decline(b: Booking, note: String) {
        update(b, buildJsonObject {
            put("status", BookingStatus.DECLINED.key)
            put("owner_note", note)
        })
    }

    /** Confirms the advance. The database records it in payment history. */
    suspend fun confirmPayment(b: Booking, total: Int = b.price.total, note: String = "") {
        update(b, buildJsonObject {
            put("status", BookingStatus.CONFIRMED.key)
            // The client paid before the owner reviewed, so the final total (travel, "price varies"
            // add-ons) is set here. The advance stays what they actually paid.
            if (total != b.price.total) put("price", buildJsonObject {
                put("base", b.price.base)
                put("length", b.price.length)
                put("addOns", b.price.addOns)
                put("travel", b.price.travel)
                put("total", total)
                put("advance", b.price.advance)
            })
            if (note.isNotBlank()) put("owner_note", note)
        })
    }

    suspend fun paymentNotFound(b: Booking) {
        update(b, buildJsonObject {
            put("status", BookingStatus.APPROVED.key)
            put("payment_ref", "")
            put("owner_note", "We couldn't find a payment with reference ${b.paymentRef}. Please check and send it again.")
        })
    }

    /** Marks the appointment done. The database records the balance collected on the day. */
    suspend fun markDone(b: Booking, balanceMethod: String) {
        update(b, buildJsonObject {
            put("status", BookingStatus.COMPLETED.key)
            put("balance_method", balanceMethod)
        })
    }

    fun payments(): Flow<List<Payment>> = live("payments") {
        supabase.from("payments").select {
            order("created_at", Order.DESCENDING)
            limit(500)
        }.decodeList<JsonObject>().map(Payment::from)
    }

    // ---------------- Chat ----------------

    fun chats(): Flow<List<ChatSummary>> = live("chats") {
        supabase.from("chats").select {
            order("last_at", Order.DESCENDING)
            limit(200)
        }.decodeList<JsonObject>().map(ChatSummary::from)
    }

    fun messages(bookingId: String): Flow<List<ChatMessage>> = live("messages") {
        supabase.from("messages").select {
            filter { eq("booking_id", bookingId) }
            order("created_at", Order.DESCENDING)
            limit(300)
        }.decodeList<JsonObject>().map(ChatMessage::from).reversed()
    }

    suspend fun markChatRead(bookingId: String) {
        runCatching { supabase.postgrest.rpc("mark_chat_read", buildJsonObject { put("p_booking_id", bookingId) }) }
    }

    /** Uploads the photo or video (if any) to Storage, then posts the message. */
    suspend fun sendMessage(
        b: Booking,
        text: String,
        media: Uri?,
        mediaType: MediaType?,
        contentType: String?,
        onProgress: (Float) -> Unit = {},
    ) {
        var url: String? = null
        if (media != null && mediaType != null) {
            onProgress(0.05f)
            val bytes = withContext(Dispatchers.IO) {
                Supa.appContext.contentResolver.openInputStream(media)?.use { it.readBytes() }
            } ?: error("Couldn't read that file")
            val isVideo = mediaType == MediaType.VIDEO
            val path = "${b.id}/${UUID.randomUUID()}.${if (isVideo) "mp4" else "jpg"}"
            val bucket = supabase.storage.from("chats")
            bucket.upload(path, bytes) {
                upsert = false
                this.contentType = ContentType.parse(contentType ?: if (isVideo) "video/mp4" else "image/jpeg")
            }
            onProgress(1f)
            url = bucket.publicUrl(path)
        }
        supabase.from("messages").insert(buildJsonObject {
            put("booking_id", b.id)
            put("sender", "owner")
            put("text", text)
            if (url != null && mediaType != null) {
                put("media_url", url)
                put("media_type", if (mediaType == MediaType.VIDEO) "video" else "image")
            }
        })
    }

    // ---------------- Push notifications ----------------

    /** Saves this phone's FCM token so the notify-owners function can reach it. */
    suspend fun registerPushToken(token: String? = null) {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return
        val t = token ?: FirebaseMessaging.getInstance().token.await()
        runCatching {
            supabase.from("push_tokens").upsert(buildJsonObject {
                put("token", t)
                put("user_id", uid)
                put("platform", "android")
            })
        }
    }

    suspend fun unregisterPushToken() {
        runCatching {
            val t = FirebaseMessaging.getInstance().token.await()
            supabase.from("push_tokens").delete { filter { eq("token", t) } }
        }
    }

    /** Writes the bundled default price list to config 'main', only if none exists yet. */
    suspend fun publishDefaultPriceList(context: Context): Boolean {
        val existing = supabase.from("config").select { filter { eq("id", "main") } }.decodeList<JsonObject>()
        if (existing.isNotEmpty()) return false
        val json = context.assets.open("default-config.json").bufferedReader().use { it.readText() }
        val data = kotlinx.serialization.json.Json.parseToJsonElement(json)
        supabase.from("config").insert(buildJsonObject {
            put("id", "main")
            put("data", data)
        })
        return true
    }
}
