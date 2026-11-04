package com.tideandtea.owner.shop

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Reviews
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tideandtea.owner.ui.components.Section
import com.tideandtea.owner.ui.theme.Danger
import com.tideandtea.owner.ui.theme.Gold
import com.tideandtea.owner.ui.theme.Lilac
import com.tideandtea.owner.ui.theme.Ok
import com.tideandtea.owner.ui.theme.PinkDeep
import com.tideandtea.owner.ui.theme.Royal
import kotlinx.serialization.json.JsonPrimitive

/* ====================================================================== */
/* Website hub                                                            */
/* ====================================================================== */

@Composable
fun WebsiteScreen(vm: ShopViewModel, snackbar: SnackbarHostState, go: (String) -> Unit) {
    val site by vm.site.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val posts by vm.posts.collectAsStateWithLifecycle()
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    val coupons by vm.coupons.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val siteUrl = site?.at("seo.siteUrl").text().ifBlank { "https://tideandteanails.in" }
    val saleOn = site?.at("sale.enabled").flag() == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ShopTopBar("Website", actions = {
                IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl))) }) { Icon(Icons.Outlined.OpenInNew, "Open website") }
            })
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PublishCard(vm) }
            item {
                Section("Shop") {
                    HubRow(Icons.Outlined.ShoppingBag, "Press-on products", products?.let { p -> "${p.count { it.visible }} on sale · ${p.count { it.soldOut && it.visible }} sold out" } ?: "Loading…") { go("web/products") }
                    HubDivider()
                    HubRow(Icons.Outlined.LocalOffer, "Sale and coupons",
                        listOfNotNull(if (saleOn) "Sale on: ${site?.at("sale.percent").number()}% off" else "No sale running", coupons?.count { it.active }?.let { "$it coupons" }).joinToString(" · ")) { go("web/discounts") }
                    HubDivider()
                    val ship = editPage("shipping")!!
                    HubRow(ship.icon, ship.title, ship.sub) { go("web/page/shipping") }
                }
            }
            item {
                Section("Bookings") {
                    PRICE_PAGES.forEachIndexed { i, p ->
                        if (i > 0) HubDivider()
                        HubRow(p.icon, p.title, p.sub) { go("web/page/${p.key}") }
                    }
                    Text("Price changes reach the booking page within a minute. No need to publish.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Section("Content") {
                    HubRow(Icons.AutoMirrored.Outlined.Article, "Blog", posts?.let { p -> "${p.count { it.published }} published · ${p.count { !it.published }} drafts" } ?: "Loading…") { go("web/posts") }
                    HubDivider()
                    HubRow(Icons.Outlined.Reviews, "Reviews", reviews?.let { r -> "${r.count { it.visible }} on the website" } ?: "Loading…") { go("web/reviews") }
                    SITE_PAGES.filter { it.key != "shipping" }.forEach { p ->
                        HubDivider()
                        HubRow(p.icon, p.title, p.sub) { go("web/page/${p.key}") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PublishCard(vm: ShopViewModel) {
    val pending = vm.unpublished
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(if (pending) Royal else MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val fg = if (pending) Color.White else MaterialTheme.colorScheme.onSurface
        Text(if (pending) "You have changes to publish" else "Website is up to date", style = MaterialTheme.typography.titleLarge, color = fg)
        Text(
            if (pending) "Products, pages and blog posts go live when you publish. It takes about 2 minutes."
            else if (vm.lastPublished > 0) "Last published ${ago(vm.lastPublished)}." else "Edits you save here go live when you tap Publish.",
            style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.8f),
        )
        Button(
            onClick = { vm.publish() }, enabled = !vm.busy, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = if (pending) Color.White else Royal, contentColor = if (pending) Royal else Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Icon(Icons.Outlined.CloudUpload, null, Modifier.size(20.dp)); Spacer(Modifier.size(8.dp))
            Text(if (vm.busy) "Working…" else "Publish website", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Lilac), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Royal, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HubDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outline)

/** A generic page editor (site content or price list). */
@Composable
fun PageEditorScreen(vm: ShopViewModel, key: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val page = editPage(key) ?: return
    val src by (if (page.prices) vm.prices else vm.site).collectAsStateWithLifecycle()
    JsonFormScreen(page.title, src, page.sections, vm, snackbar, folder = "pages", onBack = onBack) { data, _ ->
        if (page.prices) vm.savePrices(data) else vm.saveSite(data)
    }
}

/* ====================================================================== */
/* Products                                                               */
/* ====================================================================== */

@Composable
fun ProductsScreen(vm: ShopViewModel, snackbar: SnackbarHostState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val products by vm.products.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ShopTopBar("Press-on products", onBack = onBack) },
        floatingActionButton = { AddFab("New product") { onOpen("new") } },
    ) { pad ->
        val list = products
        when {
            list == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(pad), color = Royal)
            list.isEmpty() -> Empty(pad, "No products yet", "Add your first press-on set with photos, price and sizes.")
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id ?: it.name }) { p -> ProductRow(p, onToggle = { vm.setVisible(p, it) }) { onOpen(p.id ?: "new") } }
            }
        }
    }
}

