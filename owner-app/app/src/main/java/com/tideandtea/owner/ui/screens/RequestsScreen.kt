package com.tideandtea.owner.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tideandtea.owner.R
import com.tideandtea.owner.data.Booking
import com.tideandtea.owner.data.BookingStatus
import com.tideandtea.owner.data.money
import com.tideandtea.owner.ui.components.Bunny
import com.tideandtea.owner.ui.components.StatusPill
import com.tideandtea.owner.ui.theme.Pink
import com.tideandtea.owner.ui.theme.Plum
import com.tideandtea.owner.ui.theme.Royal

private data class Filter(val label: String, val statuses: Set<BookingStatus>?)

private val FILTERS = listOf(
    Filter("New", setOf(BookingStatus.REQUESTED)),
    Filter("Awaiting advance", setOf(BookingStatus.APPROVED)),
    Filter("Check payment", setOf(BookingStatus.PAYMENT_SUBMITTED)),
    Filter("Upcoming", setOf(BookingStatus.CONFIRMED)),
    Filter("All", null),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    bookings: List<Booking>?,
    snackbar: SnackbarHostState,
    ownerEmail: String?,
    onOpen: (Booking) -> Unit,
    onPublishPrices: () -> Unit,
    onSignOut: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.background(Royal)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Royal, titleContentColor = Color.White, actionIconContentColor = Color.White),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(painterResource(R.drawable.logo), null, Modifier.size(36.dp).clip(CircleShape))
                            Text("Requests", style = MaterialTheme.typography.headlineSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (ownerEmail != null) DropdownMenuItem(text = { Text(ownerEmail, color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {}, enabled = false)
                            DropdownMenuItem(text = { Text("Publish starter price list") }, onClick = { menu = false; onPublishPrices() })
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; onSignOut() })
                        }
                    },
                )
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    containerColor = Royal,
                    contentColor = Color.White,
                    edgePadding = 12.dp,
                    divider = {},
                    indicator = { positions ->
                        if (tab < positions.size) {
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), height = 3.dp, color = Pink)
                        }
                    },
                ) {
                    FILTERS.forEachIndexed { i, f ->
                        val count = f.statuses?.let { s -> bookings?.count { it.status in s } } ?: 0
                        Tab(
                            selected = tab == i, onClick = { tab = i },
                            selectedContentColor = Color.White, unselectedContentColor = Color.White.copy(alpha = 0.65f),
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(f.label, style = MaterialTheme.typography.labelLarge)
                                    if (count > 0 && f.statuses != null) Badge(containerColor = Pink, contentColor = Plum) { Text("$count") }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { pad ->
        when {
            bookings == null -> Box(Modifier.padding(pad).fillMaxWidth()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else -> {
                val f = FILTERS[tab]
                val shown = bookings.filter { f.statuses == null || it.status in f.statuses }
                    .let { list -> if (f.statuses == setOf(BookingStatus.CONFIRMED)) list.sortedWith(compareBy({ it.date }, { it.slot })) else list }
                if (shown.isEmpty()) EmptyState(f.label, Modifier.padding(pad))
                else LazyColumn(
                    Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.id }) { b -> BookingCard(b) { onOpen(b) } }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(tab: String, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Bunny(size = 96.dp)
        Spacer(Modifier.height(12.dp))
        Text("All clear", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            if (tab == "New") "New requests from the website appear here the moment they're sent." else "Nothing in \"$tab\" right now.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BookingCard(b: Booking, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(84.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(model = b.inspoUrls.firstOrNull(), contentDescription = "Inspo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (b.inspoUrls.size > 1) {
                Text(
                    "+${b.inspoUrls.size - 1}", color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape).background(Plum.copy(alpha = 0.75f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(money(b.price.total), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOfNotNull(b.extension?.name, b.length?.name).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (b.isHomeVisit) Icons.Outlined.Home else Icons.Outlined.Storefront, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${b.prettyDate} · ${b.slot}" + if (b.isHomeVisit) " · ${"%.1f".format(b.distanceKm)} km" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(b.status, Modifier.padding(top = 4.dp))
        }
    }
}
