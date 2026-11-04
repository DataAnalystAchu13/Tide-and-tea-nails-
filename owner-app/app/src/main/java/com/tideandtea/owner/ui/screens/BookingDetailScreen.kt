package com.tideandtea.owner.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tideandtea.owner.OwnerViewModel
import com.tideandtea.owner.data.Booking
import com.tideandtea.owner.data.BookingStatus
import com.tideandtea.owner.data.money
import com.tideandtea.owner.ui.components.Bunny
import com.tideandtea.owner.ui.components.InfoRow
import com.tideandtea.owner.ui.components.Section
import com.tideandtea.owner.ui.components.StatusPill
import com.tideandtea.owner.ui.theme.Danger
import com.tideandtea.owner.ui.theme.Pink
import com.tideandtea.owner.ui.theme.PinkDeep
import com.tideandtea.owner.ui.theme.Plum
import com.tideandtea.owner.ui.theme.Royal

private fun Context.open(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    booking: Booking?,
    vm: OwnerViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Royal, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = { Text(booking?.name ?: "Booking", style = MaterialTheme.typography.headlineSmall) },
            )
        },
    ) { pad ->
        if (booking == null) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Bunny(80.dp, hop = true); Text("Loading booking…", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        val b = booking
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text("From ${b.from}" + if (b.instagram.isNotBlank()) " · ${b.instagram}" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(b.status)
                    }
                    Text("${b.prettyDate} at ${b.slot}", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { ctx.open("tel:${b.phone}") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Call, null, Modifier.size(18.dp)); Text("  Call")
                        }
                        OutlinedButton(onClick = { ctx.open(b.whatsappUrl) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Chat, null, Modifier.size(18.dp)); Text("  WhatsApp")
                        }
                    }
                }
            }

            item {
                Section("Location") {
                    val place = b.place
                    if (b.isHomeVisit && place != null) {
                        Text(place.address.ifBlank { "Pinned on map" }, style = MaterialTheme.typography.bodyLarge)
                        Text("About ${"%.1f".format(b.distanceKm)} km from the studio · " + if (b.price.travel > 0) "travel ${money(b.price.travel)}" else "set the travel charge when you approve", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { ctx.open(place.mapsUrl) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)); Text("  Open in Maps")
                            }
                            OutlinedButton(onClick = { ctx.open(place.directionsUrl) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Directions, null, Modifier.size(18.dp)); Text("  Directions")
                            }
                        }
                    } else {
                        Text("Coming to the studio", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            item {
                Section("Inspo pictures (${b.inspoUrls.size})") {
                    PhotoRow(b.inspoUrls) { i -> viewer = b.inspoUrls to i }
                }
            }
            item {
                Section("Natural nails") {
                    if (b.naturalUrls.isEmpty()) Text("Not shared", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else PhotoRow(b.naturalUrls) { i -> viewer = b.naturalUrls to i }
                }
            }

            item {
                Section("Set") {
                    InfoRow("Extension", b.extension?.name ?: "-")
                    InfoRow("Length", b.length?.name ?: "-")
                    if (b.shape.isNotBlank()) InfoRow("Shape", b.shape)
                    InfoRow("Add-ons", b.addOns.joinToString { it.name }.ifBlank { "None" })
                    if (b.notes.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text("“${b.notes}”", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Section("Price") {
                    InfoRow(b.extension?.name ?: "Extension", money(b.price.base))
                    if (b.price.length > 0) InfoRow("Length", money(b.price.length))
                    if (b.price.addOns > 0) InfoRow("Add-ons", money(b.price.addOns))
                    val varies = b.addOns.filter { it.price <= 0 }
                    if (varies.isNotEmpty()) InfoRow("Price varies", varies.joinToString { it.name })
                    InfoRow("Travel", when {
                        !b.isHomeVisit -> "Studio visit"
                        b.price.travel > 0 -> money(b.price.travel)
                        else -> "To confirm"
                    })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    InfoRow("Total", money(b.price.total), strong = true)
                    InfoRow("Advance", money(b.price.advance), strong = true, valueColor = PinkDeep)
                }
            }

            item { Actions(b, vm) }
            item { Box(Modifier.height(24.dp)) }
        }
    }

    viewer?.let { (urls, start) -> PhotoViewer(urls, start) { viewer = null } }
}

@Composable
private fun PhotoRow(urls: List<String>, onOpen: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(urls) { i, url ->
            AsyncImage(
                model = url, contentDescription = "Photo ${i + 1}", contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(132.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpen(i) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoViewer(urls: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start) { urls.size }
        Box(Modifier.fillMaxSize().background(Plum)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(model = urls[page], contentDescription = "Photo ${page + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${pager.currentPage + 1} / ${urls.size}", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Actions(b: Booking, vm: OwnerViewModel) {
    when (b.status) {
        BookingStatus.REQUESTED -> {
            var total by rememberSaveable(b.id) { mutableStateOf(b.price.total.toString()) }
            var advance by rememberSaveable(b.id) { mutableStateOf(b.price.advance.toString()) }
            var note by rememberSaveable(b.id) { mutableStateOf("") }
            Section("Review") {
                Text("Adjust the price if needed (add the travel charge and any \"price varies\" add-ons to the total), add a note for the client, then approve. They'll see the advance to pay on the website.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(total, { total = it.filter(Char::isDigit) }, label = { Text("Total ₹") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(advance, { advance = it.filter(Char::isDigit) }, label = { Text("Advance ₹") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note to client (optional)") }, placeholder = { Text("e.g. PolyGel suits your nails better, same price") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                val t = total.toIntOrNull() ?: 0
                val a = advance.toIntOrNull() ?: 0
                val valid = t > 0 && a in 0..t
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.act("Declined") { decline(b, note.ifBlank { "Sorry, we can't take this booking. Please try another date." }) } },
                        enabled = !vm.busy, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    ) { Text("Decline") }
                    Button(
                        onClick = { vm.act("Approved. The client can now pay the advance.") { approve(b, t, a, note.trim()) } },
                        enabled = valid && !vm.busy, modifier = Modifier.weight(1.4f),
                    ) { Text("Approve") }
                }
            }
        }
        BookingStatus.APPROVED -> Section("Waiting for advance") {
            Text("The client has been asked to pay ${money(b.price.advance)}. You'll see their UPI reference here once they pay.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { vm.act("Booking cancelled and slot freed") { decline(b, "This booking was cancelled. Please contact us on WhatsApp.") } },
                enabled = !vm.busy, colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger), modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel booking") }
        }
        BookingStatus.PAYMENT_SUBMITTED -> Section("Check payment") {
            Text("The client says they paid ${money(b.price.advance)}.", style = MaterialTheme.typography.bodyMedium)
            InfoRow("UPI reference", b.paymentRef, strong = true)
            Text("Find this reference in your UPI app before confirming.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.act("Client asked to re-check payment") { paymentNotFound(b) } }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Not received") }
                Button(
                    onClick = { vm.act("Booking confirmed") { confirmPayment(b) } }, enabled = !vm.busy, modifier = Modifier.weight(1.4f),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Plum),
                ) { Text("Confirm booking") }
            }
        }
        BookingStatus.CONFIRMED -> Section("Confirmed") {
            Text("Advance received. Balance due on the day: ${money(b.price.total - b.price.advance)}.", style = MaterialTheme.typography.bodyMedium)
            Text("When the appointment is finished, record how the balance was paid:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.act("Done. Balance recorded as cash") { markDone(b, "Cash") } }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Paid in cash") }
                Button(onClick = { vm.act("Done. Balance recorded as UPI") { markDone(b, "UPI") } }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Paid by UPI") }
            }
        }
        BookingStatus.COMPLETED -> Section { Text("Done. Hope they loved their nails!", style = MaterialTheme.typography.bodyMedium) }
        BookingStatus.DECLINED -> Section { Text("Declined. ${b.ownerNote}", style = MaterialTheme.typography.bodyMedium) }
    }
}