@Composable
private fun ProductRow(p: Product, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(p.images.firstOrNull())
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.percentOff > 0) {
                    Text(rupees(p.salePrice), style = MaterialTheme.typography.bodyMedium, color = PinkDeep)
                    Text(rupees(p.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                } else Text(rupees(p.price), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                when { p.madeToOrder -> "Made to order"; p.soldOut -> "Sold out"; else -> "${p.stock} in stock" } + if (p.featured) " · Featured" else "",
                style = MaterialTheme.typography.labelMedium, color = if (p.soldOut) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = p.visible, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Royal))
    }
}

@Composable
fun ProductEditorScreen(vm: ShopViewModel, id: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val products by vm.products.collectAsStateWithLifecycle()
    val source = remember(products, id) { if (id == "new") Product() else products?.firstOrNull { it.id == id } }
    var p by remember(source) { mutableStateOf(source) }
    var askDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        title = if (id == "new") "New product" else "Edit product",
        dirty = p != source, busy = vm.busy, snackbar = snackbar, onBack = onBack,
        onDelete = if (id != "new") ({ askDelete = true }) else null,
        onUndo = { p = source },
        onSave = {
            val cur = p ?: return@EditorScaffold
            when {
                cur.name.isBlank() -> vm.message = "Give the product a name."
                cur.price <= 0 -> vm.message = "Add a price."
                cur.salePrice >= cur.price -> vm.message = "The sale price must be lower than the price, or 0 for no sale."
                cur.images.isEmpty() -> vm.message = "Add at least one photo."
                else -> vm.saveProduct(cur) { if (id == "new") onBack() }
            }
        },
    ) {
        val cur = p ?: return@EditorScaffold
        fun up(f: Product.() -> Product) { p = cur.f() }
        Section("Photos") {
            PhotoStrip(cur.images, uploading = vm.uploading > 0, max = 8,
                onAdd = { uris -> vm.upload(uris, "products") { urls -> p = (p ?: cur).let { it.copy(images = it.images + urls) } } },
                onChange = { up { copy(images = it) } })
            Text("The first photo is the cover. Square photos look best.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("Details") {
            Field("Name", cur.name, { up { copy(name = it) } }, hint = "Lilac almond chrome set")
            Field("Description", cur.description, { up { copy(description = it) } }, lines = 4, hint = "What's in the set, finish, how many wears")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Shape", cur.shape, { up { copy(shape = it) } }, Modifier.weight(1f), hint = "Almond")
                Field("Length", cur.length, { up { copy(length = it) } }, Modifier.weight(1f), hint = "Medium")
            }
            Field("Tags", cur.tags.joinToString(", "), { up { copy(tags = it.split(',').map(String::trim).filter(String::isNotEmpty)) } }, hint = "bridal, chrome, pastel")
        }
        Section("Price") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Price", cur.price.takeIf { it > 0 }?.toString().orEmpty(), { up { copy(price = it.toIntOrNull() ?: 0) } }, Modifier.weight(1f), number = true, prefix = "₹ ")
                Field("Sale price", cur.salePrice.takeIf { it > 0 }?.toString().orEmpty(), { up { copy(salePrice = it.toIntOrNull() ?: 0) } }, Modifier.weight(1f), number = true, prefix = "₹ ", hint = "Optional")
            }
            if (cur.percentOff > 0) Text("Shows as ${cur.percentOff}% off.", style = MaterialTheme.typography.bodySmall, color = PinkDeep)
            ToggleRow("Leave out of site-wide sales", "Keep this price when you run a sale for everything", cur.excludeFromSale) { up { copy(excludeFromSale = it) } }
        }
        Section("Stock and sizes") {
            ToggleRow("Made to order", "Always available, no stock count", cur.madeToOrder) { up { copy(madeToOrder = it) } }
            if (!cur.madeToOrder) Field("Sets in stock", cur.stock.toString(), { up { copy(stock = it.toIntOrNull() ?: 0) } }, number = true)
            if (cur.sold > 0) Text("${cur.sold} sold so far.", style = MaterialTheme.typography.bodySmall, color = Ok)
            Field("Sizes", cur.sizes.joinToString(", "), { up { copy(sizes = it.split(',').map(String::trim).filter(String::isNotEmpty)) } }, hint = "S, M, L, Custom")
            Text("Leave sizes empty if the set is one size.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("On the website") {
            ToggleRow("Show on the website", null, cur.visible) { up { copy(visible = it) } }
            ToggleRow("Feature on the home page", null, cur.featured) { up { copy(featured = it) } }
            Field("Order in the list", cur.sort.toString(), { up { copy(sort = it.toIntOrNull() ?: 100) } }, number = true, hint = "Lower numbers show first")
        }
    }
    if (askDelete) ConfirmDelete("Delete this product?", "It disappears from the website after you publish. Past orders keep their details.",
        onDismiss = { askDelete = false }) { askDelete = false; source?.let { vm.deleteProduct(it) { onBack() } } }
}

