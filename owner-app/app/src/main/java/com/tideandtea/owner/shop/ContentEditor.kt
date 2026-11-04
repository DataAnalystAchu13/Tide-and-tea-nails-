package com.tideandtea.owner.shop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tideandtea.owner.ui.components.Section
import com.tideandtea.owner.ui.theme.Royal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One editable field of a JSON document, addressed by a dotted path. */
sealed interface F {
    val path: String
    data class Text(override val path: String, val label: String, val hint: String? = null, val lines: Int = 1) : F
    data class Num(override val path: String, val label: String, val prefix: String? = null, val hint: String? = null) : F
    data class Bool(override val path: String, val label: String, val sub: String? = null) : F
    data class Image(override val path: String, val label: String) : F
    data class Images(override val path: String, val label: String, val max: Int = 6) : F
    data class Words(override val path: String, val label: String, val hint: String) : F
    /** Days of the week stored as ISO numbers: 1 = Monday ... 7 = Sunday, as the booking page reads them. */
    data class Days(override val path: String, val label: String) : F
    data class Rows(
        override val path: String,
        val label: String,
        val cols: List<Col>,
        val add: String,
        val idFromName: Boolean = false,
    ) : F
    data class Note(val text: String) : F { override val path get() = "" }
}

data class Col(val key: String, val label: String, val num: Boolean = false, val lines: Int = 1, val prefix: String? = null)

data class FormSection(val title: String, val fields: List<F>)

/**
 * A form over a JSON document (site content or the price list). Shows a sticky Save bar once
 * something changes and asks before leaving with unsaved edits.
 */
