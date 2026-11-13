package com.shivam1410.intervaltimer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** One launcher icon you can pick in Settings › App icon; [id] names its drawables (ic_launcher_<id>_*). */
data class AppIconOption(val id: String, val label: String)

val APP_ICONS = listOf(
    AppIconOption("classic", "Gong (classic)"),
    AppIconOption("night", "Gong at night"),
    AppIconOption("ripple", "Gong ripples"),
    AppIconOption("ringgong", "Gong in the ring"),
    AppIconOption("interval", "Interval ring"),
    AppIconOption("stretch", "Stretch in a ring"),
    AppIconOption("focus", "Focus dot"),
)

/** The manifest <activity-alias> that shows icon [id] in the launcher ("IconRinggong"). */
fun iconAlias(id: String) = "com.shivam1410.intervaltimer.Icon" + id.replaceFirstChar { it.uppercase() }

/**
 * Side-effect boundary for the launcher icon: exactly one alias is enabled. Switching keeps the app running;
 * some launchers drop the home-screen shortcut and you add the new one from the app drawer.
 */
object AppIcons {
    fun current(ctx: Context): String = APP_ICONS.firstOrNull { o ->
        when (ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx, iconAlias(o.id)))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> o.id == "classic" // the manifest default
            else -> false
        }
    }?.id ?: "classic"

    fun set(ctx: Context, id: String) {
        val pm = ctx.packageManager
        // Enable the new one first so there's never a moment with no launcher entry.
        pm.setComponentEnabledSetting(ComponentName(ctx, iconAlias(id)), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        APP_ICONS.filter { it.id != id }.forEach {
            pm.setComponentEnabledSetting(ComponentName(ctx, iconAlias(it.id)), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
