package com.shivam1410.intervaltimer

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A built-in movement demo ([DEMOS]) looping on a Canvas in theme colours; nothing if [id] is unknown. */
@Composable
fun MovementDemo(id: String, height: Dp = 200.dp, clockMs: Long? = null) {
    val demo = DEMOS[id] ?: return
    val cs = MaterialTheme.colorScheme
    val (ink, line, accent) = Triple(cs.onSurface, cs.outline, cs.primary)
    var ms by remember { mutableLongStateOf(0L) }
    LaunchedEffect(id, clockMs == null) {
        if (clockMs != null) return@LaunchedEffect // driven from outside (the rep counter)
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { ms = it - start } // stops with the composable
    }
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = "Animation of the movement" }) {
        val s = minOf(size.width / DEMO_W, size.height / DEMO_H)
        val ox = (size.width - DEMO_W * s) / 2
        val oy = (size.height - DEMO_H * s) / 2
        fun o(p: Pt) = Offset(ox + p.x * s, oy + p.y * s)
        val w = 3f * s
        if (demo.wall) drawLine(line, o(Pt(WALL_X, 4f)), o(Pt(WALL_X, FLOOR_Y)), w, StrokeCap.Round)
        drawLine(line, o(Pt(12f, FLOOR_Y)), o(Pt(150f, FLOOR_Y)), w, StrokeCap.Round)
        demo.pads.forEach { (a, b) -> drawLine(line, o(a), o(b), w * 2, StrokeCap.Round) }
        demo.arrow?.let { (a, b) ->
            drawLine(accent, o(a), o(b), w, StrokeCap.Round)
            // Arrow head: two short strokes back from the tip at ±30°.
            val ang = kotlin.math.atan2(b.y - a.y, b.x - a.x)
            listOf(ang + 2.6f, ang - 2.6f).forEach { t -> drawLine(accent, o(b), o(Pt(b.x + 6f * kotlin.math.cos(t), b.y + 6f * kotlin.math.sin(t))), w, StrokeCap.Round) }
        }
        val pose = poseAt(demo, clockMs ?: ms)
        demo.bones.forEachIndexed { i, (a, b) ->
            val moving = i == demo.bones.lastIndex
            drawLine(if (moving) accent else ink, o(pose.getValue(a)), o(pose.getValue(b)), if (moving) w * 1.2f else w, StrokeCap.Round)
        }
        drawCircle(ink, HEAD_R * s, o(pose.getValue(Joint.HEAD)), style = Stroke(w))
    }
}

/** Your own clip if there is one, else the built-in demo, else nothing at all (no empty box). */
@Composable
fun ExerciseMedia(clip: String?, demo: String?, height: Dp = 200.dp) {
    when {
        !clip.isNullOrEmpty() -> LoopingClip(clip, height)
        !demo.isNullOrEmpty() -> MovementDemo(demo, height)
    }
}
