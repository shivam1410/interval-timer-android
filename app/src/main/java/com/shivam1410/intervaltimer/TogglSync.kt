package com.shivam1410.intervaltimer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Toggl Track: work blocks and break activities become time entries, started and stopped as the
 * phases change. Entries are queued in prefs and sent in order on one background thread, so a
 * phase change while offline reaches Toggl later with its real times.
 *
 * The API token is encrypted with an Android Keystore key (it never leaves this phone, isn't in
 * Drive sync or Google backup) and is never logged.
 */
object TogglSync {
    private const val TAG = "Toggl"
    private const val API = "https://api.track.toggl.com/api/v9"
    private const val KEY_ALIAS = "toggl-token"

    val connected = MutableStateFlow(false)
    val enabled = MutableStateFlow(false)
    val tags = MutableStateFlow<List<String>>(emptyList())
    /** Per activity id (and TOGGL_WORK): a Toggl tag, "" = no tag, TOGGL_SKIP = don't send. */
    val mapping = MutableStateFlow<Map<String, String>>(emptyMap())
    val status = MutableStateFlow("")

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("toggl", Context.MODE_PRIVATE)
    private val secret get() = app.getSharedPreferences("toggl_secret", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private var entries: List<TogglEntry> = emptyList()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        connected.value = secret.contains("token") && prefs.getLong("wid", 0) != 0L
        enabled.value = prefs.getBoolean("enabled", false)
        tags.value = prefs.getString("tags", null)?.split("\n")?.filter { it.isNotEmpty() }.orEmpty()
        mapping.value = prefs.all.filterKeys { it.startsWith("map_") }.map { (k, v) -> k.removePrefix("map_") to (v as? String).orEmpty() }.toMap()
        entries = decodeEntries(prefs.getString("entries", null))
        flush()
    }

    // ---------- settings ----------

    /** Checks the token against /me, keeps the default workspace and the tag list. */
    fun connect(token: String) {
        val t = token.trim()
        if (t.isEmpty()) return
        status.value = "Connecting…"
        worker.execute {
            try {
                val me = JSONObject(call("GET", "/me", t))
                val wid = me.getLong("default_workspace_id")
                saveToken(t)
                prefs.edit().putLong("wid", wid).putBoolean("enabled", true).apply()
                enabled.value = true
                connected.value = true
                loadTags(t, wid)
                status.value = ""
            } catch (e: IOException) {
                status.value = if (e.message?.contains("HTTP 401") == true || e.message?.contains("HTTP 403") == true) "That API token was not accepted" else "Couldn't reach Toggl: ${e.message}"
            } catch (e: JSONException) {
                Log.w(TAG, "Unexpected /me response", e)
                status.value = "Unexpected answer from Toggl"
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Couldn't store the token", e)
                status.value = "Couldn't store the token on this phone"
            }
        }
    }

    fun disconnect() {
        secret.edit().clear().apply()
        prefs.edit().clear().apply()
        entries = emptyList()
        connected.value = false
        enabled.value = false
        tags.value = emptyList()
        mapping.value = emptyMap()
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        enabled.value = on
    }

    fun setMapping(id: String, value: String) {
        prefs.edit().putString("map_$id", value).apply()
        mapping.value = mapping.value + (id to value)
    }

    fun refreshTags() = worker.execute {
        try {
            loadTags(token() ?: return@execute, prefs.getLong("wid", 0))
            status.value = ""
        } catch (e: IOException) {
            status.value = "Couldn't load tags: ${e.message}"
        } catch (e: JSONException) {
            status.value = "Unexpected answer from Toggl"
        } catch (e: GeneralSecurityException) {
            status.value = "Reconnect Toggl: the stored token can't be read"
        }
    }

    private fun loadTags(token: String, wid: Long) {
        // A workspace without tags answers "null" rather than "[]".
        val raw = call("GET", "/workspaces/$wid/tags", token)
        val arr = if (raw.isBlank() || raw.trim() == "null") JSONArray() else JSONArray(raw)
        val names = (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }.sortedBy { it.lowercase() }
        prefs.edit().putString("tags", names.joinToString("\n")).apply()
        tags.value = names
    }

    // ---------- sessions ----------

    /** Called on every session change (Timer.commit); only phase/activity changes produce API calls. */
    fun onSession(plan: List<Phase>, s: Session, chosen: Activity) {
        if (!connected.value || !enabled.value) return
        val now = System.currentTimeMillis()
        // A mapped tag deleted in Toggl since is left off; sending it would quietly re-create it.
        val known = tags.value
        val map = mapping.value.mapValues { (_, v) -> if (v == TOGGL_SKIP || v in known) v else "" }
        update(reconcile(entries, desiredTrack(plan, s, chosen, map), now, UUID.randomUUID().toString()))
    }

    /** End -> Discard: that session's entries are removed from Toggl too. */
    fun discard(startedAt: Long) {
        if (!connected.value) return
        update(discardSession(entries, startedAt))
    }

    @Synchronized
    private fun update(next: List<TogglEntry>) {
        if (next == entries) return
        entries = next
        prefs.edit().putString("entries", encodeEntries(next)).apply()
        flush()
    }

