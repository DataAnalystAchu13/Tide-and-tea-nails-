package com.tideandtea.owner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tideandtea.owner.data.BookingStatus
import com.tideandtea.owner.ui.theme.Danger
import com.tideandtea.owner.ui.theme.Ok
import com.tideandtea.owner.ui.theme.PinkDeep
import com.tideandtea.owner.ui.theme.Royal2

data class PillColors(val bg: Color, val fg: Color)

fun statusColors(s: BookingStatus): PillColors = when (s) {
    BookingStatus.REQUESTED -> PillColors(Color(0xFFF1E8FB), Royal2)
    BookingStatus.APPROVED -> PillColors(Color(0xFFFDE8F0), PinkDeep)
    BookingStatus.PAYMENT_SUBMITTED -> PillColors(Color(0xFFFBF1DD), Color(0xFF8A6420))
    BookingStatus.CONFIRMED, BookingStatus.COMPLETED -> PillColors(Color(0xFFE2F2EE), Ok)
    BookingStatus.DECLINED -> PillColors(Color(0xFFF8E3E8), Danger)
    BookingStatus.CANCELLED -> PillColors(Color(0xFFEFEDF2), Color(0xFF6F5E82))
}

@Composable
fun StatusPill(status: BookingStatus, modifier: Modifier = Modifier) {
    val c = statusColors(status)
    Row(
        modifier
            .clip(CircleShape)
            .background(c.bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c.fg))
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = c.fg, maxLines = 1)
    }
}

/** A flat, bordered card: matte, no elevation. */
@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun InfoRow(label: String, value: String, strong: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Bold,
            color = valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
