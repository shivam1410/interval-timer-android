package com.shivam1410.intervaltimer

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.concurrent.thread

private const val SKETCH_SEED_RELEASES = "https://github.com/shivam1410/sketchseed/releases/latest"

/** Per-activity extra on the break card: guided steps, a play/open button, or the silence meter. */
@Composable
fun ActivityCue(a: Activity, elapsedMs: Long, paused: Boolean) {
    when {
        a.steps.isNotEmpty() -> StepGuide(a.steps, elapsedMs)
        a.id == "meditation" && !paused -> SilenceMeter()
    }
}

fun hasPrimaryAction(a: Activity) = a.tracks.isNotEmpty() || a.app != null

/** The activity's main button (play a track / open Sketch Seed), sized to sit in one row with Change and +10 min. */
@Composable
fun PrimaryAction(a: Activity, modifier: Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val solid = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary)
    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
    when {
        a.tracks.isNotEmpty() -> Button({ open(ctx, a.tracks.random()) }, modifier, colors = solid, contentPadding = pad) { Text("▶  ${a.playLabel}", maxLines = 1) }
        a.app != null -> Button({ launch(ctx, a.app) }, modifier, colors = solid, contentPadding = pad) {
            Text(if (installed(ctx, a.app)) "✏️  Open Sketch Seed" else "Get Sketch Seed", maxLines = 1)
        }
    }
}

private fun open(ctx: Context, url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun installed(ctx: Context, pkg: String) = ctx.packageManager.getLaunchIntentForPackage(pkg) != null

private fun launch(ctx: Context, pkg: String) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (intent != null) ctx.startActivity(intent) else open(ctx, SKETCH_SEED_RELEASES)
}

/** "Step 2 of 6 · Shoulder rolls" with a bar for the time left in that step. */
@Composable
private fun StepGuide(steps: List<Step>, elapsedMs: Long) {
    val (i, left) = stepAt(steps, elapsedMs) ?: return
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Step ${i + 1} of ${steps.size}", style = MaterialTheme.typography.labelLarge, color = cs.tertiary)
        Text(steps[i].name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
        LinearProgressIndicator(
            progress = { 1f - left / steps[i].sec.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = cs.tertiary, trackColor = cs.surfaceContainerHighest,
        )
        Row {
            Text("Next: ${steps[(i + 1) % steps.size].name}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text("0:%02d".format(left), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/**
 * Live mic level while the Silence screen is open. Only loudness is computed; no audio is kept.
 * Foreground only: listening with the screen off would need a microphone foreground service.
 */
@Composable
private fun SilenceMeter() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
        Button({ ask.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary)) {
            Text("🎙  Measure my silence")
        }
        return
    }
    val levels = remember { mutableStateListOf<Float>() } // last ~15 s for the graph
    var quiet by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var loudest by remember { mutableFloatStateOf(-90f) }
    DisposableEffect(Unit) {
        val stop = listen { db ->
            levels.add(db)
            if (levels.size > 150) levels.removeAt(0)
            total++
            if (db <= QUIET_DB) quiet++
            if (db > loudest) loudest = db
        }
        onDispose { stop() }
    }
    val share = if (total == 0) 1f else quiet.toFloat() / total
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Text(" quiet", Modifier.weight(1f).padding(bottom = 4.dp), color = cs.onSurfaceVariant)
            Text(levels.lastOrNull()?.let { "%.0f dB".format(it) } ?: "", color = cs.onSurfaceVariant)
        }
        val quietColor = cs.tertiary
        val loudColor = cs.error
        val lineColor = cs.outline
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val w = size.width / 150
            fun y(db: Float) = size.height * (1f - ((db + 90f) / 90f).coerceIn(0f, 1f))
            levels.forEachIndexed { i, db ->
                val top = y(db)
                drawRect(if (db <= QUIET_DB) quietColor else loudColor, Offset(i * w, top), Size(w * 0.7f, size.height - top))
            }
            drawLine(lineColor, Offset(0f, y(QUIET_DB)), Offset(size.width, y(QUIET_DB)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        Text(
            if (total == 0) "Listening…" else "Loudest moment %.0f dB · dashed line = quiet".format(loudest),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
    }
}

/** Reads the mic in 100 ms chunks on a background thread and reports dBFS on the main thread. */
@SuppressLint("MissingPermission") // checked by SilenceMeter before calling
private fun listen(onLevel: (Float) -> Unit): () -> Unit {
    val rate = 16_000
    val size = maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), rate / 10 * 2)
    val rec = try {
        AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
    } catch (e: IllegalArgumentException) {
        Log.e("Silence", "Mic unavailable", e)
        return {}
    }
    if (rec.state != AudioRecord.STATE_INITIALIZED) {
        Log.e("Silence", "Mic failed to initialize")
        rec.release()
        return {}
    }
    val running = java.util.concurrent.atomic.AtomicBoolean(true)
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    rec.startRecording()
    val t = thread {
        val buf = ShortArray(rate / 10)
        while (running.get()) {
            val n = rec.read(buf, 0, buf.size)
            if (n > 0) dbfs(buf, n).let { db -> main.post { if (running.get()) onLevel(db) } }
        }
    }
    return {
        running.set(false)
        t.join(300)
        rec.stop()
        rec.release()
    }
}

/** Power nap: near-black and still, so the screen can go off; the gong is the alarm. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NapScreen(s: Session) {
    val dim = Color(0xFF8A8A8A)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("💤", fontSize = 56.sp)
            Text("Sleep", color = dim, fontSize = 28.sp, fontWeight = FontWeight.Light)
            Text("Gong at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.endsAt))}", color = dim)
            Text(
                "Lock your phone.\nThe gong and vibration will wake you.",
                color = dim.copy(alpha = 0.7f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            )
            // Hold, not tap: the button appears where the Power nap chip was, so a stray tap must not end the nap.
            Text(
                "Hold to wake up now", color = dim,
                modifier = Modifier.padding(top = 24.dp).clip(RoundedCornerShape(20.dp))
                    .combinedClickable(onClick = {}, onLongClick = { Timer.skip() }).padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** 5-4-3-2-1-Start before a quick timer, so there's time to put the phone down. Back or Cancel aborts. */
@Composable
fun Countdown(id: String, onDone: () -> Unit) {
    val a = activity(id)
    val cs = MaterialTheme.colorScheme
    var n by remember(id) { mutableIntStateOf(5) }
    LaunchedEffect(id) {
        while (n > 0) {
            kotlinx.coroutines.delay(1000)
            n--
        }
        kotlinx.coroutines.delay(700) // let "Start" show
        Timer.startQuick(id)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(a.emoji, fontSize = 48.sp)
        Text(a.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text("${a.quickMin} min", color = cs.onSurfaceVariant)
        Box(Modifier.padding(vertical = 32.dp).size(220.dp).clip(CircleShape).background(cs.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(
                if (n > 0) "$n" else "Start",
                fontSize = if (n > 0) 112.sp else 48.sp, fontWeight = FontWeight.Light, color = cs.onTertiaryContainer,
            )
        }
        TextButton(onDone) { Text("Cancel") }
    }
}
