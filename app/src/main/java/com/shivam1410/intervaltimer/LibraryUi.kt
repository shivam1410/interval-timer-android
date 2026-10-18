package com.shivam1410.intervaltimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Home section with your own quick exercises, grouped by tag; tap to start, long-press to edit, + to add. */
@Composable
fun MyLibrary(onQuick: (String) -> Unit) {
    val all by Library.items.collectAsState()
    val items = all.filterNot { it.deleted }
    var editing by remember { mutableStateOf<LibraryItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Text("My library", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    val groups = items.groupBy { it.tag.ifBlank { "Untagged" } }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // null = the Add tile, placed after the last group's items so it doesn't take a row of its own.
        val rows: List<Pair<String?, List<LibraryItem?>>> =
            groups.entries.map { (tag, list) -> tag to list.toList<LibraryItem?>() }.ifEmpty { listOf(null to emptyList()) }
        rows.forEachIndexed { i, (tag, list) ->
            if (groups.size > 1 && tag != null) Text(tag, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            TileGrid(if (i == rows.lastIndex) list + null else list) { item ->
                if (item == null) Tile("➕", "Add", "YouTube link", cs.surfaceContainerHigh, cs.onSurface, cs.onSurfaceVariant, "Add to library") { adding = true }
                else Tile(item.toActivity().emoji, item.title, "${item.minutes} min", cs.surfaceContainer, cs.onSurface, cs.onSurfaceVariant, "Start ${item.title}",
                    onLongClick = { editing = item }) { onQuick("lib:${item.id}") }
            }
        }
    }
    if (adding) LibraryDialog(null, existingTags = groups.keys.toList()) { adding = false }
    editing?.let { LibraryDialog(it, existingTags = groups.keys.toList()) { editing = null } }
}

/** Add ([item] null) or edit a library item. Only https YouTube links can be saved. */
@Composable
fun LibraryDialog(
    item: LibraryItem?, existingTags: List<String>, initialUrl: String = "", initialTitle: String = "",
    initialFile: String = "", newId: String? = null, onDismiss: () -> Unit,
) {
    val file = item?.file ?: initialFile
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var url by remember { mutableStateOf(item?.url ?: initialUrl) }
    // Keyed on initialTitle: the shared link's title arrives from oEmbed a moment after the dialog opens.
    var title by remember(initialTitle) { mutableStateOf(item?.title ?: initialTitle) }
    var tag by remember { mutableStateOf(item?.tag.orEmpty()) }
    var minutes by remember { mutableStateOf(item?.minutes ?: 10) }
    val urlOk = isYouTubeUrl(url)
    val ok = (file.isNotEmpty() || urlOk) && title.isNotBlank()
    // A file shared in but not saved isn't kept.
    val cancel = { if (item == null && initialFile.isNotEmpty()) LibraryFiles.file(ctx, initialFile).delete(); onDismiss() }

    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(if (item == null) "Add to library" else "Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (file.isNotEmpty()) Text("🎵 ${if (file.endsWith(".mp4")) "Video" else "Audio"} file · backed up to your Drive", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(), label = { Text("YouTube link") }, singleLine = true,
                    isError = url.isNotBlank() && !urlOk,
                    supportingText = { if (url.isNotBlank() && !urlOk) Text("Needs an https YouTube or YouTube Music link") },
                )
                OutlinedTextField(title, { title = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                OutlinedTextField(tag, { tag = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Tag") }, singleLine = true)
                // Your Toggl tags first (the tag is what Toggl gets), then tags already used here.
                val toggl by TogglSync.tags.collectAsState()
                val choices = (toggl + existingTags).filter { it != "Untagged" }.distinct()
                if (choices.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { t -> AssistChip({ tag = t }, { Text(t) }) }
                }
                Stepper("Length", minutes, "min", 1..120) { minutes = it }
            }
        },
        confirmButton = {
            TextButton({ Library.save(item?.id ?: newId, title, url, tag, minutes, file); onDismiss() }, enabled = ok) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (item != null) TextButton(
                    { Library.delete(item.id); onDismiss() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
                TextButton(cancel) { Text("Cancel") }
            }
        },
    )
}