/* ====================================================================== */
/* Sale and coupons                                                       */
/* ====================================================================== */

@Composable
fun DiscountsScreen(vm: ShopViewModel, snackbar: SnackbarHostState, onBack: () -> Unit, onCoupon: (String) -> Unit) {
    val site by vm.site.collectAsStateWithLifecycle()
    val coupons by vm.coupons.collectAsStateWithLifecycle()
    val source = site
    var sale by remember(source) { mutableStateOf(source?.at("sale")) }
    val dirty = sale != source?.at("sale")

    EditorScaffold(
        title = "Sale and coupons", dirty = dirty, busy = vm.busy, snackbar = snackbar, onBack = onBack,
        onUndo = { sale = source?.at("sale") },
        onSave = {
            val s = sale as? kotlinx.serialization.json.JsonObject ?: return@EditorScaffold
            val pct = s.at("percent").number()
            if (s.at("enabled").flag() && pct !in 1..90) { vm.message = "Pick a sale between 1% and 90%."; return@EditorScaffold }
            source?.let { vm.saveSite(it.with("sale", s)) }
        },
        fab = { AddFab("New coupon") { onCoupon("new") } },
    ) {
        val s = sale as? kotlinx.serialization.json.JsonObject
        if (s == null) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Royal); return@EditorScaffold }
        fun set(k: String, v: kotlinx.serialization.json.JsonElement) { sale = s.with(k, v) }
        Section("Site-wide sale") {
            ToggleRow("Sale is on", "Takes a percentage off every press-on set", s.at("enabled").flag()) { set("enabled", JsonPrimitive(it)) }
            Field("Sale name", s.at("label").text(), { set("label", JsonPrimitive(it)) }, hint = "Festive sale")
            Field("Percent off", s.at("percent").number().takeIf { it > 0 }?.toString().orEmpty(), { set("percent", JsonPrimitive(it.toIntOrNull() ?: 0)) }, number = true, prefix = "% ")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Starts", s.at("startsAt").text(), { set("startsAt", JsonPrimitive(it)) }, Modifier.weight(1f), hint = "2026-11-10")
                Field("Ends", s.at("endsAt").text(), { set("endsAt", JsonPrimitive(it)) }, Modifier.weight(1f), hint = "2026-11-15")
            }
            Text("Dates are optional (year-month-day). The sale runs through the whole end date. A product's own sale price wins if it's lower. Publish after saving so the banner and prices update.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("Coupon codes") {
            Text("Coupons work at checkout as soon as you save them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val list = coupons.orEmpty()
            if (list.isEmpty()) Text("No coupons yet. Tap New coupon.", style = MaterialTheme.typography.bodyMedium)
            list.forEachIndexed { i, c ->
                if (i > 0) HubDivider()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onCoupon(c.code) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.code, style = MaterialTheme.typography.titleSmall, color = if (c.active) Royal else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listOfNotNull(c.summary, if (c.maxUses > 0) "${c.uses}/${c.maxUses} used" else "${c.uses} used", c.expiresAt.takeIf { it.isNotBlank() }?.let { "until ${it.take(10)}" })
                            .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!c.active) Pill("Off", orderColors(OrderStatus.CANCELLED))
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CouponEditorScreen(vm: ShopViewModel, code: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val coupons by vm.coupons.collectAsStateWithLifecycle()
    val isNew = code == "new"
    val source = remember(coupons, code) { if (isNew) Coupon() else coupons?.firstOrNull { it.code == code } }
    var c by remember(source) { mutableStateOf(source) }
    var askDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        title = if (isNew) "New coupon" else code, dirty = c != source, busy = vm.busy, snackbar = snackbar, onBack = onBack,
        onDelete = if (!isNew) ({ askDelete = true }) else null,
        onUndo = { c = source },
        onSave = {
            val cur = c ?: return@EditorScaffold
            val clean = cur.code.uppercase().replace(Regex("[^A-Z0-9]"), "")
            when {
                clean.length !in 3..20 -> vm.message = "Use 3 to 20 letters or numbers for the code."
                isNew && coupons.orEmpty().any { it.code == clean } -> vm.message = "$clean already exists."
                cur.value <= 0 -> vm.message = "Add the discount amount."
                cur.percent && cur.value > 90 -> vm.message = "Keep percent coupons at 90% or less."
                cur.expiresAt.isNotBlank() && !Regex("""\d{4}-\d{2}-\d{2}.*""").matches(cur.expiresAt) -> vm.message = "Write the end date as year-month-day, like 2026-12-31."
                else -> vm.saveCoupon(cur.copy(code = clean)) { onBack() }
            }
        },
    ) {
        val cur = c ?: return@EditorScaffold
        fun up(f: Coupon.() -> Coupon) { c = cur.f() }
        Section("Coupon") {
            Field("Code", cur.code, { up { copy(code = it.uppercase()) } }, caps = true, hint = "INSTA10")
            if (!isNew) Text("To rename a coupon, create a new one and switch this one off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(true to "Percent off", false to "Rupees off").forEachIndexed { i, (v, label) ->
                    SegmentedButton(selected = cur.percent == v, onClick = { up { copy(percent = v) } }, shape = SegmentedButtonDefaults.itemShape(i, 2),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = Lilac, activeContentColor = Royal)) { Text(label) }
                }
            }
            Field(if (cur.percent) "Percent off" else "Amount off", cur.value.takeIf { it > 0 }?.toString().orEmpty(), { up { copy(value = it.toIntOrNull() ?: 0) } },
                number = true, prefix = if (cur.percent) "% " else "₹ ")
            Field("Note for you", cur.note, { up { copy(note = it) } }, hint = "Instagram followers")
        }
        Section("Limits") {
            Field("Minimum order", cur.minOrder.takeIf { it > 0 }?.toString().orEmpty(), { up { copy(minOrder = it.toIntOrNull() ?: 0) } }, number = true, prefix = "₹ ", hint = "No minimum")
            if (cur.percent) Field("Biggest discount", cur.maxDiscount.takeIf { it > 0 }?.toString().orEmpty(), { up { copy(maxDiscount = it.toIntOrNull() ?: 0) } }, number = true, prefix = "₹ ", hint = "No cap")
            Field("Total uses allowed", cur.maxUses.takeIf { it > 0 }?.toString().orEmpty(), { up { copy(maxUses = it.toIntOrNull() ?: 0) } }, number = true, hint = "Unlimited")
            Field("Last day", cur.expiresAt.take(10), { up { copy(expiresAt = it) } }, hint = "2026-12-31 (optional)")
            ToggleRow("Coupon is on", null, cur.active) { up { copy(active = it) } }
            if (!isNew) Text("Used ${cur.uses} times.", style = MaterialTheme.typography.bodySmall, color = Ok)
        }
    }
    if (askDelete) ConfirmDelete("Delete $code?", "Customers can't use it any more. To pause it instead, switch it off.",
        onDismiss = { askDelete = false }) { askDelete = false; source?.let { vm.deleteCoupon(it); onBack() } }
}

