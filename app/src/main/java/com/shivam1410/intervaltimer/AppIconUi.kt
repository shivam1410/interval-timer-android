package com.shivam1410.intervaltimer

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Background and foreground drawables of each launcher icon, for the previews. */
private fun layers(id: String): Pair<Int, Int> = when (id) {
    "night" -> R.drawable.ic_launcher_night_bg to R.drawable.ic_launcher_night_fg
    "ripple" -> R.drawable.ic_launcher_ripple_bg to R.drawable.ic_launcher_ripple_fg
    "ringgong" -> R.drawable.ic_launcher_ringgong_bg to R.drawable.ic_launcher_ringgong_fg
    "interval" -> R.drawable.ic_launcher_interval_bg to R.drawable.ic_launcher_interval_fg
    "stretch" -> R.drawable.ic_launcher_stretch_bg to R.drawable.ic_launcher_stretch_fg
    "focus" -> R.drawable.ic_launcher_focus_bg to R.drawable.ic_launcher_focus_fg
    else -> R.drawable.ic_launcher_bg to R.drawable.ic_launcher_fg
}

/** The icon as the launcher shows it: both layers in a circle, the middle 72 of the 108 canvas. */
@Composable
fun AppIconPreview(id: String, size: Int) {
    val (bg, fg) = layers(id)
    val full = (size * 1.5f).dp
    Box(Modifier.size(size.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Image(painterResource(bg), null, Modifier.requiredSize(full))
        Image(painterResource(fg), null, Modifier.requiredSize(full))
    }
}

/** Settings › App icon: tap one to make it the launcher icon. */
@Composable
fun AppIconPage() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var current by remember { mutableStateOf(AppIcons.current(ctx)) }
    var changed by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        APP_ICONS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { o ->
                    val on = o.id == current
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                            .clickable(onClickLabel = "Use ${o.label}") { if (!on) { AppIcons.set(ctx, o.id); current = o.id; changed = true } }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(if (on) Modifier.border(3.dp, cs.primary, CircleShape).padding(5.dp) else Modifier.padding(8.dp)) { AppIconPreview(o.id, 64) }
                        Text((if (on) "✓ " else "") + o.label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = if (on) cs.primary else cs.onSurface)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            if (changed) "Changed. The launcher can take a few seconds; if the icon left your home screen, add it again from the app drawer."
            else "Your home screen and app drawer show the one you pick.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
    }
}
