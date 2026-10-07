package com.shivam1410.intervaltimer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Top-right of home: Google photo + first name, or "Sign in". Tap opens the account dialog. */
@Composable
fun ProfileChip() {
    val p by Drive.profile.collectAsState()
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).clickable { open = true }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p, 32.dp)
        Text(
            p?.name?.substringBefore(' ')?.ifEmpty { null } ?: if (p == null) "Sign in" else "You",
            Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge,
        )
    }
    if (open) AccountDialog(p) { open = false }
}

@Composable
private fun Avatar(p: Drive.Profile?, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val bmp = remember(p?.photo) { p?.photo?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Box(Modifier.size(size).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
        when {
            bmp != null -> Image(bmp, null, Modifier.size(size), contentScale = ContentScale.Crop)
            p != null && p.name.isNotEmpty() -> Text(p.name.take(1).uppercase(), color = cs.onPrimary, fontWeight = FontWeight.SemiBold)
            else -> Icon(painterResource(R.drawable.ic_person), null, Modifier.size(size * 0.6f), tint = cs.onPrimary)
        }
    }
}

@Composable
private fun AccountDialog(p: Drive.Profile?, onClose: () -> Unit) {
    val status by Drive.status.collectAsState()
    val activity = LocalContext.current as? MainActivity
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Avatar(p, 64.dp) },
        title = { Text(p?.name?.ifEmpty { null } ?: if (p == null) "Back up to Google Drive" else p.email, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p == null) {
                    Text("Sign in with Google to keep your history and streaks in your Drive's private app folder and restore them on any phone.", textAlign = TextAlign.Center)
                } else {
                    Text(p.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val synced = if (p.lastSync == 0L) "Not synced yet"
                    else "Last synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.lastSync))
                    Text(synced, style = MaterialTheme.typography.bodySmall)
                }
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            if (p == null) Button({ activity?.signIn() }) { Text("Sign in with Google") }
            else Button({ Drive.sync() }) { Text("Sync now") }
        },
        dismissButton = {
            if (p != null) TextButton({ Drive.signOut(); onClose() }) { Text("Sign out") }
            else TextButton(onClose) { Text("Not now") }
        },
    )
}
