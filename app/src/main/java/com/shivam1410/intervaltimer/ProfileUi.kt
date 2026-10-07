package com.shivam1410.intervaltimer

import androidx.compose.runtime.getValue
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Top-right of home: round Google photo (or person icon when signed out). Opens Settings. */
@Composable
fun ProfileChip(onClick: () -> Unit) {
    val p by Drive.profile.collectAsState()
    Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Settings", onClick = onClick)) { Avatar(p, 44.dp) }
}

@Composable
fun Avatar(p: Drive.Profile?, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val bmp = remember(p?.photo) { p?.photo?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Box(Modifier.size(size).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
        when {
            bmp != null -> Image(bmp, null, Modifier.size(size), contentScale = ContentScale.Crop)
            p != null && p.name.isNotEmpty() -> Text(p.displayName.take(1), color = cs.onPrimary, fontWeight = FontWeight.SemiBold)
            else -> Icon(painterResource(R.drawable.ic_person), null, Modifier.size(size * 0.6f), tint = cs.onPrimary)
        }
    }
}

