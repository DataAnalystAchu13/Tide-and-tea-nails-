package com.tideandtea.owner.shop

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.OffsetDateTime

/* ---------------- JSON helpers (rows arrive as JsonObject) ---------------- */

fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull ?: ""
fun JsonObject.i(k: String): Int = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() } ?: 0
fun JsonObject.b(k: String, default: Boolean = false): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: default
fun JsonObject.list(k: String): List<String> = (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
fun JsonObject.obj(k: String): JsonObject = this[k] as? JsonObject ?: JsonObject(emptyMap())
fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

/** ISO timestamp from Postgres → epoch millis (0 when missing). */
fun JsonObject.millis(k: String): Long = s(k).takeIf { it.isNotBlank() }?.let {
    runCatching { OffsetDateTime.parse(it.replace(" ", "T")).toInstant().toEpochMilli() }.getOrNull()
} ?: 0L

/** Reads a value at a dotted path like "contact.phone". */
fun JsonObject.at(path: String): JsonElement? {
    var cur: JsonElement = this
    for (p in path.split('.')) cur = (cur as? JsonObject)?.get(p) ?: return null
    return cur
}

/** Returns a copy with the value at a dotted path replaced (creating objects on the way). */
fun JsonObject.with(path: String, value: JsonElement): JsonObject {
    val parts = path.split('.')
    fun set(o: JsonObject, i: Int): JsonObject {
        val m = o.toMutableMap()
        m[parts[i]] = if (i == parts.lastIndex) value else set(o[parts[i]] as? JsonObject ?: JsonObject(emptyMap()), i + 1)
        return JsonObject(m)
    }
    return set(this, 0)
}

/** Deep merge: values in [over] win; objects merge key by key. */
fun deepMerge(base: JsonObject, over: JsonObject?): JsonObject {
    if (over == null) return base
    val m = base.toMutableMap()
    for ((k, v) in over) {
        if (v is JsonNull) continue
        val b = m[k]
        m[k] = if (v is JsonObject && b is JsonObject) deepMerge(b, v) else v
    }
    return JsonObject(m)
}

fun String.toJson(): JsonElement = JsonPrimitive(this)
fun Int.toJson(): JsonElement = JsonPrimitive(this)
fun Boolean.toJson(): JsonElement = JsonPrimitive(this)
fun List<String>.toJsonArray(): JsonArray = JsonArray(map { JsonPrimitive(it) })

/** Lower-case URL slug, same rules as the website build. */
fun slugify(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFKD)
    .replace(Regex("[\\u0300-\\u036f]"), "")
    .replace("&", " and ")
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .take(70)
    .ifBlank { "item" }

fun rupees(n: Int): String = "₹" + java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(n)

/* ---------------- Orders ---------------- */

enum class OrderStatus(val key: String, val label: String) {
    PENDING_PAYMENT("pending_payment", "Awaiting payment"),
    PAYMENT_SUBMITTED("payment_submitted", "Check payment"),
    PAID("paid", "Paid · to pack"),
    PACKED("packed", "Packed"),
    SHIPPED("shipped", "Shipped"),
    DELIVERED("delivered", "Delivered"),
    CANCELLED("cancelled", "Cancelled");

    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } ?: PENDING_PAYMENT }
}

data class OrderLine(val productId: String, val name: String, val size: String, val qty: Int, val unit: Int, val price: Int, val amount: Int)

data class Order(
    val id: String,
    val code: String,
    val status: OrderStatus,
    val mode: String,
    val name: String,
    val phone: String,
    val email: String,
    val address: String,
    val notes: String,
    val lines: List<OrderLine>,
    val subtotal: Int,
    val discount: Int,
    val coupon: String,
    val shipping: Int,
    val total: Int,
    val paymentRef: String,
    val paymentMethod: String,
    val oversold: Boolean,
    val courier: String,
    val trackingNumber: String,
    val trackingUrl: String,
    val createdAt: Long,
    val paidAt: Long,
) {
    val itemCount get() = lines.sumOf { it.qty }
    /** Orders that need the owner to do something. */
    val needsAction get() = status in setOf(OrderStatus.PAYMENT_SUBMITTED, OrderStatus.PAID, OrderStatus.PACKED)

    companion object {
        fun from(r: JsonObject): Order {
            val c = r.obj("customer")
            return Order(
                id = r.s("id"), code = r.s("code"), status = OrderStatus.of(r.s("status")), mode = r.s("mode"),
                name = c.s("name"), phone = c.s("phone"), email = c.s("email"),
                address = listOf(c.s("line1"), c.s("line2"), c.s("city"), c.s("state"), c.s("pincode")).filter { it.isNotBlank() }.joinToString(", "),
                notes = r.s("notes"),
                lines = r.arr("lines").map { OrderLine(it.s("productId"), it.s("name"), it.s("size"), it.i("qty"), it.i("unit"), it.i("price"), it.i("amount")) },
                subtotal = r.i("subtotal"), discount = r.i("discount"), coupon = r.s("coupon"), shipping = r.i("shipping"), total = r.i("total"),
                paymentRef = r.s("payment_ref"), paymentMethod = r.s("payment_method"), oversold = r.b("oversold"),
                courier = r.s("courier"), trackingNumber = r.s("tracking_number"), trackingUrl = r.s("tracking_url"),
                createdAt = r.millis("created_at"), paidAt = r.millis("paid_at"),
            )
        }
    }
}

/* ---------------- Catalogue and content ---------------- */

