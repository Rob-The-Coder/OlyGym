package olygym.app.platform

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import olygym.app.data.bool
import olygym.app.lib.backupFileName
import olygym.app.lib.todayISO

/*
 * "Auto-backup on changes": a dated snapshot in the Documents folder, so a sync app or a file
 * manager always has something recent to point at -- unlike the private mirror in files/, which
 * only this app can see. A port of writeAutoBackup in frontend/src/lib/mobile.js.
 */

object AutoBackup {

    private val json = Json { prettyPrint = true }

    /**
     * Write today's snapshot when the setting is on. Fire and forget on a thread of its own: this
     * runs off the finish tap, and a best-effort copy is not worth a frame. A failure is silent --
     * the private mirror still holds the data.
     */
    fun write(ctx: Context, S: JsonObject) {
        if (S.bool("autoBackup") != true) return
        val name = backupFileName(todayISO())
        val text = json.encodeToString(JsonObject.serializer(), S)
        val app = ctx.applicationContext
        Thread {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    writePublic(app, name, text)
                } else {
                    writeLegacy(app, name, text)
                }
            }
        }.start()
    }

    /**
     * Android 10 and later: a file in the real Documents folder through MediaStore, which needs no
     * permission and outlives an uninstall. The same day's file is overwritten rather than copied,
     * so a busy day leaves one snapshot.
     */
    private fun writePublic(ctx: Context, name: String, text: String) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val where = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?"
        val args = arrayOf(Environment.DIRECTORY_DOCUMENTS + "/", name)
        val existing = ctx.contentResolver
            .query(collection, arrayOf(MediaStore.MediaColumns._ID), where, args, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
        val target = if (existing != null) {
            ContentUris.withAppendedId(collection, existing)
        } else {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }
            ctx.contentResolver.insert(collection, values) ?: return
        }
        // "wt" truncates: today's file is replaced, never appended to.
        ctx.contentResolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray()) }
    }

    /**
     * Android 9 and older: the public Documents folder needs WRITE_EXTERNAL_STORAGE, which this app
     * never asks for, so the snapshot goes to the app's own external documents folder instead. A
     * file manager can still reach it under Android/data/.
     */
    private fun writeLegacy(ctx: Context, name: String, text: String) {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        File(dir, name).writeText(text)
    }
}
