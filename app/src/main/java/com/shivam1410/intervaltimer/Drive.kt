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
import kotlin.concurrent.thread

/**
 * Google account + history sync to the hidden Drive app folder (drive.appdata: only this app can see it).
 * One AuthorizationClient consent gives the Drive token plus name/email/photo, so no extra sign-in SDK.
 * Built-in Android Auto Backup still runs independently.
 */
object Drive {
    private const val TAG = "Drive"
    private const val FILE = "history.json"
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

    private fun syncWith(token: String) = thread {
        status.value = "Syncing…"
        refreshProfile(token)
        try {
            val q = URLEncoder.encode("name='$FILE'", "UTF-8")
            val found = JSONObject(call("GET", "$API/drive/v3/files?spaces=appDataFolder&fields=files(id)&q=$q", token)).getJSONArray("files")
            var id = if (found.length() > 0) found.getJSONObject(0).getString("id") else null
            val remote = id?.let { decode(call("GET", "$API/drive/v3/files/$it?alt=media", token)) }.orEmpty()

            val merged = merge(History.days.value, remote)
            History.replaceAll(merged)

            if (id == null) {
                val meta = JSONObject().put("name", FILE).put("parents", org.json.JSONArray().put("appDataFolder"))
                id = JSONObject(call("POST", "$API/drive/v3/files?fields=id", token, meta.toString())).getString("id")
            }
            call("PATCH", "$API/upload/drive/v3/files/$id?uploadType=media", token, encode(merged))

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

    private fun fail(msg: String, e: Exception) {
        Log.w(TAG, msg, e)
        status.value = "$msg: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun encode(days: List<Day>) = JSONObject().apply { days.forEach { put(it.date.toString(), it.encode()) } }.toString()

    private fun decode(json: String): List<Day> {
        val o = JSONObject(json)
        return o.keys().asSequence().mapNotNull { Day.decode(it, o.getString(it)) }.toList()
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