data class Product(
    val id: String? = null,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val price: Int = 0,
    val salePrice: Int = 0,
    val stock: Int = 0,
    val sold: Int = 0,
    val madeToOrder: Boolean = false,
    val excludeFromSale: Boolean = false,
    val shape: String = "",
    val length: String = "",
    val tags: List<String> = emptyList(),
    val sizes: List<String> = listOf("S", "M", "L"),
    val images: List<String> = emptyList(),
    val featured: Boolean = false,
    val visible: Boolean = true,
    val sort: Int = 100,
) {
    val soldOut get() = !madeToOrder && stock <= 0
    val percentOff get() = if (salePrice in 1 until price) ((price - salePrice) * 100.0 / price).toInt() else 0

    fun toJson(): JsonObject = buildJsonObject {
        if (id != null) put("id", id)
        put("slug", slug.ifBlank { slugify(name) })
        put("name", name.trim())
        put("description", description.trim())
        put("price", price); put("sale_price", salePrice); put("stock", stock)
        put("made_to_order", madeToOrder); put("exclude_from_sale", excludeFromSale)
        put("shape", shape.trim()); put("length", length.trim())
        putJsonArray("tags") { tags.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("sizes") { sizes.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("images") { images.forEach { add(JsonPrimitive(it)) } }
        put("featured", featured); put("visible", visible); put("sort", sort)
    }

    companion object {
        fun from(r: JsonObject) = Product(
            id = r.s("id"), slug = r.s("slug"), name = r.s("name"), description = r.s("description"), price = r.i("price"),
            salePrice = r.i("sale_price"), stock = r.i("stock"), sold = r.i("sold"), madeToOrder = r.b("made_to_order"),
            excludeFromSale = r.b("exclude_from_sale"), shape = r.s("shape"), length = r.s("length"), tags = r.list("tags"),
            sizes = r.list("sizes"), images = r.list("images"), featured = r.b("featured"), visible = r.b("visible", true), sort = r.i("sort"),
        )
    }
}

data class Post(
    val id: String? = null,
    val slug: String = "",
    val title: String = "",
    val excerpt: String = "",
    val body: String = "",
    val cover: String = "",
    val published: Boolean = false,
    val publishedAt: Long = 0,
) {
    fun toJson(): JsonObject = buildJsonObject {
        if (id != null) put("id", id)
        put("slug", slug.ifBlank { slugify(title) })
        put("title", title.trim()); put("excerpt", excerpt.trim()); put("body", body.trim()); put("cover", cover)
        put("status", if (published) "published" else "draft")
        if (published) put("published_at", java.time.Instant.ofEpochMilli(if (publishedAt > 0) publishedAt else System.currentTimeMillis()).toString())
    }

    companion object {
        fun from(r: JsonObject) = Post(r.s("id"), r.s("slug"), r.s("title"), r.s("excerpt"), r.s("body"), r.s("cover"), r.s("status") == "published", r.millis("published_at"))
    }
}

data class Review(
    val id: String? = null,
    val name: String = "",
    val area: String = "",
    val rating: Int = 5,
    val text: String = "",
    val images: List<String> = emptyList(),
    val featured: Boolean = false,
    val visible: Boolean = true,
) {
    fun toJson(): JsonObject = buildJsonObject {
        if (id != null) put("id", id)
        put("name", name.trim()); put("area", area.trim()); put("rating", rating.coerceIn(1, 5)); put("text", text.trim())
        putJsonArray("images") { images.forEach { add(JsonPrimitive(it)) } }
        put("featured", featured); put("visible", visible)
    }

    companion object {
        fun from(r: JsonObject) = Review(r.s("id"), r.s("name"), r.s("area"), r.i("rating").takeIf { it > 0 } ?: 5, r.s("text"), r.list("images"), r.b("featured"), r.b("visible", true))
    }
}

data class Coupon(
    val code: String = "",
    val percent: Boolean = true,
    val value: Int = 10,
    val minOrder: Int = 0,
    val maxDiscount: Int = 0,
    val maxUses: Int = 0,
    val uses: Int = 0,
    val expiresAt: String = "",
    val active: Boolean = true,
    val note: String = "",
) {
    val summary: String get() = buildString {
        append(if (percent) "$value% off" else "${rupees(value)} off")
        if (minOrder > 0) append(" over ${rupees(minOrder)}")
        if (maxDiscount > 0 && percent) append(", up to ${rupees(maxDiscount)}")
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("code", code.uppercase().replace(Regex("[^A-Z0-9]"), ""))
        put("type", if (percent) "percent" else "flat"); put("value", value)
        put("min_order", minOrder); put("max_discount", maxDiscount); put("max_uses", maxUses)
        if (expiresAt.isNotBlank()) put("expires_at", expiresAt) else put("expires_at", JsonNull)
        put("active", active); put("note", note)
    }

    companion object {
        fun from(r: JsonObject) = Coupon(
            r.s("code"), r.s("type") != "flat", r.i("value"), r.i("min_order"), r.i("max_discount"), r.i("max_uses"), r.i("uses"),
            r.s("expires_at"), r.b("active", true), r.s("note"),
        )
    }
}

/** Reads a JSON array of strings at a path, used by the generic content editor. */
fun JsonElement?.strings(): List<String> = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
fun JsonElement?.text(): String = (this as? JsonPrimitive)?.contentOrNull ?: ""
fun JsonElement?.number(): Long = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() } ?: 0
fun JsonElement?.flag(): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: false