/* ====================================================================== */
/* Blog                                                                   */
/* ====================================================================== */

@Composable
fun PostsScreen(vm: ShopViewModel, snackbar: SnackbarHostState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val posts by vm.posts.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ShopTopBar("Blog", onBack = onBack) },
        floatingActionButton = { AddFab("New post") { onOpen("new") } },
    ) { pad ->
        val list = posts
        when {
            list == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(pad), color = Royal)
            list.isEmpty() -> Empty(pad, "No posts yet", "Posts like \"How long do gel extensions last?\" help people in Chennai find you on Google.")
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id ?: it.title }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable { onOpen(p.id ?: "new") }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(p.cover.takeIf { it.isNotBlank() })
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (p.published) "Published ${if (p.publishedAt > 0) ago(p.publishedAt) else ""}" else "Draft",
                                style = MaterialTheme.typography.labelMedium, color = if (p.published) Ok else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostEditorScreen(vm: ShopViewModel, id: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val posts by vm.posts.collectAsStateWithLifecycle()
    val source = remember(posts, id) { if (id == "new") Post() else posts?.firstOrNull { it.id == id } }
    var p by remember(source) { mutableStateOf(source) }
    var askDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        title = if (id == "new") "New post" else "Edit post", dirty = p != source, busy = vm.busy, snackbar = snackbar, onBack = onBack,
        onDelete = if (id != "new") ({ askDelete = true }) else null,
        onUndo = { p = source },
        onSave = {
            val cur = p ?: return@EditorScaffold
            when {
                cur.title.isBlank() -> vm.message = "Give the post a title."
                cur.published && cur.body.length < 200 -> vm.message = "Write a little more before publishing: at least a few paragraphs."
                else -> vm.savePost(cur) { if (id == "new") onBack() }
            }
        },
    ) {
        val cur = p ?: return@EditorScaffold
        fun up(f: Post.() -> Post) { p = cur.f() }
        Section("Cover photo") {
            PhotoStrip(listOfNotNull(cur.cover.takeIf { it.isNotBlank() }), uploading = vm.uploading > 0, max = 1,
                onAdd = { uris -> vm.upload(uris.take(1), "posts") { urls -> urls.firstOrNull()?.let { u -> p = (p ?: cur).copy(cover = u) } } },
                onChange = { up { copy(cover = it.firstOrNull().orEmpty()) } })
        }
        Section("Post") {
            Field("Title", cur.title, { up { copy(title = it) } }, lines = 2, hint = "Gel-X vs acrylic: which lasts longer in Chennai's heat?")
            Field("Summary", cur.excerpt, { up { copy(excerpt = it) } }, lines = 2, hint = "One or two sentences. Shown in the blog list and on Google.")
            Field("Post", cur.body, { up { copy(body = it) } }, lines = 12)
            Text("Tip: start a line with ## for a heading, - for a bullet. *italic* and **bold** work. Mention your area and the service so Google can match the post to searches.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("Publishing") {
            ToggleRow("Published", "Off keeps it as a draft only you can see", cur.published) { up { copy(published = it) } }
        }
    }
    if (askDelete) ConfirmDelete("Delete this post?", "It comes off the website the next time you publish.",
        onDismiss = { askDelete = false }) { askDelete = false; source?.let { vm.deletePost(it) { onBack() } } }
}