    /** Sends what's pending, oldest first; stops at the first network error and retries on the next change or app start. */
    fun flush() = worker.execute {
        val token = try { token() } catch (e: GeneralSecurityException) { Log.e(TAG, "Token unreadable", e); null } ?: return@execute
        val wid = prefs.getLong("wid", 0)
        for (e in synchronized(this) { entries }) {
            val op = nextOp(e) ?: continue
            try {
                // Results are applied to the entry as it is *now*: a phase change may have stopped or
                // discarded it while the call was in flight.
                when (op) {
                    TogglOp.START -> create(token, wid, e, stop = null).let { id -> replace(e.localId) { it.copy(remoteId = id) } }
                    TogglOp.CREATE_DONE -> create(token, wid, e, e.stop).let { id -> replace(e.localId) { it.copy(remoteId = id, stopSent = true) } }
                    TogglOp.STOP -> { stop(token, wid, e); replace(e.localId) { it.copy(stopSent = true) } }
                    TogglOp.DELETE -> { call("DELETE", "/workspaces/$wid/time_entries/${e.remoteId}", token); replace(e.localId) { null } }
                    TogglOp.DONE -> replace(e.localId) { null }
                }
            } catch (ex: IOException) {
                val code = Regex("HTTP (\\d{3})").find(ex.message.orEmpty())?.groupValues?.get(1)?.toInt()
                if (code != null && code in 400..499 && code != 429) {
                    // Rejected for good (e.g. deleted in Toggl meanwhile): drop it instead of retrying forever.
                    Log.w(TAG, "Toggl rejected ${op.name}: $code", ex)
                    replace(e.localId) { null }
                } else {
                    Log.w(TAG, "Toggl unreachable, will retry", ex)
                    return@execute
                }
            } catch (ex: JSONException) {
                Log.w(TAG, "Unexpected Toggl response", ex)
                return@execute
            }
        }
    }

    @Synchronized
    private fun replace(localId: String, change: (TogglEntry) -> TogglEntry?) {
        entries = entries.mapNotNull { if (it.localId == localId) change(it) else it }
        prefs.edit().putString("entries", encodeEntries(entries)).apply()
    }

    private fun create(token: String, wid: Long, e: TogglEntry, stop: Long?): Long {
        val body = JSONObject()
            .put("created_with", "Interval Timer")
            .put("workspace_id", wid)
            .put("description", e.description)
            .put("tags", JSONArray(e.tags))
            .put("start", togglTime(e.start))
            .put("duration", if (stop == null) -1 else (stop - e.start) / 1000)
        if (stop != null) body.put("stop", togglTime(stop))
        return JSONObject(call("POST", "/workspaces/$wid/time_entries", token, body.toString())).getLong("id")
    }

    /** PUT with explicit times (not /stop, which uses "now"), so a late flush keeps the real end. */
    private fun stop(token: String, wid: Long, e: TogglEntry) {
        val stop = e.stop ?: return
        val body = JSONObject().put("start", togglTime(e.start)).put("stop", togglTime(stop)).put("duration", (stop - e.start) / 1000)
        call("PUT", "/workspaces/$wid/time_entries/${e.remoteId}", token, body.toString())
    }

    private fun call(method: String, path: String, token: String, body: String? = null): String {
        val c = URL(API + path).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.requestMethod = method
        val auth = Base64.encodeToString("$token:api_token".toByteArray(), Base64.NO_WRAP)
        c.setRequestProperty("Authorization", "Basic $auth")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
            throw IOException("Toggl HTTP $code ${err.take(200)}")
        }
        return c.inputStream.use { it.readBytes().decodeToString() }
    }

    // ---------- token storage ----------

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun saveToken(token: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(token.toByteArray())
        secret.edit()
            .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(enc, Base64.NO_WRAP))
            .apply()
    }

    private fun token(): String? {
        val iv = secret.getString("iv", null) ?: return null
        val enc = secret.getString("token", null) ?: return null
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return c.doFinal(Base64.decode(enc, Base64.NO_WRAP)).decodeToString()
    }

    // ---------- queue storage ----------

    private fun encodeEntries(all: List<TogglEntry>) = JSONArray(all.map { e ->
        JSONObject().put("l", e.localId).put("r", e.remoteId ?: JSONObject.NULL).put("k", e.key).put("d", e.description)
            .put("t", JSONArray(e.tags)).put("s", e.start).put("e", e.stop ?: JSONObject.NULL)
            .put("ss", e.stopSent).put("x", e.discard)
    }).toString()

    private fun decodeEntries(json: String?): List<TogglEntry> = try {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).run {
                val tags = getJSONArray("t")
                TogglEntry(
                    getString("l"), if (isNull("r")) null else getLong("r"), getString("k"), getString("d"),
                    (0 until tags.length()).map(tags::getString), getLong("s"), if (isNull("e")) null else getLong("e"),
                    optBoolean("ss"), optBoolean("x"),
                )
            }
        }
    } catch (e: JSONException) {
        Log.e(TAG, "Dropping unreadable Toggl queue", e)
        emptyList()
    }
}
