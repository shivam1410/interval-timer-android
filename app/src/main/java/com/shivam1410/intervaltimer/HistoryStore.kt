package com.shivam1410.intervaltimer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One SharedPreferences entry per day ("2026-10-07" -> "workMs,breakMs,cycles").
 * It lives in shared prefs, so Android's Google backup carries it with the settings.
 */
object History {
    val days = MutableStateFlow<List<Day>>(emptyList())
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        days.value = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { Day.decode(k, it) } }.sortedBy { it.date }
    }

    /** Adds a finished (or ended) session to the day it started on. */
    fun record(startedAt: Long, s: Session) {
        if (s.workMs + s.breakMs <= 0) return
        val date = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val add = Day(date, s.workMs, s.breakMs, s.cycles)
        val merged = days.value.firstOrNull { it.date == date }?.plus(add) ?: add
        prefs.edit().putString(date.toString(), merged.encode()).apply()
        days.value = (days.value.filter { it.date != date } + merged).sortedBy { it.date }
        Drive.sync()
    }

    /** After merging with the Drive copy. */
    fun replaceAll(all: List<Day>) {
        prefs.edit().apply { all.forEach { putString(it.date.toString(), it.encode()) } }.apply()
        days.value = all
    }

    fun today(): LocalDate = LocalDate.now()
}