/* ====================================================================== */
/* Reviews                                                                */
/* ====================================================================== */

@Composable
fun ReviewsScreen(vm: ShopViewModel, snackbar: SnackbarHostState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ShopTopBar("Reviews", onBack = onBack) },
        floatingActionButton = { AddFab("Add review") { onOpen("new") } },
    ) { pad ->
        val list = reviews
        when {
            list == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(pad), color = Royal)
            list.isEmpty() -> Empty(pad, "No reviews yet", "Add kind words from clients, with their permission. Copy them from Google, Instagram or WhatsApp.")
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id ?: it.name }) { r ->
                    Column(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable { onOpen(r.id ?: "new") }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name.ifBlank { "Client" } + if (r.area.isNotBlank()) " · ${r.area}" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Stars(r.rating, size = 16)
                        }
                        Text(r.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (!r.visible) Text("Hidden", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (r.featured) Text("On the home page", style = MaterialTheme.typography.labelMedium, color = PinkDeep)
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewEditorScreen(vm: ShopViewModel, id: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    val source = remember(reviews, id) { if (id == "new") Review() else reviews?.firstOrNull { it.id == id } }
    var r by remember(source) { mutableStateOf(source) }
    var askDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        title = if (id == "new") "Add review" else "Edit review", dirty = r != source, busy = vm.busy, snackbar = snackbar, onBack = onBack,
        onDelete = if (id != "new") ({ askDelete = true }) else null,
        onUndo = { r = source },
        onSave = {
            val cur = r ?: return@EditorScaffold
            when {
                cur.name.isBlank() -> vm.message = "Add the client's first name."
                cur.text.isBlank() -> vm.message = "Add what they said."
                else -> vm.saveReview(cur) { if (id == "new") onBack() }
            }
        },
    ) {
        val cur = r ?: return@EditorScaffold
        fun up(f: Review.() -> Review) { r = cur.f() }
        Section("Review") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("First name", cur.name, { up { copy(name = it) } }, Modifier.weight(1f))
                Field("Area", cur.area, { up { copy(area = it) } }, Modifier.weight(1f), hint = "Adyar")
            }
            Text("Rating", style = MaterialTheme.typography.titleSmall)
            Row {
                (1..5).forEach { n ->
                    IconButton(onClick = { up { copy(rating = n) } }) {
                        Icon(if (n <= cur.rating) Icons.Outlined.Star else Icons.Outlined.StarOutline, "$n stars", tint = Gold, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Field("What they said", cur.text, { up { copy(text = it) } }, lines = 5)
            Text("Only post reviews clients actually gave you, with their permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("Photos of their nails") {
            PhotoStrip(cur.images, uploading = vm.uploading > 0, max = 4,
                onAdd = { uris -> vm.upload(uris, "reviews") { urls -> r = (r ?: cur).let { it.copy(images = it.images + urls) } } },
                onChange = { up { copy(images = it) } })
        }
        Section("On the website") {
            ToggleRow("Show on the website", null, cur.visible) { up { copy(visible = it) } }
            ToggleRow("Feature on the home page", null, cur.featured) { up { copy(featured = it) } }
        }
    }
    if (askDelete) ConfirmDelete("Delete this review?", "It comes off the website the next time you publish.",
        onDismiss = { askDelete = false }) { askDelete = false; source?.let { vm.deleteReview(it) { onBack() } } }
}

/* ====================================================================== */
/* Shared pieces                                                          */
/* ====================================================================== */

/** Scrollable editor with a Save bar that appears once something changes, and a guard on back. */
@Composable
private fun EditorScaffold(
    title: String,
    dirty: Boolean,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    fab: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var askLeave by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { askLeave = true }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ShopTopBar(title, onBack = { if (dirty) askLeave = true else onBack() }, actions = {
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
            })
        },
        bottomBar = { if (dirty) SaveBar(busy, onDiscard = onUndo, onSave = onSave) },
        floatingActionButton = { if (!dirty) fab() },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            Spacer(Modifier.height(96.dp))
        }
    }
    if (askLeave) AlertDialog(
        onDismissRequest = { askLeave = false },
        title = { Text("Leave without saving?") },
        text = { Text("Your changes on this screen will be lost.") },
        confirmButton = { TextButton(onClick = { askLeave = false; onBack() }) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Keep editing") } },
    )
}

@Composable
private fun ConfirmDelete(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = Danger) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
)

@Composable
private fun AddFab(label: String, onClick: () -> Unit) = ExtendedFloatingActionButton(
    onClick = onClick, containerColor = Royal, contentColor = Color.White, shape = CircleShape,
    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(label) },
)

@Composable
private fun Thumb(url: String?) {
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Lilac), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        else Icon(Icons.Outlined.ShoppingBag, null, tint = Royal)
    }
}

@Composable
private fun Stars(n: Int, size: Int) = Row {
    (1..5).forEach { i -> Icon(if (i <= n) Icons.Outlined.Star else Icons.Outlined.StarOutline, null, tint = Gold, modifier = Modifier.size(size.dp)) }
}

@Composable
private fun Empty(pad: PaddingValues, title: String, text: String) {
    Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        com.tideandtea.owner.ui.components.Bunny(size = 88.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Royal)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
