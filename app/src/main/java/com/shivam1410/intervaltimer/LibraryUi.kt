package com.shivam1410.intervaltimer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
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

/**
 * Add ([item] null) or edit a library item: an https YouTube link, or an audio/video file picked here or
 * shared in. Title and length fill themselves in (from YouTube, or the file) until you change them.
 */
@Composable
fun LibraryDialog(
    item: LibraryItem?, existingTags: List<String>, initialUrl: String = "", initialTitle: String = "",
    initialFile: String = "", newId: String? = null, initialMinutes: Int? = null, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(item?.url ?: initialUrl) }
    var title by remember { mutableStateOf(item?.title ?: initialTitle) }
    var tag by remember { mutableStateOf(item?.tag.orEmpty()) }
    var minutes by remember { mutableStateOf(item?.minutes ?: initialMinutes ?: 10) }
    var minutesSet by remember { mutableStateOf(item != null || initialMinutes != null) } // you picked it, or the file told us
    var file by remember { mutableStateOf(item?.file ?: initialFile) }
    var id by remember { mutableStateOf(item?.id ?: newId) }
    var busy by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val urlOk = isYouTubeUrl(url)
    val ok = (file.isNotEmpty() || urlOk) && title.isNotBlank() && busy.isEmpty()
    // A file brought in here (picked or shared) but not saved isn't kept.
    val cancel = { if (item == null && file.isNotEmpty()) LibraryFiles.file(ctx, file).delete(); onDismiss() }

    // Pasted or shared link: look up its title and length once typing settles.
    LaunchedEffect(url) {
        if (item != null || file.isNotEmpty() || !urlOk) return@LaunchedEffect
        delay(500)
        busy = "Looking up the video…"
        if (title.isBlank()) withContext(Dispatchers.IO) { YouTubeInfo.title(url) }?.let { if (title.isBlank()) title = it }
        if (!minutesSet) withContext(Dispatchers.IO) { YouTubeInfo.lengthSeconds(url) }?.let { minutes = minutesFor(it * 1000L) }
        busy = ""
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val newFileId = id ?: UUID.randomUUID().toString().take(8)
        busy = "Adding file…"
        error = ""
        scope.launch {
            try {
                val (f, ms) = withContext(Dispatchers.IO) {
                    val f = LibraryFiles.import(ctx, uri, ctx.contentResolver.getType(uri), newFileId)
                    f to LibraryFiles.durationMs(ctx, f.name)
                }
                file = f.name
                id = newFileId
                if (title.isBlank()) title = f.title
                if (!minutesSet && ms != null) minutes = minutesFor(ms)
            } catch (e: IOException) {
                error = "Couldn't add the file: ${e.message}"
            } catch (e: SecurityException) {
                error = "Couldn't read that file"
            } finally {
                busy = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(if (item == null) "Add to library" else "Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (file.isNotEmpty()) Text("${if (isVideoFile(file)) "🎬 Video" else "🎵 Audio"} file · backed up to your Drive", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    OutlinedTextField(
                        url, { url = it }, Modifier.fillMaxWidth(), label = { Text("YouTube link") }, singleLine = true,
                        isError = url.isNotBlank() && !urlOk,
                        supportingText = { if (url.isNotBlank() && !urlOk) Text("Needs an https YouTube or YouTube Music link") },
                    )
                    if (item == null && url.isBlank()) OutlinedButton({ pick.launch(arrayOf("audio/*", "video/*")) }, Modifier.fillMaxWidth()) {
                        Text("🎵  Or pick an audio / video file")
                    }
                }
                OutlinedTextField(title, { title = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                OutlinedTextField(tag, { tag = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Tag") }, singleLine = true)
                // Your Toggl tags first (the tag is what Toggl gets), then tags already used here.
                val toggl by TogglSync.tags.collectAsState()
                val choices = (toggl + existingTags).filter { it != "Untagged" }.distinct()
                if (choices.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { t -> AssistChip({ tag = t }, { Text(t) }) }
                }
                Stepper("Length", minutes, "min", 1..120) { minutes = it; minutesSet = true }
                if (busy.isNotEmpty()) Text(busy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotEmpty()) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton({ Library.save(id, title, url, tag, minutes, file); onDismiss() }, enabled = ok) { Text("Save") }
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
