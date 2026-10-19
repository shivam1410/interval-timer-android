package com.shivam1410.intervaltimer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/** Checks GitHub Releases on launch, downloads a newer APK in the background, installs on tap. */
object Updater {
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    val ready = MutableStateFlow<String?>(null) // tag of a downloaded, installable update
    val error = MutableStateFlow<String?>(null)
    val checking = MutableStateFlow(false)
    /** Tag being downloaded and 0..1 done; null when no download is running. */
    val progress = MutableStateFlow<Pair<String, Float>?>(null)
    private var apk: File? = null

    /** [manual]: the Settings button, which says how the check went; the launch check stays quiet. */
    fun check(ctx: Context, manual: Boolean = false) {
        if (checking.value || progress.value != null) return // one at a time; the UI already shows it
        checking.value = true
        thread { try { checkNow(ctx, manual) } finally { checking.value = false; progress.value = null } }
    }

    private fun checkNow(ctx: Context, manual: Boolean) {
        fun say(msg: String) {
            if (manual) Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
        }
        try {
            val release = JSONObject(fetch(API))
            val tag = release.getString("tag_name")
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return say("You're up to date (v${BuildConfig.VERSION_NAME})")
            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") }
                ?: return say("Update $tag has no app file yet; try again later")
            val url = asset.getString("browser_download_url")
            val f = File(ctx.cacheDir, "update-$tag.apk")
            if (!f.exists()) {
                progress.value = tag to 0f
                download(url, f, asset.optLong("size", -1)) { p -> progress.value = tag to p }
            }
            apk = f
            ready.value = tag
            say("Update $tag ready: tap Install")
        } catch (e: IOException) {
            Log.w("Updater", "Update check failed", e)
            say("Couldn't check for updates: no connection?")
        } catch (e: JSONException) {
            Log.w("Updater", "Unexpected release payload", e)
            say("Couldn't check for updates")
        }
    }

    fun install(ctx: Context) {
        val f = apk ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            )
            return
        }
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, f.length()).use { out ->
                f.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = PendingIntent.getBroadcast(
                ctx, 1, Intent(ctx, Receiver::class.java).setAction(Receiver.INSTALL_STATUS), PendingIntent.FLAG_MUTABLE
            )
            session.commit(status.intentSender)
        }
    }
}
