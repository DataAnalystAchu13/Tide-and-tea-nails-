package com.tideandtea.owner.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/** chats/{bookingId}: one conversation per booking. */
data class ChatSummary(
    val bookingId: String,
    val name: String,
    val lastText: String,
    val lastFrom: String,
    val lastAtMillis: Long,
    val ownerUnread: Int,
) {
    companion object {
        fun from(doc: DocumentSnapshot) = ChatSummary(
            bookingId = doc.id,
            name = doc.getString("name") ?: "Client",
            lastText = doc.getString("lastText") ?: "",
            lastFrom = doc.getString("lastFrom") ?: "",
            lastAtMillis = doc.getTimestamp("lastAt")?.toDate()?.time ?: 0L,
            ownerUnread = doc.getLong("ownerUnread")?.toInt() ?: 0,
        )
    }
}

enum class MediaType { IMAGE, VIDEO }

/** chats/{bookingId}/messages/{id} */
data class ChatMessage(
    val id: String,
    val fromOwner: Boolean,
    val text: String,
    val mediaUrl: String?,
    val mediaType: MediaType?,
    val atMillis: Long,
    val pending: Boolean,
) {
    companion object {
        fun from(doc: DocumentSnapshot) = ChatMessage(
            id = doc.id,
            fromOwner = doc.getString("from") == "owner",
            text = doc.getString("text") ?: "",
            mediaUrl = doc.getString("mediaUrl"),
            mediaType = when (doc.getString("mediaType")) {
                "image" -> MediaType.IMAGE
                "video" -> MediaType.VIDEO
                else -> null
            },
            atMillis = (doc.get("at") as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
            pending = doc.metadata.hasPendingWrites(),
        )
    }
}

enum class PaymentKind(val key: String, val label: String) {
    ADVANCE("advance", "Advance"),
    BALANCE("balance", "Balance");

    companion object {
        fun from(k: String?) = entries.firstOrNull { it.key == k } ?: ADVANCE
    }
}

/** payments/{id}: written by the owner app when money is confirmed. */
data class Payment(
    val id: String,
    val bookingId: String,
    val name: String,
    val amount: Int,
    val kind: PaymentKind,
    val method: String,
    val reference: String,
    val atMillis: Long,
) {
    companion object {
        fun from(doc: DocumentSnapshot) = Payment(
            id = doc.id,
            bookingId = doc.getString("bookingId") ?: "",
            name = doc.getString("name") ?: "",
            amount = doc.getLong("amount")?.toInt() ?: 0,
            kind = PaymentKind.from(doc.getString("kind")),
            method = doc.getString("method") ?: "",
            reference = doc.getString("reference") ?: "",
            atMillis = doc.getTimestamp("at")?.toDate()?.time ?: 0L,
        )
    }
}
