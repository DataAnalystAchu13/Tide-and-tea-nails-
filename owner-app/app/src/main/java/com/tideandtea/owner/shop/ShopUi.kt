package com.tideandtea.owner.shop

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tideandtea.owner.ui.components.PillColors
import com.tideandtea.owner.ui.theme.Danger
import com.tideandtea.owner.ui.theme.Gold
import com.tideandtea.owner.ui.theme.Lilac
import com.tideandtea.owner.ui.theme.Ok
import com.tideandtea.owner.ui.theme.PinkDeep
import com.tideandtea.owner.ui.theme.Royal
import com.tideandtea.owner.ui.theme.Royal2

fun orderColors(s: OrderStatus): PillColors = when (s) {
    OrderStatus.PENDING_PAYMENT -> PillColors(Color(0xFFF1E8FB), Royal2)
    OrderStatus.PAYMENT_SUBMITTED -> PillColors(Color(0xFFFBF1DD), Color(0xFF8A6420))
    OrderStatus.PAID -> PillColors(Color(0xFFFDE8F0), PinkDeep)
    OrderStatus.PACKED -> PillColors(Color(0xFFFDE8F0), PinkDeep)
    OrderStatus.SHIPPED, OrderStatus.DELIVERED -> PillColors(Color(0xFFE2F2EE), Ok)
    OrderStatus.CANCELLED -> PillColors(Color(0xFFF8E3E8), Danger)
}

@Composable
fun Pill(text: String, c: PillColors, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(c.bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c.fg))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.fg, maxLines = 1)
    }
}

/** Royal top bar used by every shop screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Royal, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        actions = { actions() },
    )
}

/** Text field styled like the rest of the app. */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    lines: Int = 1,
    number: Boolean = false,
    caps: Boolean = false,
    prefix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(if (number) v.filter { it.isDigit() }.take(7) else v) },
        label = { Text(label) },
        placeholder = hint?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        singleLine = lines == 1,
        minLines = lines,
        maxLines = if (lines == 1) 1 else 20,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Royal, focusedLabelColor = Royal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ToggleRow(title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Royal))
    }
}

/** A row of photos with an add tile. The first photo is the cover. */
@Composable
fun PhotoStrip(
    urls: List<String>,
    uploading: Boolean,
    max: Int = 8,
    onAdd: (List<android.net.Uri>) -> Unit,
    onChange: (List<String>) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = max.coerceAtLeast(2))) { uris ->
        if (uris.isNotEmpty()) onAdd(uris.take(max - urls.size))
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(urls, key = { _, u -> u }) { i, u ->
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(16.dp)).background(Lilac)) {
                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                IconButton(
                    onClick = { onChange(urls.filterIndexed { j, _ -> j != i }) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(Color(0xCC241238)),
                ) { Icon(Icons.Outlined.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp)) }
                if (i == 0) {
                    Text("Cover", color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Royal).padding(horizontal = 8.dp, vertical = 2.dp))
                } else {
                    IconButton(
                        onClick = { onChange(listOf(u) + urls.filterIndexed { j, _ -> j != i }) },
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(28.dp).clip(CircleShape).background(Color(0xCC241238)),
                    ) { Icon(Icons.Outlined.Star, "Make cover", tint = Gold, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        if (urls.size < max) item {
            Box(
                Modifier.size(104.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFBF8FE))
                    .border(2.dp, Color(0xFFC9B6E0), RoundedCornerShape(16.dp))
                    .clickable(enabled = !uploading) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (uploading) CircularProgressIndicator(color = Royal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, tint = Royal)
                    Text("Add photo", style = MaterialTheme.typography.labelMedium, color = Royal)
                }
            }
        }
    }
}

fun ago(ms: Long): String {
    if (ms <= 0) return ""
    val d = (System.currentTimeMillis() - ms) / 1000
    return when {
        d < 60 -> "just now"
        d < 3600 -> "${d / 60} min ago"
        d < 86400 -> "${d / 3600} h ago"
        d < 7 * 86400 -> "${d / 86400} d ago"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date(ms))
    }
}
