package com.tideandtea.owner.shop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tideandtea.owner.ui.components.Bunny
import com.tideandtea.owner.ui.components.InfoRow
import com.tideandtea.owner.ui.components.Section
import com.tideandtea.owner.ui.theme.Danger
import com.tideandtea.owner.ui.theme.Ok
import com.tideandtea.owner.ui.theme.PinkDeep
import com.tideandtea.owner.ui.theme.Royal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class OrderFilter(val label: String, val test: (Order) -> Boolean) {
    TODO("To do", { it.needsAction }),
    UNPAID("Unpaid", { it.status == OrderStatus.PENDING_PAYMENT }),
    SHIPPED("Shipped", { it.status == OrderStatus.SHIPPED || it.status == OrderStatus.DELIVERED }),
    ALL("All", { true }),
}

@Composable
fun OrdersScreen(vm: ShopViewModel, snackbar: SnackbarHostState, onOpen: (String) -> Unit) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    var filterIx by rememberSaveable { mutableIntStateOf(0) }
    val filter = OrderFilter.entries[filterIx]
    val shown = remember(orders, filter) { orders.orEmpty().filter(filter.test) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ShopTopBar("Orders", actions = { IconButton(onClick = { vm.refreshOrders() }) { Icon(Icons.Outlined.Refresh, "Refresh") } }) },
    ) { pad ->
        if (orders == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(pad), color = Royal); return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val todo = orders.orEmpty().count { it.needsAction }
                val week = orders.orEmpty().filter { it.paidAt > System.currentTimeMillis() - 7 * 86_400_000L }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStat("To pack or check", "$todo", Modifier.weight(1f))
                    MiniStat("Sold this week", rupees(week.sumOf { it.total }), Modifier.weight(1f))
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(OrderFilter.entries.size) { i ->
                        val f = OrderFilter.entries[i]
                        val n = orders.orEmpty().count(f.test)
                        FilterChip(
                            selected = i == filterIx, onClick = { filterIx = i },
                            label = { Text(if (f == OrderFilter.ALL) f.label else "${f.label} · $n") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Royal, selectedLabelColor = Color.White),
                        )
                    }
                }
            }
            if (shown.isEmpty()) item {
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Bunny(size = 56.dp)
                        Text(
                            if (filter == OrderFilter.TODO) "All caught up. New paid orders show up here with a notification."
                            else "No orders here yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(shown, key = { it.id }) { o -> OrderCard(o) { onOpen(o.id) } }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Section(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
private fun OrderCard(o: Order, onClick: () -> Unit) {
    Section(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.name.ifBlank { "Customer" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Order ${o.code} · ${ago(o.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill(o.status.label, orderColors(o.status))
        }
        Text(
            o.lines.joinToString(" · ") { "${it.name}${if (it.size.isNotBlank()) " (${it.size})" else ""} ×${it.qty}" },
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${o.itemCount} item${if (o.itemCount == 1) "" else "s"} · ${o.address.substringAfterLast(", ").ifBlank { "" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(rupees(o.total), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (o.oversold) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Danger, modifier = Modifier.size(18.dp))
            Text("Stock ran out for this order. Check before packing.", color = Danger, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val whenFmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)

@Composable
fun OrderDetailScreen(vm: ShopViewModel, id: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    val o = orders?.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    var shipOpen by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var payOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ShopTopBar(if (o != null) "Order ${o.code}" else "Order", onBack = onBack) },
        bottomBar = { if (o != null) OrderActions(o, vm.busy, onConfirm = { payOpen = true }, onReject = { vm.rejectPayment(o) },
            onPacked = { vm.setStatus(o, OrderStatus.PACKED) }, onShip = { shipOpen = true }, onDelivered = { vm.setStatus(o, OrderStatus.DELIVERED) }) },
    ) { pad ->
        if (o == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(pad), color = Royal); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(o.status.label, orderColors(o.status))
                Spacer(Modifier.weight(1f))
                Text(whenFmt.format(Date(o.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (o.oversold) Section {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = Danger)
                    Text("This order was paid after stock ran out. Check you have the set before packing, or message the customer.", color = Danger)
                }
            }
            Section("Customer") {
                Text(o.name, style = MaterialTheme.typography.titleLarge)
                Text(o.address, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91${o.phone}"))) }) {
                        Icon(Icons.Outlined.Call, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Call")
                    }
                    OutlinedButton(onClick = {
                        val text = Uri.encode("Hi ${o.name.substringBefore(' ')}, this is Tide & Tea about your order ${o.code}.")
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91${o.phone}?text=$text")))
                    }) { Text("WhatsApp") }
                    OutlinedButton(onClick = { copy(ctx, "${o.name}\n${o.address}\n+91 ${o.phone}"); vm.message = "Address copied" }) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Copy")
                    }
                }
                if (o.email.isNotBlank()) Text(o.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section("Items") {
                o.lines.forEachIndexed { i, l ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(l.size.takeIf { it.isNotBlank() }?.let { "Size $it" }, "Qty ${l.qty}", rupees(l.unit) + " each").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(rupees(l.amount), fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                InfoRow("Subtotal", rupees(o.subtotal))
                if (o.discount > 0) InfoRow("Coupon ${o.coupon}", "−" + rupees(o.discount), valueColor = PinkDeep)
                InfoRow("Shipping", if (o.shipping > 0) rupees(o.shipping) else "Free")
                InfoRow("Total", rupees(o.total), strong = true)
            }
            if (o.notes.isNotBlank()) Section("Customer notes") { Text(o.notes) }
            Section("Payment") {
                InfoRow("Method", if (o.mode == "razorpay") "Online (Razorpay)" else "UPI to your ID")
                if (o.paymentRef.isNotBlank()) InfoRow("Reference", o.paymentRef)
                if (o.paidAt > 0) InfoRow("Paid", whenFmt.format(Date(o.paidAt)), valueColor = Ok)
                if (o.status == OrderStatus.PAYMENT_SUBMITTED) Text("Check your UPI app for ${rupees(o.total)} with this reference before confirming.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (o.status == OrderStatus.SHIPPED || o.status == OrderStatus.DELIVERED) Section("Shipping") {
                if (o.courier.isNotBlank()) InfoRow("Courier", o.courier)
                if (o.trackingNumber.isNotBlank()) InfoRow("Tracking number", o.trackingNumber)
                if (o.trackingUrl.isNotBlank()) TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(o.trackingUrl))) }) { Text("Open tracking page") }
            }
            if (o.status !in setOf(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.SHIPPED)) {
                TextButton(onClick = { confirmCancel = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("Cancel order") }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (o != null && payOpen) PaymentDialog(o, onDismiss = { payOpen = false }) { method -> vm.confirmPayment(o, method) { payOpen = false } }
    if (o != null && shipOpen) ShipDialog(onDismiss = { shipOpen = false }) { c, t, u -> vm.ship(o, c, t, u) { shipOpen = false } }
    if (o != null && confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("Cancel order ${o.code}?") },
        text = { Text(if (o.paidAt > 0) "Refund the customer from Razorpay or your UPI app. Stock isn't added back automatically, so update it in Website, then Products." else "The customer hasn't paid, so nothing needs refunding.") },
        confirmButton = { TextButton(onClick = { vm.setStatus(o, OrderStatus.CANCELLED); confirmCancel = false }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("Cancel order") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it") } },
    )
}

@Composable
private fun OrderActions(o: Order, busy: Boolean, onConfirm: () -> Unit, onReject: () -> Unit, onPacked: () -> Unit, onShip: () -> Unit, onDelivered: () -> Unit) {
    val (label, action, icon) = when (o.status) {
        OrderStatus.PAYMENT_SUBMITTED -> Triple("Payment received", onConfirm, null)
        OrderStatus.PENDING_PAYMENT -> if (o.mode == "upi") Triple("Mark as paid", onConfirm, null) else return
        OrderStatus.PAID -> Triple("Mark packed", onPacked, Icons.Outlined.Inventory2)
        OrderStatus.PACKED -> Triple("Mark shipped", onShip, Icons.Outlined.LocalShipping)
        OrderStatus.SHIPPED -> Triple("Mark delivered", onDelivered, null)
        else -> return
    }
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (o.status == OrderStatus.PAYMENT_SUBMITTED) OutlinedButton(onClick = onReject, enabled = !busy, modifier = Modifier.height(52.dp)) { Text("Not received") }
            Button(
                onClick = action, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Royal), shape = CircleShape,
            ) {
                if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)) }
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PaymentDialog(o: Order, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm ${rupees(o.total)}") },
        text = { Text(if (o.paymentRef.isNotBlank()) "Make sure UPI ref ${o.paymentRef} shows ${rupees(o.total)} in your bank or UPI app." else "Only confirm once the money is in your account.") },
        confirmButton = { TextButton(onClick = { onConfirm("UPI") }) { Text("Received by UPI") } },
        dismissButton = { TextButton(onClick = { onConfirm("Cash") }) { Text("Paid in cash") } },
    )
}

@Composable
private fun ShipDialog(onDismiss: () -> Unit, onShip: (String, String, String) -> Unit) {
    var courier by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shipping details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Courier", courier, { courier = it }, hint = "India Post, Delhivery, DTDC…")
                Field("Tracking number", number, { number = it }, caps = true)
                Field("Tracking link", url, { url = it }, hint = "Optional")
                Text("The customer sees these on their order page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onShip(courier, number, url) }) { Text("Mark shipped") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Address", text))
}
