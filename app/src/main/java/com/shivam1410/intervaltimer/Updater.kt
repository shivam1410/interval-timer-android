package com.shivam1410.intervaltimer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.util.Log
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
    private var apk: File? = null

    fun check(ctx: Context) = thread {
        try {
            val release = JSONObject(fetch(API))
            val tag = release.getString("tag_name")
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@thread
            val assets = release.getJSONArray("assets")
            val url = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }?.getString("browser_download_url") ?: return@thread
            val f = File(ctx.cacheDir, "update-$tag.apk")
            if (!f.exists()) download(url, f)
            apk = f
            ready.value = tag
        } catch (e: IOException) {
            Log.w("Updater", "Update check failed", e)
        } catch (e: JSONException) {
            Log.w("Updater", "Unexpected release payload", e)
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
