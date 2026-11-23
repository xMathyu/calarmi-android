package com.mathyusolutions.calarmi.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Las fotos de las alarmas y de las cuentas regresivas, como JPEG reducidos en
 * el almacenamiento de la app. En la base solo se guarda el nombre del archivo.
 */
class PhotoStore(storage: Context) {
    private val dir = File(storage.filesDir, "photos").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** Copia la imagen elegida, reducida a `maxSide` px. Devuelve el nombre del archivo. */
    fun import(context: Context, uri: Uri, maxSide: Int = 1080): String? = runCatching {
        val bitmap = decode(context, uri, maxSide) ?: return null
        val name = "${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    /** Guarda un JPEG que llegó de fuera (la foto de algo compartido). Devuelve el nombre del archivo. */
    fun save(jpeg: ByteArray): String? = runCatching {
        val name = "${UUID.randomUUID()}.jpg"
        file(name).writeBytes(jpeg)
        name
    }.getOrNull()

    fun delete(name: String) {
        file(name).delete()
    }

    private fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val scale = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
