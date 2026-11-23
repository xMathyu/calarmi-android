package com.mathyusolutions.calarmi.countdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.mathyusolutions.calarmi.CalarmiApp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * La foto de fondo de una cuenta regresiva. Ya llega reducida (`PhotoStore`
 * la guarda a 1080 px), pero decodificarla en cada tarjeta al hacer scroll se
 * nota: se guarda decodificada en memoria, como el `CountdownPhoto` de iOS.
 */
object CountdownPhoto {
    /** Por bytes: unas cuantas fotos de tarjeta caben sin apretar la memoria. */
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Decodifica `file` con el lado mayor cerca de `maxSide` (nunca menos de la
     * mitad). La clave incluye la fecha de modificación por si el archivo cambia.
     */
    fun load(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val key = "${file.name}-${file.lastModified()}-$maxSide"
        cache.get(key)?.let { return it }
        val bitmap = decode(file, maxSide) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    /** Solo lo que ya está decodificado (sin tocar el disco más que para la fecha). */
    fun cached(file: File, maxSide: Int): Bitmap? = cache.get("${file.name}-${file.lastModified()}-$maxSide")

    fun decode(file: File, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /**
     * Una copia propia de la foto de otra cosa (una alarma): si compartieran el
     * archivo, borrar cualquiera de las dos dejaría a la otra sin foto.
     */
    suspend fun copy(context: Context, name: String): String? = withContext(Dispatchers.IO) {
        val photos = CalarmiApp.container(context).photos
        val source = photos.file(name)
        if (!source.exists()) return@withContext null
        val copy = "${UUID.randomUUID()}.jpg"
        runCatching { source.copyTo(photos.file(copy), overwrite = true) }.getOrNull() ?: return@withContext null
        copy
    }
}

/** En qué va la foto de una tarjeta: sin foto, cargando o lista. */
sealed interface CountdownPhotoState {
    data object None : CountdownPhotoState
    data object Loading : CountdownPhotoState
    data class Loaded(val bitmap: ImageBitmap) : CountdownPhotoState

    /** Mientras carga ya se usa el diseño con foto: así la tarjeta no salta. */
    val hasPhoto: Boolean get() = this !is None
    val image: ImageBitmap? get() = (this as? Loaded)?.bitmap
}

/** La foto (`photoPath` de `PhotoStore`) lista para Compose, decodificada fuera del hilo principal. */
@Composable
fun rememberCountdownPhoto(photoPath: String?, maxSide: Int = 1080): State<CountdownPhotoState> {
    val context = LocalContext.current
    val photos = CalarmiApp.container(context).photos
    // Si ya está en memoria, se pinta desde el primer cuadro.
    val initial = remember(photoPath, maxSide) {
        when (val cached = photoPath?.let { CountdownPhoto.cached(photos.file(it), maxSide) }) {
            null -> if (photoPath == null) CountdownPhotoState.None else CountdownPhotoState.Loading
            else -> CountdownPhotoState.Loaded(cached.asImageBitmap())
        }
    }
    return produceState(initialValue = initial, photoPath, maxSide) {
        if (photoPath == null) {
            value = CountdownPhotoState.None
            return@produceState
        }
        if (value !is CountdownPhotoState.Loaded) value = CountdownPhotoState.Loading
        val bitmap = withContext(Dispatchers.IO) { CountdownPhoto.load(photos.file(photoPath), maxSide) }
        value = bitmap?.let { CountdownPhotoState.Loaded(it.asImageBitmap()) } ?: CountdownPhotoState.None
    }
}
