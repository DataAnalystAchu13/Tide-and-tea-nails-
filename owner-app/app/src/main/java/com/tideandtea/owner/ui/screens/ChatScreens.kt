package com.tideandtea.owner.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tideandtea.owner.OwnerViewModel
import com.tideandtea.owner.data.Booking
import com.tideandtea.owner.data.ChatMessage
import com.tideandtea.owner.data.ChatSummary
import com.tideandtea.owner.data.MediaType
import com.tideandtea.owner.ui.components.Bunny
import com.tideandtea.owner.ui.theme.Pink
import com.tideandtea.owner.ui.theme.Plum
import com.tideandtea.owner.ui.theme.Royal

private val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH)

private fun ago(millis: Long): String =
    if (millis <= 0) "" else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(chats: List<ChatSummary>, onOpen: (String) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Royal, titleContentColor = Color.White),
                title = { Text("Chats", style = MaterialTheme.typography.headlineSmall) },
            )
        },
    ) { pad ->
        if (chats.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Bunny(96.dp)
                Spacer(Modifier.height(12.dp))
                Text("No chats yet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("Clients can message you from their booking on the website. You can also start a chat from any request.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(chats, key = { it.bookingId }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(c.bookingId) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text(c.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ago(c.lastAtMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (c.lastFrom == "owner") "You: " else "") + c.lastText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (c.ownerUnread > 0) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (c.ownerUnread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            if (c.ownerUnread > 0) Badge(containerColor = Pink, contentColor = Plum) { Text("${c.ownerUnread}") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    bookingId: String,
    booking: Booking?,
    vm: OwnerViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenBooking: () -> Unit,
) {
    val ctx = LocalContext.current
    val flow = remember(bookingId) { vm.messages(bookingId) }
    val messages by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var text by rememberSaveable(bookingId) { mutableStateOf("") }
    var media by remember { mutableStateOf<Uri?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    val mediaMime = media?.let { ctx.contentResolver.getType(it) }
    val mediaType = when {
        media == null -> null
        mediaMime?.startsWith("video") == true -> MediaType.VIDEO
        else -> MediaType.IMAGE
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) media = uri }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
        vm.markChatRead(bookingId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Royal, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = {
                    Column(Modifier.clickable(enabled = booking != null, onClick = onOpenBooking)) {
                        Text(booking?.name ?: "Chat", style = MaterialTheme.typography.headlineSmall)
                        if (booking != null) Text("${booking.prettyDate} · ${booking.slot} · tap for booking", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                vm.sendProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                if (media != null) {
                    Row(Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.small).background(Plum), contentAlignment = Alignment.Center) {
                            if (mediaType == MediaType.IMAGE) AsyncImage(media, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Outlined.Videocam, null, tint = Color.White)
                        }
                        Text(if (mediaType == MediaType.VIDEO) "Video ready to send" else "Photo ready to send", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { media = null }) { Icon(Icons.Outlined.Close, "Remove") }
                    }
                }
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                        Icon(Icons.Outlined.AddPhotoAlternate, "Add photo or video", tint = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(2000) },
                        placeholder = { Text("Message") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    )
                    val canSend = booking != null && vm.sendProgress == null && (text.isNotBlank() || media != null)
                    IconButton(
                        onClick = {
                            if (booking != null) vm.send(booking, text, media, mediaType, mediaMime) { text = ""; media = null }
                        },
                        enabled = canSend,
                        modifier = Modifier.padding(start = 4.dp).clip(CircleShape).background(if (canSend) Royal else MaterialTheme.colorScheme.surfaceVariant),
                    ) { Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = Color.White) }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = list,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Bunny(72.dp)
                    Text("Say hi! Photos and videos you send appear on the client's booking page.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(messages, key = { it.id }) { m ->
                Bubble(m, onImage = { viewer = it }, onVideo = { url ->
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")) } catch (_: ActivityNotFoundException) {}
                })
            }
        }
    }

    viewer?.let { url -> PhotoViewer(listOf(url), 0) { viewer = null } }
}

@Composable
private fun Bubble(m: ChatMessage, onImage: (String) -> Unit, onVideo: (String) -> Unit) {
    val mine = m.fromOwner
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp))
                .background(if (mine) Royal else MaterialTheme.colorScheme.surface)
                .then(if (mine) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val url = m.mediaUrl
            if (url != null && m.mediaType == MediaType.IMAGE) {
                AsyncImage(url, "Photo", contentScale = ContentScale.Crop, modifier = Modifier.size(230.dp).clip(RoundedCornerShape(16.dp)).clickable { onImage(url) })
            }
            if (url != null && m.mediaType == MediaType.VIDEO) {
                Box(Modifier.size(230.dp, 150.dp).clip(RoundedCornerShape(16.dp)).background(Plum).clickable { onVideo(url) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayCircle, "Play video", tint = Color.White, modifier = Modifier.size(52.dp))
                    Text("Video", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                }
            }
            if (m.text.isNotBlank()) {
                Text(m.text, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Text(
                if (m.pending) "Sending…" else timeFmt.format(java.util.Date(m.atMillis)),
                color = if (mine) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
            )
        }
    }
}
