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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
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

/**
 * No 5-4-3-2-1 for your own items: the timer starts and the video / track opens right away
 * (YouTube or YouTube Music for links, "Play with…" for files), so it still counts in History.
 */
fun startLibraryItem(ctx: android.content.Context, item: LibraryItem) {
    Timer.startQuick("lib:${item.id}")
    if (item.isFile) LibraryFiles.play(ctx, item.file)?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show() }
    else openLink(ctx, item.url)
}

/** Home section with your own quick exercises: one card per row with thumbnail, title, tag and length; + to add. */
@Composable
fun MyLibrary() {
    val ctx = LocalContext.current
    val all by Library.items.collectAsState()
    val items = all.filterNot { it.deleted }
    var editing by remember { mutableStateOf<LibraryItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val tags = items.map { it.tag }.filter { it.isNotBlank() }.distinct()

    Text("My library", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> LibraryCard(item, onOpen = { startLibraryItem(ctx, item) }, onEdit = { editing = item }) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable(onClickLabel = "Add to library") { adding = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("➕", fontSize = 20.sp)
            Text("Add a YouTube link or a file", Modifier.padding(start = 12.dp), color = cs.onSurface)
        }
    }
    if (adding) LibraryDialog(null, existingTags = tags) { adding = false }
    editing?.let { LibraryDialog(it, existingTags = tags) { editing = null } }
}

/** Thumbnail, title, tag label and length; tap starts it, long-press edits. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(item: LibraryItem, onOpen: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val thumb by produceState<ImageBitmap?>(null, item.id, item.url, item.file) {
        value = withContext(Dispatchers.IO) { Thumbs.get(ctx, item) }?.asImageBitmap()
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer)
            .combinedClickable(onClickLabel = "Start ${item.title}", onLongClickLabel = "Edit", onLongClick = onEdit, onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text(item.toActivity().emoji, fontSize = 26.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.tag.isNotBlank()) Text(
                    item.tag, Modifier.clip(RoundedCornerShape(8.dp)).background(cs.secondaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer, maxLines = 1,
                )
                Text("${if (item.tag.isNotBlank()) "  " else ""}${item.minutes} min", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
    }
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
                        url, { url = it }, Modifier.fillMaxWidth(), label = { Text("YouTube link") }, singleLine = true, shape = FieldShape,
                        isError = url.isNotBlank() && !urlOk,
                        supportingText = if (url.isNotBlank() && !urlOk) ({ Text("Needs an https YouTube or YouTube Music link") }) else null,
                    )
                    if (item == null && url.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text("or", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                        // Same height and corners as the link field above.
                        OutlinedButton({ pick.launch(arrayOf("audio/*", "video/*")) }, Modifier.fillMaxWidth().height(56.dp), shape = FieldShape) {
                            Text("🎵  Pick an audio / video file")
                        }
                    }
                }
                OutlinedTextField(title, { title = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, shape = FieldShape)
                // Your Toggl tags first (the tag is what Toggl gets), then tags already used here.
                val toggl by TogglSync.tags.collectAsState()
                TagField(tag, (toggl + existingTags).filter { it != "Untagged" }.distinct()) { tag = it.take(30) }
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

/** Rounded corners shared by every box in the Add to library form. */
private val FieldShape = RoundedCornerShape(12.dp)

/** Tag: a dropdown of known tags that narrows as you type; typing something new makes a new tag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagField(value: String, choices: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = choices.filter { it.contains(value, ignoreCase = true) && it != value }.ifEmpty { if (value.isEmpty()) choices else emptyList() }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            value, { onChange(it); open = true },
            Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            label = { Text("Tag") }, singleLine = true, shape = FieldShape,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
        )
        if (shown.isNotEmpty()) ExposedDropdownMenu(open, { open = false }) {
            shown.forEach { t -> DropdownMenuItem({ Text(t) }, { onChange(t); open = false }) }
        }
    }
}
