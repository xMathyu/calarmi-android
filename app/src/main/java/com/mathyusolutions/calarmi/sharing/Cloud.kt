package com.mathyusolutions.calarmi.sharing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * La entrada a Firebase. Nada de aquí se toca antes del primer desbloqueo: Auth
 * y la caché de Firestore viven en el almacenamiento normal (cifrado con la
 * credencial), que en el arranque directo todavía no se puede leer. Quien llama
 * lo comprueba con `AppContainer.isUserUnlocked`.
 */
object Cloud {
    @Volatile private var configured = false

    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Firestore con la caché persistente encendida (es la de por defecto, pero
     * se dice explícito): lo compartido se ve y se edita sin red, y las
     * escrituras pendientes sobreviven a cerrar la app.
     */
    /** Firestore ya se usó en este proceso (p. ej. sin sesión, para leer una invitación). */
    @Volatile private var used = false

    /**
     * Tras abrir la sesión por primera vez. Si Firestore ya se había usado sin
     * sesión (la vista previa de una invitación), el cambio de usuario le llega
     * por su cuenta y sin orden garantizado: una escritura hecha justo después
     * podía quedar en la cola del usuario «sin sesión» y no salir nunca (visto en
     * el emulador: «Aceptar» se quedaba girando). Se cierra y se abre de nuevo, ya
     * con el usuario nuevo.
     */
    suspend fun restartAfterSignIn() {
        if (!used) return
        runCatching { FirebaseFirestore.getInstance().terminate().await() }
        synchronized(this) {
            configured = false
            used = false
        }
    }

    fun firestore(): FirebaseFirestore {
        used = true
        val db = FirebaseFirestore.getInstance()
        if (!configured) synchronized(this) {
            if (!configured) {
                runCatching {
                    db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                }
                configured = true
            }
        }
        return db
    }

    fun shares() = firestore().collection("shares")
    fun share(id: String) = shares().document(id)
    fun sharePhoto(id: String) = share(id).collection("photo").document("current")
    fun invite(token: String) = firestore().collection("invites").document(token)
    fun user(uid: String) = firestore().collection("users").document(uid)
    fun delegations() = firestore().collection("delegations")
    fun delegation(principal: String) = delegations().document(principal)
    fun delegatedReminders(principal: String) = delegation(principal).collection("reminders")
}

/** Por qué falló algo compartido, en lo que la interfaz sabe explicar. */
enum class ShareFailure { NO_NAME, OFFLINE, INVITE_MISSING, INVITE_REVOKED, DENIED, OTHER }

class ShareException(val failure: ShareFailure, cause: Throwable? = null) : Exception(failure.name, cause)

fun Throwable.shareFailure(): ShareFailure = when (this) {
    is ShareException -> failure
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ShareFailure.OFFLINE
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ShareFailure.DENIED
        FirebaseFirestoreException.Code.NOT_FOUND -> ShareFailure.INVITE_MISSING
        else -> ShareFailure.OTHER
    }
    is com.google.firebase.FirebaseNetworkException -> ShareFailure.OFFLINE
    else -> cause?.shareFailure() ?: ShareFailure.OTHER
}

/** ¿Las reglas dijeron que no (o el documento no existe, que para ellas es lo mismo)? */
fun Throwable.isDenied(): Boolean = this is FirebaseFirestoreException &&
    (code == FirebaseFirestoreException.Code.PERMISSION_DENIED || code == FirebaseFirestoreException.Code.NOT_FOUND)

/** Cada estado de una consulta mientras alguien escucha (sin las de solo metadatos). */
fun Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/** Las fotos que viajan: JPEG reducido hasta caber en el límite de las reglas. */
object ShareImages {
    /** `shares/{id}/photo/current`: ≤ 600 KB, lado ≤ 1080 px. */
    const val SHARE_MAX_BYTES = 600 * 1024
    const val SHARE_MAX_SIDE = 1080
    /** Miniatura de la tarjeta del enlace (`invites/{token}.photo`): ≤ 60 KB. */
    const val INVITE_MAX_BYTES = 60 * 1024
    const val INVITE_MAX_SIDE = 320

    fun jpeg(file: File, maxSide: Int, maxBytes: Int): ByteArray? = runCatching { encode(file, maxSide, maxBytes) }.getOrNull()

    private fun encode(file: File, maxSide: Int, maxBytes: Int): ByteArray? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        var side = maxSide
        var quality = 85
        while (side >= 64) {
            val bytes = ByteArrayOutputStream().use { out ->
                scaled(decoded, side).compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            if (bytes.size <= maxBytes) return bytes
            // Primero baja la calidad; si no alcanza, también el tamaño.
            if (quality > 50) quality -= 10 else {
                side = (side * 0.75f).toInt()
                quality = 75
            }
        }
        return null
    }

    private fun scaled(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    /**
     * El `photoRevision` de una foto propia: sale del nombre del archivo (cada
     * foto elegida tiene uno nuevo), así cambia solo cuando cambia la foto.
     */
    fun revisionFor(photoPath: String): Long = (photoPath.hashCode().toLong() and 0x7FFFFFFF).coerceAtLeast(1)
}
