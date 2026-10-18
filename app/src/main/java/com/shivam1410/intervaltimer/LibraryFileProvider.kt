package com.shivam1410.intervaltimer

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider

/**
 * FileProvider for library files that reports your title as the display name (e.g. "Singing bowl.wav"
 * instead of "lib-8be56fd9.wav"), so the player you pick shows it.
 */
class LibraryFileProvider : FileProvider() {
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val base = super.query(uri, projection, selection, selectionArgs, sortOrder)
        val fileName = uri.lastPathSegment ?: return base
        val title = Library.items.value.firstOrNull { it.file == fileName }?.title ?: return base
        val nameCol = base.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameCol < 0 || !base.moveToFirst()) return base
        // Same columns and values, only the name swapped.
        return MatrixCursor(base.columnNames, 1).apply {
            addRow(Array<Any?>(base.columnCount) { i ->
                when {
                    i == nameCol -> playerName(title, fileName)
                    base.getType(i) == Cursor.FIELD_TYPE_INTEGER -> base.getLong(i)
                    else -> base.getString(i)
                }
            })
        }.also { base.close() }
    }
}
