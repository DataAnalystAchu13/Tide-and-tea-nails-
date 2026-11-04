package com.tideandtea.owner.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
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

    suspend fun confirmPayment(b: Booking) {
        ref(b.id).update(
            mapOf(
                "status" to BookingStatus.CONFIRMED.key,
                "confirmedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

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

    suspend fun markDone(b: Booking) {
        ref(b.id).update(
            mapOf("status" to BookingStatus.COMPLETED.key, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
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
