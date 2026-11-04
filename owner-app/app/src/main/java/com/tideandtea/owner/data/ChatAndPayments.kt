package com.tideandtea.owner.data

import kotlinx.serialization.json.JsonObject

/** public.chats: one conversation per booking (maintained by a database trigger). */
data class ChatSummary(
    val bookingId: String,
    val name: String,
    val lastText: String,
    val lastFrom: String,
    val lastAtMillis: Long,
    val ownerUnread: Int,
) {
    companion object {
        fun from(r: JsonObject) = ChatSummary(
            bookingId = r.str("booking_id"),
            name = r.str("name").ifBlank { "Client" },
            lastText = r.str("last_text"),
            lastFrom = r.str("last_from"),
            lastAtMillis = r.millis("last_at"),
            ownerUnread = r.int("owner_unread"),
        )
    }
}

enum class MediaType { IMAGE, VIDEO }

/** public.messages */
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
        fun from(r: JsonObject) = ChatMessage(
            id = r.str("id"),
            fromOwner = r.str("sender") == "owner",
            text = r.str("text"),
            mediaUrl = r.strOrNull("media_url"),
            mediaType = when (r.strOrNull("media_type")) {
                "image" -> MediaType.IMAGE
                "video" -> MediaType.VIDEO
                else -> null
            },
            atMillis = r.millis("created_at").takeIf { it > 0 } ?: System.currentTimeMillis(),
            pending = false,
        )
    }
}

enum class PaymentKind(val key: String, val label: String) {
    ADVANCE("advance", "Advance"),
    BALANCE("balance", "Balance"),
    SHOP("shop", "Shop order");

    companion object {
        fun from(k: String?) = entries.firstOrNull { it.key == k } ?: ADVANCE
    }
}

/** public.payments: written by database triggers when a booking is confirmed or completed. */
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
        fun from(r: JsonObject) = Payment(
            id = r.str("id"),
            bookingId = r.str("booking_id"),
            name = r.str("name"),
            amount = r.int("amount"),
            kind = PaymentKind.from(r.strOrNull("kind")),
            method = r.str("method"),
            reference = r.str("reference"),
            atMillis = r.millis("created_at"),
        )
    }
}
