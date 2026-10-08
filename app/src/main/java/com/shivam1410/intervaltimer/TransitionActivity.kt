package com.shivam1410.intervaltimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

/**
 * Shown over the lock screen when a phase changes (launched by the alert's full-screen intent).
 * Black and white, no buttons, ignores touches, and closes itself; the phone stays locked.
 */
class TransitionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val (big, line, until, small) = lines()
        setContent {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(SHOW_MS)
                finish()
            }
            Box(
                // Swallow every touch: this screen is for looking, not tapping.
                Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(big, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp)
                    Text(line, color = Color(0xFFDDDDDD), fontSize = 24.sp, textAlign = TextAlign.Center)
                    if (until.isNotEmpty()) Text(until, color = Color(0xFF9E9E9E), fontSize = 20.sp)
                    if (small.isNotEmpty()) Text(small, color = Color(0xFF6E6E6E), fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp))
                }
            }
        }
    }

    /** Text only (no emoji) so it stays black and white. */
    private fun lines(): List<String> {
        val s = Timer.session.value
        val st = Timer.settings.value
        val plan = Timer.plan
        if (s.done(plan)) {
            return if (s.quick != null) listOf("DONE", "${activity(s.quick).name} complete", "", "")
            else listOf("DONE", "Workday complete", "${fmtDur(s.workMs)} focused", "${s.cycles} of ${st.cycles} cycles")
        }
        if (!s.running(plan)) return listOf("", "", "", "")
        val p = plan[s.index]
        val until = if (s.paused) "Waiting for you" else "until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.endsAt))
        val a = activity(s.quick ?: st.activity)
        return when (p.kind) {
            Kind.WORK -> listOf("WORK", "${fmtDur(s.phaseMs)} focus", until, "Cycle ${p.cycle} of ${st.cycles}")
            Kind.PREP -> listOf("BREAK", "Prepare, then ${a.name}", until, "Lie down · put the phone aside")
            Kind.ACTIVITY -> listOf("BREAK", "${a.name} · ${fmtDur(s.phaseMs)}", until, if (s.quick == null) "Cycle ${p.cycle} of ${st.cycles}" else "")
        }
    }

    companion object {
        private const val SHOW_MS = 12_000L
    }
}
