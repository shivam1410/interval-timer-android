package com.shivam1410.intervaltimer

import java.time.Instant
import java.time.temporal.ChronoUnit

// Pure Toggl Track logic (no Android), unit-tested in TogglTest. TogglSync does the HTTP and storage.

/** Tag-mapping key for work blocks; activities use their own id. */
const val TOGGL_WORK = "work"
/** Mapping value meaning "don't send this one to Toggl". */
const val TOGGL_SKIP = "-"

/** What should be running in Toggl right now. [key] identifies it, so an unchanged phase isn't restarted. */
data class Track(val key: String, val description: String, val tags: List<String>, val start: Long)

/** Ambient sounds (Refresh) are background, not an activity: never sent. */
fun togglable(a: Activity) = a.group != "Refresh"

fun sessionKey(startedAt: Long, index: Int, what: String) = "$startedAt:$index:$what"

/**
 * Work blocks and break activities are tracked; prepare, pauses and ambient sounds aren't.
 * Tags come from [mapping] (picked in Settings); library items carry their own tag.
 */
fun desiredTrack(plan: List<Phase>, s: Session, chosen: Activity, mapping: Map<String, String>): Track? {
    if (!s.running(plan) || s.paused) return null
    fun tags(v: String?) = listOfNotNull(v?.takeIf { it.isNotBlank() && it != TOGGL_SKIP })
    return when (plan[s.index].kind) {
        Kind.PREP -> null
        Kind.WORK -> mapping[TOGGL_WORK].let { m ->
            if (m == TOGGL_SKIP) null else Track(sessionKey(s.startedAt, s.index, TOGGL_WORK), "Work", tags(m), s.runFrom)
        }
        Kind.ACTIVITY -> {
            val m = mapping[chosen.id]
            if (!togglable(chosen) || m == TOGGL_SKIP) null
            else Track(sessionKey(s.startedAt, s.index, chosen.id), chosen.name, tags(if (chosen.id.startsWith("lib:")) chosen.group else m), s.runFrom)
        }
    }
}

/** A time entry as this phone knows it; [remoteId] is set once Toggl has it. */
data class TogglEntry(
    val localId: String,
    val remoteId: Long?,
    val key: String,
    val description: String,
    val tags: List<String>,
    val start: Long,
    val stop: Long?,
    val stopSent: Boolean = false,
    val discard: Boolean = false,
)

/** Stops whatever runs when it no longer matches [desired] (at [now]) and adds the new entry. */
fun reconcile(entries: List<TogglEntry>, desired: Track?, now: Long, newId: String): List<TogglEntry> {
    val open = entries.lastOrNull { it.stop == null }
    if (desired != null && open?.key == desired.key) return entries
    val stopped = entries.map { if (it.stop == null) it.copy(stop = maxOf(now, it.start)) else it }
    return if (desired == null) stopped
    else stopped + TogglEntry(newId, null, desired.key, desired.description, desired.tags, desired.start, null)
}

/** Discard on the End dialog: that session's entries are deleted from Toggl too. */
fun discardSession(entries: List<TogglEntry>, startedAt: Long): List<TogglEntry> =
    entries.map { if (it.key.startsWith("$startedAt:")) it.copy(discard = true) else it }

enum class TogglOp { START, CREATE_DONE, STOP, DELETE, DONE }

/** The next API call for an entry, or null while it is running and already in Toggl. DONE = forget it. */
fun nextOp(e: TogglEntry): TogglOp? = when {
    e.discard -> if (e.remoteId != null) TogglOp.DELETE else TogglOp.DONE
    e.remoteId == null -> if (e.stop == null) TogglOp.START else TogglOp.CREATE_DONE
    e.stop == null -> null
    !e.stopSent -> TogglOp.STOP
    else -> TogglOp.DONE
}

/** RFC 3339 in UTC, whole seconds, as Toggl's API takes it. */
fun togglTime(ms: Long): String = Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).toString()
