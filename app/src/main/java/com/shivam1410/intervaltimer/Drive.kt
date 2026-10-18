package com.shivam1410.intervaltimer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * Google account + history sync to the hidden Drive app folder (drive.appdata: only this app can see it).
 * One AuthorizationClient consent gives the Drive token plus name/email/photo, so no extra sign-in SDK.
 * Built-in Android Auto Backup still runs independently.
 */
object Drive {
    private const val TAG = "Drive"
    private const val FILE = "history.json"
    // A file of its own, so app versions from before the day graph never overwrite the timelines.
    private const val TIMELINE_FILE = "timeline.json"
    private const val LIBRARY_FILE = "library.json"
    private const val AMBIENT_FILE = "ambient.json"
    private const val API = "https://www.googleapis.com"

    data class Profile(val name: String, val email: String, val photo: File?, val lastSync: Long) {
        /** "shivam garg" -> "Shivam Garg" (Google returns names as typed). */
        val displayName get() = name.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
    }

    val profile = MutableStateFlow<Profile?>(null)
    val status = MutableStateFlow("")
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val avatar get() = File(app.filesDir, "avatar.jpg")

    private val request = AuthorizationRequest.builder().setRequestedScopes(
        listOf(Scope("https://www.googleapis.com/auth/drive.appdata"), Scope("email"), Scope("profile"))
    ).build()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs.getString("email", null)?.let { email ->
            profile.value = Profile(prefs.getString("name", "").orEmpty(), email, avatar.takeIf { it.exists() }, prefs.getLong("lastSync", 0))
        }
    }

    /** Shows Google's account picker / consent if needed; the result comes back through [onResult]. */
    fun signIn(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        status.value = "Connecting…"
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { r ->
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                else onAuthorized(r)
            }
            .addOnFailureListener { fail("Sign-in failed", it) }
    }

    fun onResult(data: Intent?) {
        try {
            onAuthorized(Identity.getAuthorizationClient(app).getAuthorizationResultFromIntent(data))
        } catch (e: ApiException) {
            fail("Sign-in cancelled", e)
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        avatar.delete()
        profile.value = null
        status.value = ""
    }

    /** Silent sync when already signed in (app launch, after a day is recorded). */
    fun sync() {
        if (profile.value == null) return
        Identity.getAuthorizationClient(app).authorize(request)
            .addOnSuccessListener { r -> if (!r.hasResolution()) r.accessToken?.let { syncWith(it) } else status.value = "Tap to reconnect Google" }
            .addOnFailureListener { fail("Sync failed", it) }
    }

    private fun onAuthorized(r: AuthorizationResult) {
        val token = r.accessToken
        if (token == null) {
            status.value = "Sign-in failed: no access token"
            return
        }
        // Placeholder until userinfo answers; toGoogleSignInAccount() leaves name/email/photo blank.
        if (profile.value == null) {
            prefs.edit().putString("email", "").apply()
            profile.value = Profile("", "", null, prefs.getLong("lastSync", 0))
        }
        syncWith(token)
    }

    /** Name, email and photo from Google's userinfo endpoint (granted by the email + profile scopes). */
    private fun refreshProfile(token: String) {
        try {
            val me = JSONObject(call("GET", "$API/oauth2/v3/userinfo", token))
            val name = me.optString("name")
            val email = me.optString("email")
            prefs.edit().putString("name", name).putString("email", email).apply()
            val photoUrl = me.optString("picture").takeIf { it.isNotEmpty() }
            if (photoUrl != null) download(photoUrl.replace("=s96-c", "=s256-c"), avatar)
            profile.value = Profile(name, email, avatar.takeIf { it.exists() }, prefs.getLong("lastSync", 0))
        } catch (e: IOException) {
            Log.w(TAG, "Profile fetch failed", e)
        } catch (e: JSONException) {
            Log.w(TAG, "Unexpected userinfo response", e)
        }
    }

    // One sync at a time: two overlapping ones would both upload the same new library file.
    private fun syncWith(token: String) = thread { synchronized(Drive) { syncNow(token) } }

    private fun syncNow(token: String) {
        status.value = "Syncing…"
        refreshProfile(token)
        try {
            exchange(token, FILE) { remote ->
                val merged = merge(History.days.value, remote?.let(::decode).orEmpty())
                History.replaceAll(merged)
                encode(merged)
            }
            exchange(token, TIMELINE_FILE) { remote ->
                val merged = mergeTimelines(History.timelines.value, remote?.let(::decodeTimelines).orEmpty())
                History.replaceTimelines(merged)
                encodeTimelines(merged)
            }
            exchange(token, LIBRARY_FILE) { remote ->
                val merged = mergeLibrary(Library.items.value, decodeLibrary(remote?.let { JSONObject(it).optString("items") }))
                Library.replaceAll(merged, sync = false)
                JSONObject().put("items", encodeLibrary(merged)).toString()
            }
            exchange(token, AMBIENT_FILE) { remote ->
                val theirs = remote?.let { JSONObject(it) }?.let { o -> o.keys().asSequence().associateWith { o.getLong(it) } }.orEmpty()
                val merged = mergeAmbient(History.ambientRaw.value, theirs)
                History.replaceAmbient(merged)
                JSONObject().apply { merged.forEach { (k, v) -> put(k, v) } }.toString()
            }
            syncFiles(token)

            val now = System.currentTimeMillis()
            prefs.edit().putLong("lastSync", now).apply()
            profile.value = profile.value?.copy(lastSync = now)
            status.value = ""
        } catch (e: IOException) {
            fail("Sync failed", e)
        } catch (e: JSONException) {
            fail("Sync failed: unexpected Drive response", e)
        }
    }

    /** Downloads [name] from the app folder (null if it isn't there yet), merges via [merge], uploads the result. */
    private fun exchange(token: String, name: String, merge: (String?) -> String) {
        val q = URLEncoder.encode("name='$name'", "UTF-8")
        val found = JSONObject(call("GET", "$API/drive/v3/files?spaces=appDataFolder&fields=files(id)&q=$q", token)).getJSONArray("files")
        var id = if (found.length() > 0) found.getJSONObject(0).getString("id") else null
        val body = merge(id?.let { call("GET", "$API/drive/v3/files/$it?alt=media", token) })
        if (id == null) {
            val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
            id = JSONObject(call("POST", "$API/drive/v3/files?fields=id", token, meta.toString())).getString("id")
        }
        call("PATCH", "$API/upload/drive/v3/files/$id?uploadType=media", token, body)
    }

    /**
     * Library MP3/MP4s, as "lib-<id>.<ext>" in the app folder: upload what only this phone has,
     * download what only Drive has, and remove the files of deleted items from both.
     */
    private fun syncFiles(token: String) {
        val q = URLEncoder.encode("name contains 'lib-'", "UTF-8")
        val list = JSONObject(call("GET", "$API/drive/v3/files?spaces=appDataFolder&pageSize=1000&fields=files(id,name)&q=$q", token)).getJSONArray("files")
        val onDrive = (0 until list.length()).associate { list.getJSONObject(it).run { getString("name") to getString("id") } }
        Library.items.value.filter { it.isFile }.forEach { item ->
            val local = LibraryFiles.file(app, item.file)
            val id = onDrive[item.file]
            when {
                item.deleted -> {
                    id?.let { call("DELETE", "$API/drive/v3/files/$it", token) }
                    local.delete()
                }
                id == null && local.exists() -> upload(token, item.file, local)
                id != null && !local.exists() -> download(token, id, local)
            }
        }
    }

    /** Resumable upload: a session URL first, then the bytes streamed in one PUT (files can be hundreds of MB). */
    private fun upload(token: String, name: String, f: File) {
        val mime = LibraryFiles.mimeOf(name)
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val c = URL("$API/upload/drive/v3/files?uploadType=resumable").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c.setRequestProperty("X-Upload-Content-Type", mime)
        c.setRequestProperty("X-Upload-Content-Length", f.length().toString())
        c.outputStream.use { it.write(meta.toByteArray()) }
        expectOk(c)
        val session = c.getHeaderField("Location")?.takeIf { it.startsWith("$API/") } ?: throw IOException("Drive gave no upload URL")
        val put = URL(session).openConnection() as HttpURLConnection
        put.connectTimeout = 15_000
        put.readTimeout = 120_000
        put.requestMethod = "PUT"
        put.doOutput = true
        put.setFixedLengthStreamingMode(f.length())
        put.setRequestProperty("Content-Type", mime)
        f.inputStream().use { input -> put.outputStream.use { input.copyTo(it) } }
        expectOk(put)
        Log.i(TAG, "Uploaded $name")
    }

    /** Streams to a temp file first, so a dropped connection never leaves a half file that looks complete. */
    private fun download(token: String, id: String, dest: File) {
        val c = URL("$API/drive/v3/files/$id?alt=media").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 120_000
        c.setRequestProperty("Authorization", "Bearer $token")
        expectOk(c)
        val tmp = File(dest.path + ".part")
        c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(dest)) throw IOException("Couldn't save ${dest.name}")
        Log.i(TAG, "Downloaded ${dest.name}")
    }

    private fun expectOk(c: HttpURLConnection) {
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
            throw IOException("Drive HTTP $code ${err.take(200)}")
        }
    }

    private fun fail(msg: String, e: Exception) {
        Log.w(TAG, msg, e)
        status.value = "$msg: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun encode(days: List<Day>) = JSONObject().apply { days.forEach { put(it.date.toString(), it.encode()) } }.toString()

    private fun decode(json: String): List<Day> {
        val o = JSONObject(json)
        return o.keys().asSequence().mapNotNull { Day.decode(it, o.getString(it)) }.toList()
    }

    private fun encodeTimelines(all: Map<LocalDate, List<Segment>>) =
        JSONObject().apply { all.forEach { (date, segs) -> put(date.toString(), encodeSegments(segs)) } }.toString()

    private fun decodeTimelines(json: String): Map<LocalDate, List<Segment>> {
        val o = JSONObject(json)
        return o.keys().asSequence().mapNotNull { k ->
            runCatching { LocalDate.parse(k) }.getOrNull()?.let { it to decodeSegments(o.optString(k)) }
        }.toMap()
    }

    /** HttpURLConnection has no PATCH; Google APIs accept the method override header. */
    private fun call(method: String, url: String, token: String, body: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.requestMethod = if (method == "PATCH") "POST" else method
        if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
            throw IOException("Drive HTTP $code ${err.take(200)}")
        }
        return c.inputStream.use { it.readBytes().decodeToString() }
    }
}
