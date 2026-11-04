package com.tideandtea.owner.shop

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** State for the Orders and Website tabs. Start it with [start] once the owner is signed in. */
class ShopViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ShopRepository
    private val ctx get() = getApplication<Application>()
    private val prefs = app.getSharedPreferences("shop", 0)

    private val _orders = MutableStateFlow<List<Order>?>(null)
    val orders: StateFlow<List<Order>?> = _orders.asStateFlow()
    private val _products = MutableStateFlow<List<Product>?>(null)
    val products: StateFlow<List<Product>?> = _products.asStateFlow()
    private val _posts = MutableStateFlow<List<Post>?>(null)
    val posts: StateFlow<List<Post>?> = _posts.asStateFlow()
    private val _reviews = MutableStateFlow<List<Review>?>(null)
    val reviews: StateFlow<List<Review>?> = _reviews.asStateFlow()
    private val _coupons = MutableStateFlow<List<Coupon>?>(null)
    val coupons: StateFlow<List<Coupon>?> = _coupons.asStateFlow()
    private val _site = MutableStateFlow<JsonObject?>(null)
    val site: StateFlow<JsonObject?> = _site.asStateFlow()
    private val _prices = MutableStateFlow<JsonObject?>(null)
    val prices: StateFlow<JsonObject?> = _prices.asStateFlow()

    /** One-line messages for the snackbar. */
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var uploading by mutableStateOf(0)
        private set
    /** Content saved since the last Publish (kept across app restarts). */
    var unpublished by mutableStateOf(prefs.getBoolean("unpublished", false))
        private set
    var lastPublished by mutableStateOf(0L)
        private set

    private var started = false
    private var ordersJob: Job? = null
    private var pollJob: Job? = null

    fun start() {
        if (started) return
        started = true
        ordersJob = viewModelScope.launch {
            runCatching { repo.liveOrders().collect { _orders.value = it } }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) { Log.w("Shop", "Live order updates unavailable", it); refreshOrders() } }
        }
        // Fallback refresh in case live updates drop.
        pollJob = viewModelScope.launch { while (true) { delay(120_000); refreshOrders() } }
        loadWebsite()
    }

    fun stop() {
        ordersJob?.cancel()
        pollJob?.cancel()
        started = false
        _orders.value = null
    }

    fun refreshOrders() = viewModelScope.launch {
        runCatching { repo.orders() }.onSuccess { _orders.value = it }.onFailure { fail("Couldn't load orders", it) }
    }

    fun loadWebsite() = viewModelScope.launch {
        runCatching { _site.value = repo.site(ctx) }.onFailure { fail("Couldn't load website content", it) }
        runCatching { _prices.value = repo.prices(ctx) }.onFailure { fail("Couldn't load prices", it) }
        runCatching { _products.value = repo.products() }.onFailure { fail("Couldn't load products", it) }
        runCatching { _posts.value = repo.posts() }.onFailure { fail("Couldn't load posts", it) }
        runCatching { _reviews.value = repo.reviews() }.onFailure { fail("Couldn't load reviews", it) }
        runCatching { _coupons.value = repo.coupons() }.onFailure { fail("Couldn't load coupons", it) }
        lastPublished = repo.lastPublished()
    }

    /* ---------------- Orders ---------------- */

    fun confirmPayment(o: Order, method: String, done: () -> Unit = {}) = act("Payment confirmed. Stock updated.", done) {
        repo.confirmPayment(o.id, method); refreshOrders()
    }

    fun rejectPayment(o: Order) = act("Moved back to awaiting payment.") {
        repo.updateOrder(o.id, buildJsonObject { put("status", "pending_payment"); put("payment_ref", "") }); refreshOrders()
    }

    fun setStatus(o: Order, s: OrderStatus, done: () -> Unit = {}) = act("Marked ${s.label.lowercase()}.", done) {
        repo.updateOrder(o.id, buildJsonObject { put("status", s.key) }); refreshOrders()
    }

    fun ship(o: Order, courier: String, tracking: String, url: String, done: () -> Unit = {}) = act("Marked shipped. The customer sees the tracking.", done) {
        repo.updateOrder(o.id, buildJsonObject {
            put("status", "shipped"); put("courier", courier.trim()); put("tracking_number", tracking.trim())
            put("tracking_url", url.trim().let { if (it.isNotBlank() && !it.startsWith("http")) "https://$it" else it })
            put("shipped_at", java.time.Instant.now().toString())
        })
        refreshOrders()
    }

    fun saveOrderNote(o: Order, note: String) = act("Note saved.") {
        repo.updateOrder(o.id, buildJsonObject { put("owner_note", note.take(1000)) })
    }

    /* ---------------- Website content ---------------- */

    fun saveProduct(p: Product, done: (Product) -> Unit = {}) = act("Saved. Tap Publish to update the website.") {
        val saved = repo.saveProduct(p)
        _products.value = (_products.value.orEmpty().filterNot { it.id == saved.id } + saved).sortedBy { it.sort }
        markUnpublished(); done(saved)
    }

    fun deleteProduct(p: Product, done: () -> Unit = {}) = act("Product deleted.", done) {
        repo.deleteProduct(p.id ?: return@act); _products.value = _products.value.orEmpty().filterNot { it.id == p.id }; markUnpublished()
    }

    /** Quick toggle from the list without opening the editor. */
    fun setVisible(p: Product, visible: Boolean) = saveProduct(p.copy(visible = visible))

    fun savePost(p: Post, done: (Post) -> Unit = {}) = act(if (p.published) "Saved. Tap Publish to put it on the website." else "Draft saved.") {
        val saved = repo.savePost(p)
        _posts.value = listOf(saved) + _posts.value.orEmpty().filterNot { it.id == saved.id }
        if (p.published) markUnpublished(); done(saved)
    }

    fun deletePost(p: Post, done: () -> Unit = {}) = act("Post deleted.", done) {
        repo.deletePost(p.id ?: return@act); _posts.value = _posts.value.orEmpty().filterNot { it.id == p.id }; markUnpublished()
    }

    fun saveReview(r: Review, done: (Review) -> Unit = {}) = act("Saved. Tap Publish to update the website.") {
        val saved = repo.saveReview(r)
        _reviews.value = listOf(saved) + _reviews.value.orEmpty().filterNot { it.id == saved.id }
        markUnpublished(); done(saved)
    }

    fun deleteReview(r: Review, done: () -> Unit = {}) = act("Review deleted.", done) {
        repo.deleteReview(r.id ?: return@act); _reviews.value = _reviews.value.orEmpty().filterNot { it.id == r.id }; markUnpublished()
    }

    fun saveCoupon(c: Coupon, done: () -> Unit = {}) = act("Coupon ${c.code.uppercase()} saved. It works at checkout right away.", done) {
        repo.saveCoupon(c); _coupons.value = repo.coupons()
    }

    fun deleteCoupon(c: Coupon) = act("Coupon deleted.") {
        repo.deleteCoupon(c.code); _coupons.value = _coupons.value.orEmpty().filterNot { it.code == c.code }
    }

    fun saveSite(data: JsonObject, done: () -> Unit = {}) = act("Saved. Tap Publish to update the website.", done) {
        repo.saveSite(data); _site.value = data; markUnpublished()
    }

    fun savePrices(data: JsonObject, done: () -> Unit = {}) = act("Prices saved. Bookings use them right away.", done) {
        repo.savePrices(data); _prices.value = data; markUnpublished()
    }

    fun publish() = viewModelScope.launch {
        if (busy) return@launch
        busy = true
        runCatching { repo.publish() }
            .onSuccess { msg ->
                message = msg
                if (msg.startsWith("Publishing")) { setUnpublished(false); lastPublished = System.currentTimeMillis() }
            }
            .onFailure { fail("Couldn't publish", it) }
        busy = false
    }

    /** Uploads picked photos one by one and hands back their public URLs. */
    fun upload(uris: List<Uri>, folder: String, done: (List<String>) -> Unit) = viewModelScope.launch {
        uploading += uris.size
        val urls = mutableListOf<String>()
        for (u in uris) {
            runCatching { repo.uploadPhoto(ctx, u, folder) }
                .onSuccess { urls += it }
                .onFailure { fail("A photo didn't upload", it) }
            uploading -= 1
        }
        done(urls)
    }

    /* ---------------- helpers ---------------- */

    private fun markUnpublished() = setUnpublished(true)
    private fun setUnpublished(v: Boolean) { unpublished = v; prefs.edit().putBoolean("unpublished", v).apply() }

    private fun act(success: String, done: () -> Unit = {}, block: suspend () -> Unit) = viewModelScope.launch {
        if (busy) return@launch
        busy = true
        runCatching { block() }
            .onSuccess { message = success; done() }
            .onFailure { fail("That didn't save", it) }
        busy = false
    }

    private fun fail(what: String, e: Throwable) {
        Log.w("Shop", what, e)
        val detail = e.message?.takeIf { it.length in 1..120 && !it.contains("Exception") }
        message = if (detail != null) "$what: $detail" else "$what. Check your connection and try again."
    }
}
