package app.papra.mobile.data

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

fun ContentResolver.getFileName(uri: Uri): String {
    var name: String? = null
    val cursor: Cursor? = query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                name = it.getString(index)
            }
        }
    }
    return name ?: "upload"
}

fun ContentResolver.getMimeType(uri: Uri): String? = getType(uri)

fun guessMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "")
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    return mime ?: "application/octet-stream"
}
