package com.tideandtea.owner.ui.screens

import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tideandtea.owner.data.Booking
import com.tideandtea.owner.data.BookingStatus
import com.tideandtea.owner.data.Payment
import com.tideandtea.owner.data.money
import com.tideandtea.owner.ui.components.Section
import com.tideandtea.owner.ui.components.statusColors
import com.tideandtea.owner.ui.theme.Pink
import com.tideandtea.owner.ui.theme.Royal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class Period(val label: String) { MONTH("This month"), DAYS30("Last 30 days"), ALL("All time") }

private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Everything on this screen is computed on the phone from the live bookings and payments. */
private data class Stats(
    val revenue: Int,
    val advances: Int,
    val balances: Int,
    val requests: Int,
    val approvalRate: Int?,
    val avgBooking: Int?,
    val homeShare: Int?,
    val weekly: List<Pair<String, Int>>,
    val topSets: List<Pair<String, Int>>,
    val pipeline: List<Pair<BookingStatus, Int>>,
    val upcoming7: Int,
)

private fun compute(bookings: List<Booking>, payments: List<Payment>, period: Period): Stats {
    val today = LocalDate.now()
    val inPeriod: (LocalDate) -> Boolean = when (period) {
        Period.MONTH -> { d -> d.year == today.year && d.month == today.month }
        Period.DAYS30 -> { d -> !d.isBefore(today.minusDays(29)) }
        Period.ALL -> { _ -> true }
    }
    val pays = payments.filter { it.atMillis > 0 && inPeriod(it.atMillis.toDate()) }
    val reqs = bookings.filter { it.createdAtMillis > 0 && inPeriod(it.createdAtMillis.toDate()) }
    val decided = reqs.filter { it.status != BookingStatus.REQUESTED }
    val accepted = decided.count { it.status != BookingStatus.DECLINED }
    val won = reqs.filter { it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.COMPLETED }

    // Revenue by week for the last 8 weeks (always, so the chart has shape).
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    val weekly = (7 downTo 0).map { back ->
        val start = weekStart.minusWeeks(back.toLong())
        val end = start.plusDays(7)
        start.format(fmt) to payments.filter { p ->
            val d = p.atMillis.toDate(); p.atMillis > 0 && !d.isBefore(start) && d.isBefore(end)
        }.sumOf { it.amount }
    }

    return Stats(
        revenue = pays.sumOf { it.amount },
        advances = pays.filter { it.kind.key == "advance" }.sumOf { it.amount },
        balances = pays.filter { it.kind.key == "balance" }.sumOf { it.amount },
        requests = reqs.size,
        approvalRate = if (decided.isEmpty()) null else accepted * 100 / decided.size,
        avgBooking = if (won.isEmpty()) null else won.sumOf { it.price.total } / won.size,
        homeShare = if (reqs.isEmpty()) null else reqs.count { it.isHomeVisit } * 100 / reqs.size,
        weekly = weekly,
        topSets = reqs.groupingBy { it.extension?.name ?: "Other" }.eachCount().toList().sortedByDescending { it.second }.take(5),
        pipeline = listOf(BookingStatus.REQUESTED, BookingStatus.APPROVED, BookingStatus.PAYMENT_SUBMITTED, BookingStatus.CONFIRMED)
            .map { s -> s to bookings.count { it.status == s } },
        upcoming7 = bookings.count {
            it.status == BookingStatus.CONFIRMED && runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it.date)) in 0..6 }.getOrDefault(false)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(bookings: List<Booking>?, payments: List<Payment>?, onPayments: () -> Unit = {}) {
    var periodIx by rememberSaveable { mutableIntStateOf(0) }
    val period = Period.entries[periodIx]
    val stats = remember(bookings, payments, period) { compute(bookings.orEmpty(), payments.orEmpty(), period) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Royal, titleContentColor = Color.White, actionIconContentColor = Color.White),
                title = { Text("Insights", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    androidx.compose.material3.TextButton(onClick = onPayments) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = Color.White)
                        Text("  Payments", color = Color.White)
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEachIndexed { i, p ->
                        FilterChip(
                            selected = i == periodIx, onClick = { periodIx = i }, label = { Text(p.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Royal, selectedLabelColor = Color.White),
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Collected", money(stats.revenue), "Advance ${money(stats.advances)} · balance ${money(stats.balances)}", Modifier.weight(1f))
                    Tile("Requests", "${stats.requests}", stats.homeShare?.let { "$it% home visits" } ?: "No requests yet", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Approval rate", stats.approvalRate?.let { "$it%" } ?: "–", "Of requests you've reviewed", Modifier.weight(1f))
                    Tile("Avg booking", stats.avgBooking?.let { money(it) } ?: "–", "Confirmed and done sets", Modifier.weight(1f))
                }
            }
            item {
                Section("Collected per week") {
                    WeeklyBars(stats.weekly)
                }
            }
            item {
                Section("Right now") {
                    stats.pipeline.forEach { (s, n) ->
                        val c = statusColors(s)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.label, color = c.fg, style = MaterialTheme.typography.titleSmall)
                            Text("$n", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Text("${stats.upcoming7} confirmed appointment${if (stats.upcoming7 == 1) "" else "s"} in the next 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Section("Most requested sets") {
                    if (stats.topSets.isEmpty()) Text("No requests in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val max = stats.topSets.maxOfOrNull { it.second } ?: 1
                    stats.topSets.forEach { (name, n) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("$n", style = MaterialTheme.typography.titleSmall)
                            }
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(n.toFloat() / max).clip(RoundedCornerShape(8.dp)).background(Pink))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String, modifier: Modifier) {
    Section(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
private fun WeeklyBars(data: List<Pair<String, Int>>) {
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val last = Pink
    val grid = MaterialTheme.colorScheme.outline
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Highest week ${money(max)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = data.size
            val gap = 8.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            data.forEachIndexed { i, (_, v) ->
                val h = if (v == 0) 2.dp.toPx() else size.height * v / max
                drawRoundRect(
                    color = if (i == n - 1) last else bar,
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(data.first().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("This week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
