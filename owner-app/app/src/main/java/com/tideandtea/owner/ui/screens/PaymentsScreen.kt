package com.tideandtea.owner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tideandtea.owner.data.Payment
import com.tideandtea.owner.data.PaymentKind
import com.tideandtea.owner.data.money
import com.tideandtea.owner.ui.components.Section
import com.tideandtea.owner.ui.theme.Ok
import com.tideandtea.owner.ui.theme.Royal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
private val dayFmt = SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(payments: List<Payment>?, onBack: (() -> Unit)? = null, onOpen: (Payment) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = listOf("All", "Advances", "Balances", "Shop")
    val shown = remember(payments, filter) {
        payments.orEmpty().filter {
            when (filter) {
                1 -> it.kind == PaymentKind.ADVANCE
                2 -> it.kind == PaymentKind.BALANCE
                3 -> it.kind == PaymentKind.SHOP
                else -> true
            }
        }
    }
    val grouped = remember(shown) { shown.groupBy { if (it.atMillis > 0) monthFmt.format(Date(it.atMillis)) else "Just now" } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Royal, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                title = { Text("Payments", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    if (onBack != null) androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { pad ->
        if (payments == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(pad)); return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEachIndexed { i, f ->
                        FilterChip(
                            selected = filter == i, onClick = { filter = i }, label = { Text(f) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Royal, selectedLabelColor = Color.White),
                        )
                    }
                }
            }
            if (shown.isEmpty()) item {
                Section {
                    Text("No payments yet. Advances appear here when you confirm a client's UPI payment, and balances when you mark an appointment as done.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            grouped.forEach { (month, list) ->
                item(key = "h_$month") {
                    Section {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(month, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Text(money(list.sumOf { it.amount }), style = MaterialTheme.typography.titleMedium, color = Ok)
                        }
                        list.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            PaymentRow(p) { onOpen(p) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(p: Payment, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(if (p.method == "Cash") Icons.Outlined.Payments else Icons.Outlined.QrCode2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(p.kind.label, p.method, p.reference.takeIf { it.isNotBlank() }?.let { "Ref $it" }, if (p.atMillis > 0) dayFmt.format(Date(p.atMillis)) else null)
                    .filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            )
        }
        Text("+" + money(p.amount), style = MaterialTheme.typography.titleSmall, color = Ok)
    }
}
