package com.mathyusolutions.calarmi.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/**
 * Los audios que importa la persona (Calarmi Plus). Se copian al almacenamiento
 * de la app: el archivo original puede desaparecer y la alarma seguiría sonando.
 */
class ImportedTonesStore(storage: Context) {
    data class Tone(val id: String, val name: String)

    private val dir = File(storage.filesDir, "tones").apply { mkdirs() }
    private val prefs = storage.getSharedPreferences("imported_tones", Context.MODE_PRIVATE)

    fun all(): List<Tone> = prefs.all.entries
        .mapNotNull { (id, name) -> (name as? String)?.let { Tone(id, it) } }
        .filter { file(it.id) != null }
        .sortedBy { it.name.lowercase() }

    fun name(id: String): String? = prefs.getString(id, null)

    fun file(id: String): File? = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == id }

    /** Copia el audio elegido. Devuelve el id, o `null` si no se pudo leer. */
    fun import(context: Context, uri: Uri): Tone? = runCatching {
        val display = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "audio"
        val ext = display.substringAfterLast('.', "audio").take(5)
        val id = UUID.randomUUID().toString()
        context.contentResolver.openInputStream(uri)?.use { input ->
            File(dir, "$id.$ext").outputStream().use { input.copyTo(it) }
        } ?: return null
        val name = display.substringBeforeLast('.').ifBlank { display }
        prefs.edit().putString(id, name).apply()
        Tone(id, name)
    }.getOrNull()

    fun delete(id: String) {
        file(id)?.delete()
        prefs.edit().remove(id).apply()
    }
}