@Composable
fun JsonFormScreen(
    title: String,
    source: JsonObject?,
    sections: List<FormSection>,
    vm: ShopViewModel,
    snackbar: SnackbarHostState,
    folder: String,
    onBack: () -> Unit,
    onSave: (JsonObject, () -> Unit) -> Unit,
) {
    var data by remember(source) { mutableStateOf(source) }
    var askLeave by remember { mutableStateOf(false) }
    val dirty = data != null && data != source
    BackHandler(enabled = dirty) { askLeave = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ShopTopBar(title, onBack = { if (dirty) askLeave = true else onBack() }) },
        bottomBar = { if (dirty) SaveBar(vm.busy, onDiscard = { data = source }) { onSave(normalise(data!!, sections)) {} } },
    ) { pad ->
        val d = data
        if (d == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(pad), color = Royal); return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sections.forEach { s ->
                Section(s.title) {
                    s.fields.forEach { f -> FieldFor(f, d, vm, folder) { path, v -> data = d.with(path, v) } }
                }
            }
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
fun SaveBar(busy: Boolean, label: String = "Save", onDiscard: (() -> Unit)? = null, onSave: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDiscard != null) OutlinedButton(onClick = onDiscard, enabled = !busy, modifier = Modifier.height(52.dp)) { Text("Undo") }
            Button(
                onClick = onSave, enabled = !busy, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Royal),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(if (busy) "Saving…" else label, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable
private fun FieldFor(f: F, d: JsonObject, vm: ShopViewModel, folder: String, set: (String, kotlinx.serialization.json.JsonElement) -> Unit) {
    when (f) {
        is F.Note -> Text(f.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is F.Text -> Field(f.label, d.at(f.path).text(), { set(f.path, JsonPrimitive(it)) }, hint = f.hint, lines = f.lines)
        is F.Num -> Field(f.label, d.at(f.path).number().takeIf { it != 0L || d.at(f.path) != null }?.toString().orEmpty(),
            { set(f.path, JsonPrimitive(it.toLongOrNull() ?: 0L)) }, number = true, prefix = f.prefix, hint = f.hint)
        is F.Bool -> ToggleRow(f.label, f.sub, d.at(f.path).flag()) { set(f.path, JsonPrimitive(it)) }
        is F.Words -> {
            val list = d.at(f.path).strings()
            var text by remember(f.path) { mutableStateOf(list.joinToString(", ")) }
            Field(f.label, text, { text = it; set(f.path, JsonArray(it.split(',').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.map(::JsonPrimitive))) }, hint = f.hint)
        }
        is F.Days -> {
            Text(f.label, style = MaterialTheme.typography.titleSmall)
            val on = d.at(f.path).objectsOrNumbers()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { ix, label ->
                    val i = ix + 1
                    val sel = i in on
                    androidx.compose.material3.FilterChip(
                        selected = sel,
                        onClick = { set(f.path, JsonArray((if (sel) on - i else on + i).sorted().map { JsonPrimitive(it) })) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Royal, selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                        ),
                    )
                }
            }
        }
        is F.Image -> {
            Text(f.label, style = MaterialTheme.typography.titleSmall)
            val url = d.at(f.path).text()
            PhotoStrip(listOfNotNull(url.takeIf { it.isNotBlank() }), uploading = vm.uploading > 0, max = 1,
                onAdd = { uris -> vm.upload(uris.take(1), folder) { urls -> urls.firstOrNull()?.let { set(f.path, JsonPrimitive(it)) } } },
                onChange = { set(f.path, JsonPrimitive(it.firstOrNull().orEmpty())) })
        }
        is F.Images -> {
            Text(f.label, style = MaterialTheme.typography.titleSmall)
            val urls = d.at(f.path).strings()
            PhotoStrip(urls, uploading = vm.uploading > 0, max = f.max,
                onAdd = { uris -> vm.upload(uris, folder) { added -> set(f.path, JsonArray((urls + added).map(::JsonPrimitive))) } },
                onChange = { set(f.path, JsonArray(it.map(::JsonPrimitive))) })
        }
        is F.Rows -> RowsEditor(f, d.at(f.path).objects()) { rows -> set(f.path, JsonArray(rows)) }
    }
}

@Composable
private fun RowsEditor(f: F.Rows, rows: List<JsonObject>, onChange: (List<JsonObject>) -> Unit) {
    Text(f.label, style = MaterialTheme.typography.titleSmall)
    rows.forEachIndexed { i, row ->
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            f.cols.forEach { c ->
                val el = row[c.key]
                val value = if (c.num) el.number().toString().takeIf { el != null }.orEmpty() else el.text()
                Field(c.label, value, { v ->
                    val nv = if (c.num) JsonPrimitive(v.toLongOrNull() ?: 0L) else JsonPrimitive(v)
                    onChange(rows.mapIndexed { j, r -> if (j == i) JsonObject(r + (c.key to nv)) else r })
                }, number = c.num, lines = c.lines, prefix = c.prefix)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (i > 0) IconButton(onClick = { onChange(rows.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
                IconButton(onClick = { onChange(rows.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.DeleteOutline, "Remove") }
            }
        }
    }
    TextButton(onClick = {
        onChange(rows + JsonObject(f.cols.associate { it.key to (if (it.num) JsonPrimitive(0) else JsonPrimitive("")) }))
    }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(f.add) }
}

/** Gives price-list rows a stable id from their name, and drops empty rows, before saving. */
private fun normalise(d: JsonObject, sections: List<FormSection>): JsonObject {
    var out = d
    sections.flatMap { it.fields }.filterIsInstance<F.Rows>().forEach { f ->
        val first = f.cols.first().key
        val seen = mutableSetOf<String>()
        val rows = out.at(f.path).objects().filter { it.s(first).isNotBlank() }.map { r ->
            if (!f.idFromName) r else {
                var id = r.s("id").ifBlank { slugify(r.s("name")) }
                while (!seen.add(id)) id += "-2"
                JsonObject(r + ("id" to JsonPrimitive(id)))
            }
        }
        out = out.with(f.path, JsonArray(rows))
    }
    return out
}

private fun kotlinx.serialization.json.JsonElement?.objectsOrNumbers(): Set<Int> =
    (this as? JsonArray)?.mapNotNull { it.number().toInt().takeIf { n -> n in 1..7 } }?.toSet() ?: emptySet()
