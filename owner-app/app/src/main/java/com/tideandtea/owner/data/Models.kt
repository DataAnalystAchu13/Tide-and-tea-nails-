package com.tideandtea.owner.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mirrors the documents the customer website writes to Firestore at bookings/{id}. */
enum class BookingStatus(val key: String, val label: String) {
    REQUESTED("requested", "New request"),
    APPROVED("approved", "Awaiting advance"),
    PAYMENT_SUBMITTED("payment_submitted", "Check payment"),
    CONFIRMED("confirmed", "Confirmed"),
    COMPLETED("completed", "Done"),
    DECLINED("declined", "Declined");

    companion object {
        fun from(key: String?): BookingStatus = entries.firstOrNull { it.key == key } ?: REQUESTED
    }
}

data class PriceItem(val id: String, val name: String, val price: Int)

data class Price(
    val base: Int = 0,
    val length: Int = 0,
    val addOns: Int = 0,
    val travel: Int = 0,
    val total: Int = 0,
    val advance: Int = 0,
)

data class Place(val lat: Double, val lng: Double, val address: String) {
    /** Opens a pin in Google Maps (app if installed, browser otherwise). */
    val mapsUrl: String get() = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
    val directionsUrl: String get() = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"
}

data class Booking(
    val id: String,
    val customerUid: String,
    val name: String,
    val phone: String,
    val from: String,
    val instagram: String,
    val notes: String,
    val extension: PriceItem?,
    val length: PriceItem?,
    val shape: String,
    val addOns: List<PriceItem>,
    val date: String,
    val slot: String,
    val isHomeVisit: Boolean,
    val place: Place?,
    val distanceKm: Double,
    val inspoUrls: List<String>,
    val naturalUrls: List<String>,
    val price: Price,
    val status: BookingStatus,
    val ownerNote: String,
    val paymentRef: String,
    val createdAtMillis: Long,
) {
    val prettyDate: String
        get() = runCatching {
            LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))
        }.getOrDefault(date)

    val whatsappUrl: String
        get() {
            val digits = phone.filter { it.isDigit() }
            val full = if (digits.length == 10) "91$digits" else digits
            return "https://wa.me/$full"
        }

    companion object {
        fun from(doc: DocumentSnapshot): Booking {
            val d: Map<String, Any?> = doc.data ?: emptyMap()
            val loc = d["location"] as? Map<*, *>
            val p = d["price"] as? Map<*, *>
            return Booking(
                id = doc.id,
                customerUid = d.str("customerUid"),
                name = d.str("name"),
                phone = d.str("phone"),
                from = d.str("from"),
                instagram = d.str("instagram"),
                notes = d.str("notes"),
                extension = item(d["extension"]),
                length = item(d["length"]),
                shape = d.str("shape"),
                addOns = (d["addOns"] as? List<*>)?.mapNotNull { item(it) } ?: emptyList(),
                date = d.str("date"),
                slot = d.str("slot"),
                isHomeVisit = d.str("serviceMode") != "studio",
                place = loc?.let {
                    val lat = (it["lat"] as? Number)?.toDouble()
                    val lng = (it["lng"] as? Number)?.toDouble()
                    if (lat != null && lng != null) Place(lat, lng, it["address"] as? String ?: "") else null
                },
                distanceKm = (d["distanceKm"] as? Number)?.toDouble() ?: 0.0,
                inspoUrls = d.strings("inspoUrls"),
                naturalUrls = d.strings("naturalUrls"),
                price = Price(
                    base = p?.get("base").asInt(),
                    length = p?.get("length").asInt(),
                    addOns = p?.get("addOns").asInt(),
                    travel = p?.get("travel").asInt(),
                    total = p?.get("total").asInt(),
                    advance = p?.get("advance").asInt(),
                ),
                status = BookingStatus.from(d["status"] as? String),
                ownerNote = d.str("ownerNote"),
                paymentRef = d.str("paymentRef"),
                createdAtMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
            )
        }

        private fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: ""
        private fun Map<String, Any?>.strings(key: String): List<String> =
            (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        private fun Any?.asInt(): Int = (this as? Number)?.toInt() ?: 0
        private fun item(raw: Any?): PriceItem? {
            val m = raw as? Map<*, *> ?: return null
            return PriceItem(m["id"] as? String ?: "", m["name"] as? String ?: "", m["price"].asInt())
        }
    }
}

private val inr: NumberFormat = NumberFormat.getIntegerInstance(Locale("en", "IN"))
fun money(amount: Int, currency: String = "₹"): String = currency + inr.format(amount)
