package com.shivam1410.intervaltimer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URLDecoder
import java.net.URLEncoder

// Workday cues: a one-line prompt at the start of the day, each break (rotating) and the end.
// Pure logic first (unit-tested in CueTest), then the small store that keeps your edited lists.

data class CueLists(
    val on: Boolean = true,
    val start: List<String> = listOf("Pick today's top 3"),
    val breaks: List<String> = listOf(
        "Drink water", "Stand up and stretch", "Look out a window for 20 s", "Refill your bottle", "Shoulders down, unclench your jaw",
    ),
    val end: List<String> = listOf("Write tomorrow's first task", "Close the laptop"),
)

/**
 * The cue for workday phase [p]: the start list on the first work block, break *n* gets the break list's
 * *n*-th line (wrapping round), nothing on later work blocks, quick timers or when cues are off.
 */
fun phaseCue(p: Phase, quick: Boolean, lists: CueLists): String? {
    if (quick || !lists.on) return null
    return when (p.kind) {
        Kind.WORK -> if (p.cycle == 1) lists.start.filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null } else null
        Kind.PREP, Kind.ACTIVITY -> lists.breaks.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { it[(p.cycle - 1).mod(it.size)] }
    }
}

/** The end-of-day cue: the whole (short) end list as one line, or null. */
fun endCue(lists: CueLists): String? = if (!lists.on) null else lists.end.filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

/** Four lines: on, then each list's lines URL-encoded and joined by "|". */
fun encodeCues(c: CueLists): String = listOf(
    if (c.on) "1" else "0",
    c.start.joinToString("|", transform = ::enc), c.breaks.joinToString("|", transform = ::enc), c.end.joinToString("|", transform = ::enc),
).joinToString("\n")

fun decodeCues(text: String?): CueLists {
    val l = text?.split("\n") ?: return CueLists()
    if (l.size != 4) return CueLists()
    fun list(s: String) = s.split("|").filter { it.isNotEmpty() }.map(::dec)
    return CueLists(l[0] == "1", list(l[1]), list(l[2]), list(l[3]))
}

/** Your cue lists, in shared prefs (Google backup carries them). */
object Cues {
    val lists = MutableStateFlow(CueLists())
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("cues", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        lists.value = decodeCues(prefs.getString("lists", null))
    }

    fun save(c: CueLists) {
        prefs.edit().putString("lists", encodeCues(c)).apply()
        lists.value = c
    }
}
