package com.tideandtea.owner.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mirrors public.bookings in Supabase (see supabase/migrations). */
enum class BookingStatus(val key: String, val label: String) {
    REQUESTED("requested", "Not paid yet"),
    APPROVED("approved", "Awaiting advance"),
    PAYMENT_SUBMITTED("payment_submitted", "Paid · confirm"),
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
        fun from(r: JsonObject): Booking {
            val loc = r.obj("location")
            val p = r.obj("price")
            return Booking(
                id = r.str("id"),
                customerUid = r.str("customer_uid"),
                name = r.str("name"),
                phone = r.str("phone"),
                from = r.str("from_place"),
                instagram = r.str("instagram"),
                notes = r.str("notes"),
                extension = r["extension"]?.let(::item),
                length = r["length"]?.let(::item),
                shape = r.str("shape"),
                addOns = r.list("add_ons").mapNotNull(::item),
                date = r.str("date"),
                slot = r.str("slot"),
                isHomeVisit = r.str("service_mode") == "home",
                place = loc?.let {
                    val lat = it.strOrNull("lat")?.toDoubleOrNull()
                    val lng = it.strOrNull("lng")?.toDoubleOrNull()
                    if (lat != null && lng != null) Place(lat, lng, it.str("address")) else null
                },
                distanceKm = r.double("distance_km"),
                inspoUrls = r.strings("inspo_urls"),
                naturalUrls = r.strings("natural_urls"),
                price = Price(
                    base = p?.int("base") ?: 0,
                    length = p?.int("length") ?: 0,
                    addOns = p?.int("addOns") ?: 0,
                    travel = p?.int("travel") ?: 0,
                    total = p?.int("total") ?: 0,
                    advance = p?.int("advance") ?: 0,
                ),
                status = BookingStatus.from(r.strOrNull("status")),
                ownerNote = r.str("owner_note"),
                paymentRef = r.str("payment_ref"),
                createdAtMillis = r.millis("created_at"),
            )
        }

        private fun item(raw: JsonElement): PriceItem? {
            val m = raw.asObjectOrNull() ?: return null
            return PriceItem(m.str("id"), m.str("name"), m.int("price"))
        }
    }
}

private val inr: NumberFormat = NumberFormat.getIntegerInstance(Locale("en", "IN"))
fun money(amount: Int, currency: String = "₹"): String = currency + inr.format(amount)
