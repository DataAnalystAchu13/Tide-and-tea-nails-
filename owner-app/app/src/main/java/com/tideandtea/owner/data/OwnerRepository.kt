package com.tideandtea.owner.data

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class NotOwnerException : Exception(
    "This account isn't set up as a studio owner yet. Add its user ID under \"owners\" in Firestore (see SETUP.md)."
)

/** All reads and writes the owner app makes. Security rules only let owner accounts do these. */
class OwnerRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {
    val signedInEmail: String? get() = auth.currentUser?.email

    suspend fun isOwner(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return runCatching { db.collection("owners").document(uid).get().await().exists() }.getOrDefault(false)
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        if (!isOwner()) {
            val uid = auth.currentUser?.uid
            auth.signOut()
            throw NotOwnerException().also { android.util.Log.w("TideOwner", "Not an owner. uid=$uid") }
        }
    }

    fun signOut() = auth.signOut()

    /** Live list of the latest bookings, newest first. */
    fun bookings(): Flow<List<Booking>> = callbackFlow {
        val reg = db.collection("bookings")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(300)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                if (snap != null) trySend(snap.documents.map { Booking.from(it) })
            }
        awaitClose { reg.remove() }
    }

    private fun ref(id: String) = db.collection("bookings").document(id)

    /** Approve with the final price; the slot is marked taken so customers can't pick it. */
    suspend fun approve(b: Booking, total: Int, advance: Int, note: String) {
        db.batch()
            .update(
                ref(b.id), mapOf(
                    "status" to BookingStatus.APPROVED.key,
                    "price.total" to total,
                    "price.advance" to advance,
                    "ownerNote" to note,
                    "approvedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            .set(db.collection("slots").document(b.date), mapOf("taken" to FieldValue.arrayUnion(b.slot)), SetOptions.merge())
            .commit().await()
    }

    suspend fun decline(b: Booking, note: String) {
        val batch = db.batch().update(
            ref(b.id), mapOf(
                "status" to BookingStatus.DECLINED.key,
                "ownerNote" to note,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        if (b.status != BookingStatus.REQUESTED) {
            batch.set(db.collection("slots").document(b.date), mapOf("taken" to FieldValue.arrayRemove(b.slot)), SetOptions.merge())
        }
        batch.commit().await()
    }

    /** Confirms the advance and records it in payment history. */
    suspend fun confirmPayment(b: Booking) {
        db.batch()
            .update(
                ref(b.id), mapOf(
                    "status" to BookingStatus.CONFIRMED.key,
                    "confirmedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            .set(db.collection("payments").document("${b.id}_advance"), paymentDoc(b, PaymentKind.ADVANCE, b.price.advance, "UPI", b.paymentRef))
            .commit().await()
    }

    private fun paymentDoc(b: Booking, kind: PaymentKind, amount: Int, method: String, reference: String) = mapOf(
        "bookingId" to b.id,
        "name" to b.name,
        "amount" to amount,
        "kind" to kind.key,
        "method" to method,
        "reference" to reference,
        "service" to (b.extension?.name ?: ""),
        "at" to FieldValue.serverTimestamp(),
    )

    suspend fun paymentNotFound(b: Booking) {
        ref(b.id).update(
            mapOf(
                "status" to BookingStatus.APPROVED.key,
                "paymentRef" to "",
                "ownerNote" to "We couldn't find a payment with reference ${b.paymentRef}. Please check and send it again.",
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /** Marks the appointment done and records the balance collected on the day. */
    suspend fun markDone(b: Booking, balanceMethod: String) {
        val balance = (b.price.total - b.price.advance).coerceAtLeast(0)
        val batch = db.batch().update(
            ref(b.id), mapOf(
                "status" to BookingStatus.COMPLETED.key,
                "balanceMethod" to balanceMethod,
                "completedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        if (balance > 0) batch.set(db.collection("payments").document("${b.id}_balance"), paymentDoc(b, PaymentKind.BALANCE, balance, balanceMethod, ""))
        batch.commit().await()
    }

    fun payments(): Flow<List<Payment>> = callbackFlow {
        val reg = db.collection("payments").orderBy("at", Query.Direction.DESCENDING).limit(500)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap != null) trySend(snap.documents.map { Payment.from(it) })
            }
        awaitClose { reg.remove() }
    }

    // ---------------- Chat ----------------

    fun chats(): Flow<List<ChatSummary>> = callbackFlow {
        val reg = db.collection("chats").orderBy("lastAt", Query.Direction.DESCENDING).limit(200)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap != null) trySend(snap.documents.map { ChatSummary.from(it) })
            }
        awaitClose { reg.remove() }
    }

    fun messages(bookingId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("chats").document(bookingId).collection("messages")
            .orderBy("at", Query.Direction.ASCENDING).limitToLast(300)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap != null) trySend(snap.documents.map { ChatMessage.from(it) })
            }
        awaitClose { reg.remove() }
    }

    suspend fun markChatRead(bookingId: String) {
        runCatching { db.collection("chats").document(bookingId).update("ownerUnread", 0).await() }
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
        val chat = db.collection("chats").document(b.id)
        val msg = chat.collection("messages").document()
        var url: String? = null
        if (media != null && mediaType != null) {
            val ext = if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
            val file = storage.reference.child("chats/${b.id}/${msg.id}.$ext")
            val meta = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(contentType ?: if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg")
                .build()
            val task = file.putFile(media, meta)
            task.addOnProgressListener { s -> onProgress(s.bytesTransferred.toFloat() / maxOf(1L, s.totalByteCount)) }
            task.await()
            url = file.downloadUrl.await().toString()
        }
        val data = buildMap<String, Any> {
            put("from", "owner")
            put("text", text)
            put("at", FieldValue.serverTimestamp())
            if (url != null && mediaType != null) {
                put("mediaUrl", url)
                put("mediaType", if (mediaType == MediaType.VIDEO) "video" else "image")
            }
        }
        val preview = text.ifBlank { if (mediaType == MediaType.VIDEO) "Video" else "Photo" }
        db.batch()
            .set(msg, data)
            .set(
                chat, mapOf(
                    "customerUid" to b.customerUid,
                    "name" to b.name,
                    "lastText" to preview,
                    "lastFrom" to "owner",
                    "lastAt" to FieldValue.serverTimestamp(),
                    "customerUnread" to FieldValue.increment(1),
                    "ownerUnread" to 0,
                ), SetOptions.merge()
            )
            .commit().await()
    }

    // ---------------- Push notifications ----------------

    /** Saves this phone's FCM token on owners/{uid} so Cloud Functions can notify it. */
    suspend fun registerPushToken(token: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        val t = token ?: FirebaseMessaging.getInstance().token.await()
        runCatching { db.collection("owners").document(uid).update("fcmTokens", FieldValue.arrayUnion(t)).await() }
    }

    suspend fun unregisterPushToken() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            val t = FirebaseMessaging.getInstance().token.await()
            db.collection("owners").document(uid).update("fcmTokens", FieldValue.arrayRemove(t)).await()
        }
    }

    /** Writes the bundled default price list to config/main, only if none exists yet. */
    suspend fun publishDefaultPriceList(context: Context): Boolean {
        val doc = db.collection("config").document("main")
        if (doc.get().await().exists()) return false
        val json = context.assets.open("default-config.json").bufferedReader().use { it.readText() }
        doc.set(JSONObject(json).toMap()).await()
        return true
    }
}

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { k -> unwrap(get(k)) }
private fun JSONArray.toList(): List<Any?> = (0 until length()).map { unwrap(get(it)) }
private fun unwrap(v: Any?): Any? = when (v) {
    is JSONObject -> v.toMap()
    is JSONArray -> v.toList()
    JSONObject.NULL -> null
    else -> v
}
