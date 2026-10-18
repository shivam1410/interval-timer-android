package com.shivam1410.intervaltimer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * Your own quick exercises, in shared prefs (so Android's Google backup carries them) and in
 * library.json on Drive. [items] holds deletes too; the UI shows [visible].
 */
object Library {
    val items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val visible get() = items.value.filterNot { it.deleted }
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("library", Context.MODE_PRIVATE)

    /** Before Timer.init: a quick timer running from the library needs its item to rebuild the plan. */
    fun init(ctx: Context) {
        app = ctx.applicationContext
        publish(decodeLibrary(prefs.getString("items", null)))
    }

    /** New item when [id] is null, otherwise an edit of that item. */
    fun save(id: String?, title: String, url: String, tag: String, minutes: Int) {
        require(isYouTubeUrl(url)) { "Not a YouTube link" }
        val item = LibraryItem(id ?: UUID.randomUUID().toString().take(8), title.trim(), url.trim(), tag.trim(), minutes, System.currentTimeMillis())
        replaceAll(mergeLibrary(items.value, listOf(item)))
    }

    fun delete(id: String) {
        val item = items.value.firstOrNull { it.id == id } ?: return
        replaceAll(mergeLibrary(items.value, listOf(item.copy(deleted = true, updatedAt = System.currentTimeMillis()))))
    }

    /** After a local change or merging with the Drive copy. */
    fun replaceAll(all: List<LibraryItem>, sync: Boolean = true) {
        prefs.edit().putString("items", encodeLibrary(all)).apply()
        publish(all)
        if (sync) Drive.sync()
    }

    private fun publish(all: List<LibraryItem>) {
        items.value = all
        extraActivities = all.filterNot { it.deleted }.map { it.toActivity() }
    }
}
