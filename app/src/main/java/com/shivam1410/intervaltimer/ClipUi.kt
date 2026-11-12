package com.shivam1410.intervaltimer

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "ClipUi"

/**
 * A library clip played muted on a loop: videos in a VideoView (no audio focus taken, so focus music
 * keeps playing), GIFs / WebPs through ImageDecoder. Nothing shows until the file is on this phone.
 */
@Composable
fun LoopingClip(name: String, height: Dp = 200.dp) {
    val ctx = LocalContext.current
    val f = LibraryFiles.file(ctx, name)
    if (!f.exists()) return
    val box = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
    Box(box, contentAlignment = Alignment.Center) {
        if (isVideoFile(name)) AndroidView(
            factory = { c ->
                VideoView(c).apply {
                    setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
                    setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() }
                    setVideoPath(f.path)
                }
            },
            onRelease = { it.stopPlayback() },
        ) else {
            val drawable by produceState<Drawable?>(null, name) {
                value = withContext(Dispatchers.IO) {
                    try {
                        ImageDecoder.decodeDrawable(ImageDecoder.createSource(f))
                    } catch (e: IOException) {
                        Log.w(TAG, "Can't decode $name", e)
                        null
                    }
                }
            }
            AndroidView(
                factory = { c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { iv ->
                    iv.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; start() }
                },
            )
        }
    }
}

/** Opens the photo picker (videos and GIFs, no storage permission) and copies the pick in; [onPicked] gets its name. */
@Composable
fun rememberClipPicker(onPicked: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                onPicked(withContext(Dispatchers.IO) { LibraryFiles.importClip(ctx, uri) })
            } catch (e: IOException) {
                Log.w(TAG, "Clip import failed", e)
                Toast.makeText(ctx, "Couldn't add the clip: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}
