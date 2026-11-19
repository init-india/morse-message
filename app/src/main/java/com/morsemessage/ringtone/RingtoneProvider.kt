package com.morsemessage.ringtone

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the Morse ringtone files to the phone's call system, read-only.
 *
 * Why not put them in the shared Ringtones folder? Files there survive an uninstall, so contacts
 * would keep ringing in Morse after the app is gone. These files live in the app's private storage
 * instead, so uninstalling removes them together with this provider. The contact's ringtone link
 * then no longer opens and Android falls back to the phone's own ringtone.
 *
 * File names contain a random per-install salt, so other apps can't guess them.
 */
class RingtoneProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = MIME

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read-only")
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** Some players ask for a title or size before playing; answer whatever columns they request. */
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val file = fileFor(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols, 1).apply {
            addRow(cols.map { col ->
                when (col) {
                    OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE -> "Morse ringtone"
                    OpenableColumns.SIZE -> file.length()
                    MediaStore.MediaColumns.MIME_TYPE -> MIME
                    MediaStore.MediaColumns._ID -> 1L
                    else -> null
                }
            })
        }
    }

    private fun fileFor(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!NAME_PATTERN.matches(name)) throw FileNotFoundException(name)
        val file = File(dir(requireNotNull(context)), name)
        if (!file.exists()) throw FileNotFoundException(name)
        return file
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        const val MIME = "audio/x-wav"
        private val NAME_PATTERN = Regex("[a-f0-9]{40}\\.wav")

        fun dir(context: Context): File = File(context.filesDir, "ringtones").apply { mkdirs() }

        fun uriFor(context: Context, fileName: String): Uri =
            Uri.parse("content://${context.packageName}.ringtones/$fileName")
    }
}
