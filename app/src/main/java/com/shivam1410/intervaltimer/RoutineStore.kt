package com.shivam1410.intervaltimer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.LocalTime

/**
 * Your routines in shared prefs (Google backup carries them). First run seeds Morning, Leaving the house
 * and Evening. [all] keeps deletes as tombstones so a sync can't bring them back; the UI shows [visible].
 */
object Routines {
    val all = MutableStateFlow<List<Routine>>(emptyList())
    val visible get() = all.value.filterNot { it.deleted }
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("routines", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val stored = prefs.getString("items", null)
        all.value = if (stored == null) DEFAULT_ROUTINES else decodeRoutines(stored)
    }

    /** Finished just now: today gets this minute (tells you later that you already did it). */
    fun finish(id: String) {
        val now = LocalTime.now()
        update(id) { it.copy(done = it.done + (LocalDate.now() to now.hour * 60 + now.minute)) }
    }

    /** New routine when [r]'s id is unknown, otherwise an edit of it. */
    fun save(r: Routine) = replaceAll(mergeRoutines(all.value, listOf(r.copy(updatedAt = System.currentTimeMillis()))))

    fun delete(id: String) = update(id) { it.copy(deleted = true) }

    private fun update(id: String, change: (Routine) -> Routine) =
        replaceAll(all.value.map { if (it.id == id) change(it).copy(updatedAt = System.currentTimeMillis()) else it })

    /** After a local change, or merging with the Drive copy. */
    fun replaceAll(list: List<Routine>) {
        prefs.edit().putString("items", encodeRoutines(list)).apply()
        all.value = list
    }
}
