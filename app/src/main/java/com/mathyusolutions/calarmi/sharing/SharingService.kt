package com.mathyusolutions.calarmi.sharing

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.messaging.FirebaseMessaging
import com.mathyusolutions.calarmi.AppContainer
import com.mathyusolutions.calarmi.alarm.toHex
import com.mathyusolutions.calarmi.data.SyncHooks
import com.mathyusolutions.calarmi.data.leadTimesCsv
import com.mathyusolutions.calarmi.data.parseLeadTimes
import com.mathyusolutions.calarmi.domain.nextOccurrence
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderIconKind
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Alarmas y cuentas regresivas compartidas por Firestore (contrato en
 * `calarmi-firebase/DESIGN.md`, reglas en `firestore.rules`).
 *
 * - Dueño: «Invitar» crea o actualiza `shares/{id}` (el id es el de la alarma) y
 *   una invitación `invites/{token}`; cada cambio posterior reescribe el
 *   contenido (`revision` + 1) y la vista previa; borrar o dejar de compartir
 *   borra el share y revoca la invitación.
 * - Invitado: acepta con el token (se añade a `members`/`memberUids`), y la copia
 *   local se reescribe con cada cambio del dueño, salvo sus propios avisos.
 * - Todos: mientras la app está a la vista se escuchan los shares de los que se
 *   es miembro; con la app cerrada, un push de datos despierta `ShareSyncWorker`.
 *
 * Firebase solo se toca tras el primer desbloqueo. La cuenta es obligatoria
 * (`account/AccountService`): sin una sesión no anónima no se escucha ni se sube
 * nada, y una sesión anónima de versiones anteriores cuenta como «sin sesión»
 * hasta que se vincula al iniciar sesión.
 */
class SharingService(private val container: AppContainer) {
    val profile = SharingProfile(container.storage)
    val delegation = DelegationService(container, this)

    private val app = container.app
    private val scope = container.scope
    /** Aplicar lo remoto, de a uno: el listener y el push no se pisan. */
    internal val applyLock = Mutex()

    /** Ids aceptados hace poco: una lectura vieja del listener no los borra mientras llega la nueva. */
    private val recentlyJoined = ConcurrentHashMap<String, Long>()

    private val _incoming = MutableStateFlow<IncomingShare?>(null)
    /** El aviso de arriba mientras se agrega una invitación y justo después (`IncomingShareToast`). */
    val incoming: StateFlow<IncomingShare?> = _incoming.asStateFlow()
    fun dismissIncoming() { _incoming.value = null }
    internal fun showIncoming(value: IncomingShare?) { _incoming.value = value }

    private val _signedIn = MutableStateFlow(false)
    /** Hay sesión (para mostrar u ocultar lo que solo existe con ella). */
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /** Firebase se puede usar: tras el primer desbloqueo. */
    val available: Boolean get() = container.isUserUnlocked

    /** El `uid` de la cuenta. Una sesión anónima vieja no cuenta (las reglas ya no la aceptan). */
    val uid: String?
        get() = if (!available) null else runCatching { Cloud.auth().currentUser?.takeIf { !it.isAnonymous }?.uid }.getOrNull()
            .also { _signedIn.value = it != null }

    // ---------------------------------------------------------------- Sesión

    private var uploadedProfile: String? = null

    /**
     * El último `uid` que entró en este teléfono (DESIGN.md › Cambio de cuenta), en el
     * almacenamiento NORMAL: la copia de seguridad no se lo lleva, igual que la sesión de
     * Firebase. Las versiones anteriores ya guardaban aquí el de la sesión anónima.
     */
    private val sessionMarker by lazy { app.getSharedPreferences("sharing_session", android.content.Context.MODE_PRIVATE) }

    internal var lastUid: String?
        get() = sessionMarker.getString("uid", null)
        set(value) {
            sessionMarker.edit().apply { if (value == null) remove("uid") else putString("uid", value) }.commit()
        }

    /**
     * El nombre que ven los demás: el de la cuenta, copiado aquí al iniciar sesión
     * (`SharingProfile.name`), o el de Firebase Auth, o «Usuario de Calarmi».
     */
    fun myName(): String = profile.name.value
        ?: runCatching { Cloud.auth().currentUser?.displayName }.getOrNull()?.let(ShareMapping::validName)
        ?: app.getString(com.mathyusolutions.calarmi.R.string.account_default_name)

    /**
     * La sesión de la cuenta (la pantalla de inicio de sesión garantiza que exista),
     * el perfil en `users/{uid}` y el token de push.
     */
    suspend fun ensureSignedIn(): String {
        val uid = uid ?: throw ShareException(ShareFailure.OTHER)
        if (uploadedProfile != profileKey(uid)) {
            try {
                uploadProfile(uid)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw ShareException(e.shareFailure(), e)
            }
            scope.launch { registerPushToken() }
            if (isForeground && listening == null) startListening()
        }
        return uid
    }

    private fun profileKey(uid: String) = "$uid|${myName()}|${language()}"

    /** `users/{uid}`: el nombre que leen las funciones y los demás, el idioma y la plataforma. */
    private suspend fun uploadProfile(uid: String) {
        val key = profileKey(uid)
        Cloud.user(uid).set(
            mapOf(
                "name" to myName(),
                "language" to language(),
                "platform" to "android",
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        uploadedProfile = key
    }

    /**
     * Recién iniciada la sesión (ya con el nombre de la cuenta en `profile`): sube el
     * perfil y el token de push y, si la app está a la vista, empieza a escuchar. Sin
     * red, Firestore lo deja en su cola y sale al volver.
     */
    fun onSignedIn() {
        val uid = uid ?: return
        uploadedProfile = null
        scope.launch {
            runCatching { uploadProfile(uid) }.onFailure { Log.w(TAG, "No se pudo subir el perfil", it) }
            registerPushToken()
        }
        if (isForeground) startListening()
    }

    /**
     * Antes de cerrar la sesión (DESIGN.md › Cerrar sesión): deja de escuchar y quita el
     * token de push de `users/{uid}.tokens`, para que no lleguen avisos de esta cuenta.
     * Sin red no espera más de unos segundos (el borrado queda en la cola de Firestore).
     */
    suspend fun prepareSignOut() {
        stopListening()
        val uid = uid
        val token = profile.registeredPushToken
        if (uid != null && token != null && profile.registeredPushUid == uid) {
            withTimeoutOrNull(5_000) {
                runCatching {
                    Cloud.user(uid).update(FieldPath.of("tokens", token), FieldValue.delete(), FieldPath.of("updatedAt"), FieldValue.serverTimestamp()).await()
                }.onFailure { Log.w(TAG, "No se pudo quitar el token de push", it) }
            }
        }
        forgetSession()
    }

    /** Lo que este teléfono recuerda de la sesión que se va (no los datos: eso es `clearAccountData`). */
    internal fun forgetSession() {
        uploadedProfile = null
        profile.registeredPushUid = null
        _signedIn.value = false
    }

    /** ¿Hay en el teléfono algo que dependa de una cuenta? (copias recibidas, compartidas propias, espejo). */
    internal suspend fun hasLocalSharedData(): Boolean {
        val reminders = container.database.reminders()
        val countdowns = container.database.countdowns()
        return reminders.received().isNotEmpty() || reminders.sharedByMe().isNotEmpty() ||
            countdowns.received().isNotEmpty() || countdowns.sharedByMe().isNotEmpty() ||
            profile.delegationEnabled.value
    }

    /**
     * «Cambio de cuenta» (y después de borrar la cuenta): se borran las copias de lo que
     * otros compartieron, lo de personas de confianza (el espejo y su invitación) y el
     * nombre guardado; las alarmas y cuentas propias que estaban compartidas se quedan como
     * propias sin compartir. El share en Firestore no se toca: sigue siendo de la otra cuenta.
     */
    internal suspend fun clearAccountData() {
        stopListening()
        applyLock.withLock {
            val reminders = container.database.reminders()
            val countdowns = container.database.countdowns()
            for (r in reminders.received()) {
                reminders.deleteById(r.id)
                r.photoPath?.let(container.photos::delete)
            }
            for (c in countdowns.received()) {
                countdowns.deleteById(c.id)
                c.photoPath?.let(container.photos::delete)
            }
            reminders.sharedByMe().forEach {
                reminders.upsert(it.copy(shareId = null, sharedByName = null, shareRevision = 0, sharePhotoRevision = 0, ownerLeadTimes = null, personalLeadTimes = null))
            }
            countdowns.sharedByMe().forEach {
                countdowns.upsert(it.copy(shareId = null, sharedByName = null, shareRevision = 0, sharePhotoRevision = 0))
            }
        }
        profile.setDelegationEnabled(false)
        profile.clearName()
        forgetSession()
        container.onDataChanged()
    }

    /** "es", "pt" o "en": el de la app (Ajustes › Idioma) o el del teléfono. */
    fun language(): String {
        val appLocales = runCatching { AppCompatDelegate.getApplicationLocales() }.getOrNull()
        val code = appLocales?.takeIf { !it.isEmpty }?.get(0)?.language
            ?: app.resources.configuration.locales[0]?.language
        return ShareMapping.language(code)
    }

    /**
     * Guarda el token de FCM en `users/{uid}.tokens` (y quita el anterior). FCM no
     * se enciende antes: quien nunca compartió nada no genera ni registra token.
     */
    suspend fun registerPushToken(newToken: String? = null) {
        val uid = uid ?: return
        try {
            val messaging = FirebaseMessaging.getInstance()
            if (!messaging.isAutoInitEnabled) messaging.isAutoInitEnabled = true
            // Ver `ShareMessagingService.onNewToken`: el contrato usa tokens de registro.
            @Suppress("DEPRECATION")
            val token = newToken ?: messaging.token.await()
            val old = profile.registeredPushToken
            if (token == old && uid == profile.registeredPushUid) return
            val tokens = mutableMapOf<String, Any>(
                token to mapOf("platform" to "android", "updatedAt" to FieldValue.serverTimestamp()),
            )
            if (old != null && old != token && uid == profile.registeredPushUid) tokens[old] = FieldValue.delete()
            Cloud.user(uid).set(mapOf("tokens" to tokens, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
            profile.registeredPushToken = token
            profile.registeredPushUid = uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar el token de push", e)
        }
    }

    // ------------------------------------------------------------- Primer plano

    @Volatile private var isForeground = false
    private var listening: Job? = null

    /** La app está a la vista: escuchar lo compartido (lo primero que llega del servidor es una sincronización). */
    fun onForeground() {
        isForeground = true
        if (!available || uid == null) return
        startListening()
    }

    fun onBackground() {
        isForeground = false
        stopListening()
    }

    /** Deja de escuchar (al pasar a segundo plano, al cerrar sesión o antes de borrar la cuenta). */
    fun stopListening() {
        listening?.cancel()
        listening = null
    }

    /** Vuelve a escuchar si la app está a la vista (p. ej. si borrar la cuenta falló). */
    fun resumeListening() {
        if (isForeground) onForeground()
    }

    private fun startListening() {
        val uid = uid ?: return
        listening?.cancel()
        listening = scope.launch {
            launch { registerPushToken() }
            launch {
                listen("shares") {
                    Cloud.shares().whereArrayContains("memberUids", uid).snapshots().conflate().collect { snapshot ->
                        applySharesSnapshot(snapshot.documents, fromServer = !snapshot.metadata.isFromCache, uid = uid)
                    }
                }
            }
            launch { delegation.whileForeground(uid) }
        }
    }

    /** Un listener que se cae (sin red un rato, un error raro) se vuelve a poner, cada vez más espaciado. */
    internal suspend fun listen(what: String, block: suspend () -> Unit) {
        var wait = 2_000L
        while (scope.isActive) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Listener de $what cayó; se reintenta en ${wait / 1000} s", e)
                delay(wait)
                wait = (wait * 2).coerceAtMost(5 * 60_000L)
            }
        }
    }

    // ----------------------------------------------------------- Dueño: invitar

    /** Lo que se manda por la hoja de compartir. */
    data class Invite(val token: String, val url: String)

    /** «Invitar» en una alarma propia: la publica (o la actualiza) y devuelve el enlace. */
    suspend fun inviteReminder(id: String): Invite = withContext(Dispatchers.Default) {
        val uid = ensureSignedIn()
        val reminder = container.database.reminders().byId(id) ?: throw ShareException(ShareFailure.OTHER)
        if (reminder.isReceivedShare) throw ShareException(ShareFailure.DENIED)
        val invite = publish(reminderContent(reminder), uid)
        // «Invitar» guardó sin avisar: la lista de las personas de confianza también se pone al día.
        if (profile.delegationEnabled.value) container.database.reminders().byId(id)?.let { delegation.mirror(it) }
        invite
    }

    suspend fun inviteCountdown(id: String): Invite = withContext(Dispatchers.Default) {
        val uid = ensureSignedIn()
        val event = container.database.countdowns().byId(id) ?: throw ShareException(ShareFailure.OTHER)
        if (event.isReceivedShare) throw ShareException(ShareFailure.DENIED)
        publish(countdownContent(event), uid)
    }

    /** Sube a las personas con las que está compartida una alarma que cambió por otra vía (una persona de confianza). */
    internal fun pushShareOf(reminder: Reminder) {
        if (!reminder.isSharedByMe) return
        scope.launch { pushContent(reminderContent(reminder)) }
    }

    /** Lo que se publica de una alarma o cuenta, sin importar el tipo. */
    private class Content(
        val kind: String,
        val id: String,
        val body: Map<String, Any>,
        val preview: Map<String, Any>,
        /** La foto, si el ícono elegido es la foto. */
        val photoPath: String?,
        /** El `photoRevision` que ya está subido según la base local. */
        val uploadedPhotoRevision: Long,
    ) {
        val field: String get() = kind
        val photoRevision: Long get() = photoPath?.let(ShareImages::revisionFor) ?: 0L
    }

    private suspend fun reminderContent(reminder: Reminder): Content {
        val custom = reminder.customCategoryId?.let { id -> container.database.categories().all().firstOrNull { it.id == id } }
        val tint = custom?.colorHex ?: reminder.category.tint.toHex()
        val (emoji, symbol) = ShareMapping.alarmPreviewIcon(
            reminder,
            custom?.iconKind ?: ReminderIconKind.SYMBOL,
            custom?.iconValue ?: reminder.category.defaultSymbol,
        )
        return Content(
            kind = ShareMapping.KIND_ALARM,
            id = reminder.id,
            body = ShareMapping.alarmMap(reminder, custom),
            preview = ShareMapping.previewMap(
                title = reminder.title,
                // La próxima vez que suena dice más que la fecha base de una alarma que se repite.
                date = reminder.nextOccurrence() ?: reminder.date,
                hasTime = true,
                timeZone = TimeZone.getDefault().id,
                language = language(),
                tintHex = tint,
                emoji = emoji,
                symbol = symbol,
                repeatsYearly = reminder.recurrence is com.mathyusolutions.calarmi.model.RecurrenceRule.Yearly,
            ),
            photoPath = reminder.photoPath?.takeIf { reminder.iconKind == ReminderIconKind.PHOTO },
            uploadedPhotoRevision = reminder.sharePhotoRevision,
        )
    }

    private fun countdownContent(event: CountdownEvent): Content {
        val (emoji, symbol) = ShareMapping.countdownPreviewIcon(event)
        return Content(
            kind = ShareMapping.KIND_COUNTDOWN,
            id = event.id,
            body = ShareMapping.countdownMap(event),
            preview = ShareMapping.previewMap(
                title = event.title,
                date = event.target(),
                hasTime = event.includesTime,
                timeZone = TimeZone.getDefault().id,
                language = language(),
                tintHex = event.colorHex,
                emoji = emoji,
                symbol = symbol,
                repeatsYearly = event.repeatsYearly,
            ),
            photoPath = event.photoPath?.takeIf { event.iconKind == ReminderIconKind.PHOTO },
            uploadedPhotoRevision = event.sharePhotoRevision,
        )
    }

    private fun photoBytes(path: String, maxSide: Int, maxBytes: Int): ByteArray? =
        ShareImages.jpeg(container.photos.file(path), maxSide, maxBytes)

    /**
     * Crea el share (con el dueño como único miembro) o, si ya existe, le sube el
     * contenido; deja una invitación vigente y la devuelve. Todo en un lote: las
     * reglas de la invitación y de la foto miran el share «después» del lote.
     */
    private suspend fun publish(content: Content, uid: String): Invite {
        val name = myName()
        val shareRef = Cloud.share(content.id)
        val existing = try {
            shareRef.get(Source.SERVER).await().takeIf { it.exists() }
        } catch (e: FirebaseFirestoreException) {
            // Para las reglas, leer un share que no existe es leer uno ajeno: se crea.
            if (e.isDenied()) null else throw ShareException(e.shareFailure(), e)
        }
        if (existing != null && existing.getString("ownerUid") != uid) throw ShareException(ShareFailure.DENIED)

        // La invitación de antes sirve mientras exista y no esté revocada.
        val oldToken = existing?.getString("inviteToken")?.takeIf(InviteTokens::isValid)
        val reuse = oldToken != null && runCatching {
            Cloud.invite(oldToken).get(Source.SERVER).await().let { it.exists() && it.getBoolean("revoked") != true && it.getString("ownerUid") == uid }
        }.getOrDefault(false)
        val token = if (reuse && oldToken != null) oldToken else InviteTokens.generate()

        val photo = content.photoPath?.let { photoBytes(it, ShareImages.SHARE_MAX_SIDE, ShareImages.SHARE_MAX_BYTES) }
        val thumbnail = content.photoPath?.let { photoBytes(it, ShareImages.INVITE_MAX_SIDE, ShareImages.INVITE_MAX_BYTES) }
        val hasPhoto = photo != null
        val photoRevision = if (hasPhoto) content.photoRevision else 0L

        val db = Cloud.firestore()
        val batch = db.batch()
        if (existing == null) {
            batch.set(
                shareRef,
                mapOf(
                    "kind" to content.kind,
                    "ownerUid" to uid,
                    "ownerName" to name,
                    "members" to mapOf(uid to mapOf("name" to name, "role" to "owner", "joinedAt" to FieldValue.serverTimestamp())),
                    "memberUids" to listOf(uid),
                    "revision" to 1,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid,
                    "inviteToken" to token,
                    "preview" to content.preview,
                    content.field to content.body,
                    "hasPhoto" to hasPhoto,
                    "photoRevision" to photoRevision,
                ),
            )
        } else {
            batch.update(shareRef, contentUpdate(content, uid, name, hasPhoto, photoRevision) + ("inviteToken" to token))
        }
        if (photo != null) {
            batch.set(Cloud.sharePhoto(content.id), mapOf("jpeg" to Blob.fromBytes(photo), "revision" to photoRevision))
        } else if (existing?.getBoolean("hasPhoto") == true) {
            batch.delete(Cloud.sharePhoto(content.id))
        }
        val invite = mutableMapOf<String, Any>(
            "ownerName" to name,
            "preview" to content.preview,
            "photo" to (thumbnail?.let(Blob::fromBytes) ?: FieldValue.delete()),
        )
        if (reuse) {
            batch.update(Cloud.invite(token), invite)
        } else {
            invite.remove("photo")
            thumbnail?.let { invite["photo"] = Blob.fromBytes(it) }
            batch.set(
                Cloud.invite(token),
                invite + mapOf(
                    "kind" to content.kind,
                    "targetId" to content.id,
                    "ownerUid" to uid,
                    "revoked" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }
        try {
            batch.commit().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw ShareException(e.shareFailure(), e)
        }
        markShared(content, photoRevision)
        return Invite(token, InviteLinks.url(token))
    }

    /** Los campos que cambia el dueño al reescribir el contenido (`revision` + 1). */
    private fun contentUpdate(content: Content, uid: String, name: String, hasPhoto: Boolean, photoRevision: Long): Map<String, Any> = mapOf(
        content.field to content.body,
        "preview" to content.preview,
        "ownerName" to name,
        "members.$uid.name" to name,
        "hasPhoto" to hasPhoto,
        "photoRevision" to photoRevision,
        "revision" to FieldValue.increment(1),
        "updatedAt" to FieldValue.serverTimestamp(),
        "updatedBy" to uid,
    )

    private suspend fun markShared(content: Content, photoRevision: Long) {
        when (content.kind) {
            ShareMapping.KIND_ALARM -> container.database.reminders().setShare(content.id, content.id, photoRevision)
            else -> container.database.countdowns().setShare(content.id, content.id, photoRevision)
        }
    }

    private suspend fun markUnshared(kind: String, id: String) {
        when (kind) {
            ShareMapping.KIND_ALARM -> container.database.reminders().setShare(id, null, 0)
            else -> container.database.countdowns().setShare(id, null, 0)
        }
    }

    /**
     * Cada vez que el dueño guarda una alarma o cuenta compartida: el contenido
     * nuevo (`revision` + 1), la foto si cambió y la vista previa de la
     * invitación. Sin red queda en la cola de Firestore y sale al volver.
     */
    private suspend fun pushContent(content: Content) {
        val uid = uid ?: return
        val name = myName()
        val photoChanged = content.photoRevision != content.uploadedPhotoRevision
        val photo = if (content.photoPath != null && photoChanged) photoBytes(content.photoPath, ShareImages.SHARE_MAX_SIDE, ShareImages.SHARE_MAX_BYTES) else null
        val hasPhoto = content.photoPath != null && (photo != null || !photoChanged)
        val photoRevision = if (hasPhoto) content.photoRevision else 0L

        val batch = Cloud.firestore().batch()
        batch.update(Cloud.share(content.id), contentUpdate(content, uid, name, hasPhoto, photoRevision))
        if (photo != null) {
            batch.set(Cloud.sharePhoto(content.id), mapOf("jpeg" to Blob.fromBytes(photo), "revision" to photoRevision))
        } else if (!hasPhoto && content.uploadedPhotoRevision != 0L) {
            batch.delete(Cloud.sharePhoto(content.id))
        }
        inviteTokenOf(content.id)?.let { token ->
            val update = mutableMapOf<String, Any>("preview" to content.preview, "ownerName" to name)
            if (photoChanged) {
                update["photo"] = content.photoPath?.let { photoBytes(it, ShareImages.INVITE_MAX_SIDE, ShareImages.INVITE_MAX_BYTES) }
                    ?.let(Blob::fromBytes) ?: FieldValue.delete()
            }
            batch.update(Cloud.invite(token), update)
        }
        // La foto ya va en la cola: no se vuelve a subir en el próximo guardado.
        if (photoChanged) markShared(content, photoRevision)
        try {
            batch.commit().await()
        } catch (e: FirebaseFirestoreException) {
            // El share ya no existe (se dejó de compartir desde otro lado): queda como propia.
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) markUnshared(content.kind, content.id)
            else Log.w(TAG, "No se pudo subir el cambio de ${content.id}", e)
        }
    }

    /** El token vigente del share (de la caché si está; si no, del servidor). */
    private suspend fun inviteTokenOf(id: String): String? {
        val ref = Cloud.share(id)
        val snapshot = runCatching { ref.get(Source.CACHE).await() }.getOrNull()?.takeIf { it.exists() }
            ?: runCatching { ref.get().await() }.getOrNull()
        return snapshot?.getString("inviteToken")?.takeIf(InviteTokens::isValid)
    }

    /**
     * «Dejar de compartir» (o borrar la alarma): borra el share y su foto y revoca
     * la invitación, en un lote (la regla de la foto mira el share de antes).
     */
    suspend fun unshare(kind: String, id: String) {
        if (uid == null) return
        val token = inviteTokenOf(id)
        val batch: WriteBatch = Cloud.firestore().batch()
        batch.delete(Cloud.sharePhoto(id))
        batch.delete(Cloud.share(id))
        token?.let { batch.update(Cloud.invite(it), "revoked", true) }
        markUnshared(kind, id)
        try {
            batch.commit().await()
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "No se pudo dejar de compartir $id", e)
            // Sin la invitación (borrada a mano, o de otro dueño): al menos el share.
            if (token != null && e.isDenied()) runCatching {
                Cloud.firestore().batch().delete(Cloud.sharePhoto(id)).delete(Cloud.share(id)).commit().await()
            }
        }
    }

    /** El dueño quita a alguien: sale de `members` y `memberUids` (sin tocar el contenido). */
    suspend fun removeMember(shareId: String, memberUid: String) {
        val uid = uid ?: return
        Cloud.share(shareId).update(
            mapOf(
                "members.$memberUid" to FieldValue.delete(),
                "memberUids" to FieldValue.arrayRemove(memberUid),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Quién está en un share (para «Personas»), mientras alguien mira. */
    data class ShareState(val ownerUid: String, val people: List<ShareMapping.Person>)

    fun observeShare(id: String): kotlinx.coroutines.flow.Flow<ShareState?> {
        val uid = uid ?: return kotlinx.coroutines.flow.flowOf(null)
        return kotlinx.coroutines.flow.flow {
            listen("share $id") {
                Cloud.share(id).snapshots().collect { snapshot ->
                    if (!snapshot.exists()) emit(null)
                    else emit(ShareState(snapshot.getString("ownerUid").orEmpty(), ShareMapping.people(snapshot.get("members"), excluding = uid)))
                }
            }
        }
    }

    // ------------------------------------------------------------ Invitaciones

    /** Lo que dice una invitación (se puede leer sin sesión: el token es la llave). */
    data class InviteInfo(
        val token: String,
        val kind: String,
        val targetId: String,
        val ownerUid: String,
        val ownerName: String,
        val title: String,
        val date: Long?,
        val hasTime: Boolean,
        val emoji: String?,
        val symbol: String?,
        val tintHex: String?,
        val photo: ByteArray?,
        val revoked: Boolean,
    )

    suspend fun loadInvite(token: String): InviteInfo {
        if (!available || !InviteTokens.isValid(token)) throw ShareException(ShareFailure.INVITE_MISSING)
        val snapshot = try {
            Cloud.invite(token).get(Source.SERVER).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw ShareException(e.shareFailure().takeIf { it == ShareFailure.OFFLINE } ?: ShareFailure.INVITE_MISSING, e)
        }
        if (!snapshot.exists()) throw ShareException(ShareFailure.INVITE_MISSING)
        val preview = snapshot.get("preview") as? Map<*, *>
        return InviteInfo(
            token = token,
            kind = snapshot.getString("kind") ?: ShareMapping.KIND_ALARM,
            targetId = snapshot.getString("targetId") ?: throw ShareException(ShareFailure.INVITE_MISSING),
            ownerUid = snapshot.getString("ownerUid").orEmpty(),
            ownerName = ShareMapping.validName(snapshot.getString("ownerName")) ?: "",
            title = (preview?.get("title") as? String).orEmpty(),
            date = ShareMapping.millis(preview?.get("date")),
            hasTime = preview?.get("hasTime") as? Boolean ?: true,
            emoji = preview?.get("emoji") as? String,
            symbol = preview?.get("symbol") as? String,
            tintHex = preview?.get("tintHex") as? String,
            photo = snapshot.getBlob("photo")?.toBytes(),
            revoked = snapshot.getBoolean("revoked") == true,
        )
    }

    /** Qué quedó tras aceptar: a dónde llevar a la persona. */
    data class Accepted(val kind: String, val id: String, val title: String, val ownerName: String, val isMine: Boolean)

    /**
     * «Aceptar»: se une al share (o a la lista de alguien, en una de personas de
     * confianza) con el token, en una sola actualización que solo añade a quien
     * acepta, que es lo único que permiten las reglas. Si ya era miembro, solo
     * sincroniza.
     */
    suspend fun accept(info: InviteInfo): Accepted = withContext(Dispatchers.Default) { acceptNow(info) }

    private suspend fun acceptNow(info: InviteInfo): Accepted {
        val uid = ensureSignedIn()
        if (info.kind == ShareMapping.KIND_DELEGATION) return delegation.join(info, uid)
        val title = info.title
        if (info.ownerUid == uid) return Accepted(info.kind, info.targetId, title, info.ownerName, isMine = true)
        _incoming.value = IncomingShare.Adding(info.kind)
        try {
            val ref = Cloud.share(info.targetId)
            val current = try {
                ref.get(Source.SERVER).await().takeIf { it.exists() }
            } catch (e: FirebaseFirestoreException) {
                if (e.isDenied()) null else throw ShareException(e.shareFailure(), e)
            }
            @Suppress("UNCHECKED_CAST")
            val isMember = (current?.get("memberUids") as? List<String>)?.contains(uid) == true
            if (!isMember) {
                if (info.revoked) throw ShareException(ShareFailure.INVITE_REVOKED)
                val name = myName()
                recentlyJoined[info.targetId] = System.currentTimeMillis()
                try {
                    ref.update(
                        mapOf(
                            "members.$uid" to mapOf("name" to name, "role" to "member", "joinedAt" to FieldValue.serverTimestamp()),
                            "memberUids" to FieldValue.arrayUnion(uid),
                            "joinToken" to info.token,
                        ),
                    ).await()
                } catch (e: FirebaseFirestoreException) {
                    // El share ya no está, o la invitación se revocó entre leerla y aceptar.
                    throw ShareException(if (e.isDenied()) ShareFailure.INVITE_REVOKED else e.shareFailure(), e)
                }
            }
            val snapshot = ref.get(Source.SERVER).await()
            applyLock.withLock { applyReceived(snapshot, notify = false) }
            container.onDataChanged()
            val ownerName = ShareMapping.validName(snapshot.getString("ownerName")) ?: info.ownerName
            val finalTitle = (snapshot.get("${info.kind}.title") as? String) ?: title
            _incoming.value = IncomingShare.Added(info.kind, info.targetId, finalTitle, ownerName)
            return Accepted(info.kind, info.targetId, finalTitle, ownerName, isMine = false)
        } catch (e: Exception) {
            _incoming.value = null
            if (e is CancellationException || e is ShareException) throw e
            throw ShareException(e.shareFailure(), e)
        }
    }

    /** «Salir» (invitado): se quita de `members` y `memberUids`. La copia local ya se borró. */
    suspend fun leave(shareId: String) {
        val uid = uid ?: return
        try {
            Cloud.share(shareId).update(
                mapOf("members.$uid" to FieldValue.delete(), "memberUids" to FieldValue.arrayRemove(uid)),
            ).await()
        } catch (e: FirebaseFirestoreException) {
            // Ya no estaba (el dueño la borró o lo quitó antes): nada que hacer.
            if (!e.isDenied()) Log.w(TAG, "No se pudo salir de $shareId", e)
        }
    }

    // ------------------------------------------------------ Aplicar lo remoto

    /**
     * Lo que llega del listener de `shares where memberUids array-contains uid`.
     * Si viene del servidor (no de la caché), lo que falta es que el dueño lo
     * borró o sacó a esta persona: la copia local se va (avisando si iba a sonar).
     */
    private suspend fun applySharesSnapshot(docs: List<DocumentSnapshot>, fromServer: Boolean, uid: String) {
        var changed = false
        applyLock.withLock {
            val present = HashSet<String>()
            for (doc in docs) {
                present += doc.id
                if (doc.getString("ownerUid") == uid) {
                    changed = repairOwned(doc) || changed
                    continue
                }
                changed = runCatching { applyReceived(doc, notify = true) }
                    .onFailure { Log.w(TAG, "No se pudo aplicar ${doc.id}", it) }
                    .getOrDefault(false) || changed
            }
            if (fromServer) {
                val now = System.currentTimeMillis()
                recentlyJoined.entries.removeIf { now - it.value > 120_000 }
                val reminders = container.database.reminders()
                val countdowns = container.database.countdowns()
                for (r in reminders.received()) {
                    val sid = r.shareId ?: r.id
                    if (sid !in present && !recentlyJoined.containsKey(sid)) changed = removeReceived(sid, notifyDeleted = true) || changed
                }
                for (c in countdowns.received()) {
                    val sid = c.shareId ?: c.id
                    if (sid !in present && !recentlyJoined.containsKey(sid)) changed = removeReceived(sid, notifyDeleted = false) || changed
                }
                // Propias que ya no están compartidas (el lote de crear el share falló, p. ej.).
                reminders.sharedByMe().filter { it.shareId !in present }.forEach { markUnshared(ShareMapping.KIND_ALARM, it.id); changed = true }
                countdowns.sharedByMe().filter { it.shareId !in present }.forEach { markUnshared(ShareMapping.KIND_COUNTDOWN, it.id); changed = true }
            }
        }
        if (changed) container.onDataChanged()
    }

    /** Un share propio cuya alarma no sabía que estaba compartida (se perdió la marca local). */
    private suspend fun repairOwned(doc: DocumentSnapshot): Boolean {
        val revision = doc.getLong("photoRevision") ?: 0L
        return when (doc.getString("kind")) {
            ShareMapping.KIND_ALARM -> container.database.reminders().byId(doc.id)
                ?.takeIf { !it.isReceivedShare && it.shareId == null }
                ?.let { container.database.reminders().setShare(it.id, it.id, revision); true } ?: false
            ShareMapping.KIND_COUNTDOWN -> container.database.countdowns().byId(doc.id)
                ?.takeIf { !it.isReceivedShare && it.shareId == null }
                ?.let { container.database.countdowns().setShare(it.id, it.id, revision); true } ?: false
            else -> false
        }
    }

    /** Escribe (o reescribe) la copia local de un share recibido. Devuelve si cambió algo. */
    internal suspend fun applyReceived(doc: DocumentSnapshot, notify: Boolean): Boolean {
        if (!doc.exists()) return false
        val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return false
        return when (data["kind"]) {
            ShareMapping.KIND_ALARM -> applyReceivedAlarm(doc.id, data, notify)
            ShareMapping.KIND_COUNTDOWN -> applyReceivedCountdown(doc.id, data)
            else -> false
        }
    }

    private suspend fun applyReceivedAlarm(id: String, data: Map<String, Any?>, notify: Boolean): Boolean {
        val dao = container.database.reminders()
        val local = dao.byId(id)
        // Una propia con el mismo id: nunca se pisa (no debería pasar, los ids son UUID).
        if (local != null && !local.isReceivedShare) return false
        val revision = ShareMapping.long(data["revision"]) ?: 0L
        val ownerName = ShareMapping.validName(data["ownerName"] as? String)
        val hasPhoto = data["hasPhoto"] == true
        val photoRevision = ShareMapping.long(data["photoRevision"]) ?: 0L
        val photoUpToDate = if (hasPhoto) local?.photoPath != null && local.sharePhotoRevision == photoRevision else local?.photoPath == null
        if (local != null && local.shareRevision == revision && local.sharedByName == ownerName && photoUpToDate) return false

        val remote = ShareMapping.alarm(id, data["alarm"]) ?: return false
        remote.customCategory?.let { ensureCategory(it) }
        val (photoPath, appliedPhotoRevision) = receivedPhoto(id, hasPhoto, photoRevision, local?.photoPath, local?.sharePhotoRevision ?: 0L)
        val personal = parseLeadTimes(local?.personalLeadTimes)
        val updated = remote.reminder.copy(
            photoPath = photoPath,
            leadTimes = personal ?: remote.leadTimes,
            createdAt = local?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            shareId = id,
            isReceivedShare = true,
            sharedByName = ownerName,
            shareRevision = revision,
            sharePhotoRevision = appliedPhotoRevision,
            ownerLeadTimes = leadTimesCsv(remote.leadTimes),
            personalLeadTimes = local?.personalLeadTimes,
        )
        dao.upsert(updated)
        if (notify && local != null && !ShareNotifications.sameSchedules(local, updated)) {
            ShareNotifications.scheduleChanged(app, updated, ownerName)
        }
        return true
    }

    private suspend fun applyReceivedCountdown(id: String, data: Map<String, Any?>): Boolean {
        val dao = container.database.countdowns()
        val local = dao.byId(id)
        if (local != null && !local.isReceivedShare) return false
        val revision = ShareMapping.long(data["revision"]) ?: 0L
        val ownerName = ShareMapping.validName(data["ownerName"] as? String)
        val hasPhoto = data["hasPhoto"] == true
        val photoRevision = ShareMapping.long(data["photoRevision"]) ?: 0L
        val photoUpToDate = if (hasPhoto) local?.photoPath != null && local.sharePhotoRevision == photoRevision else local?.photoPath == null
        if (local != null && local.shareRevision == revision && local.sharedByName == ownerName && photoUpToDate) return false

        val remote = ShareMapping.countdown(id, data["countdown"]) ?: return false
        val (photoPath, appliedPhotoRevision) = receivedPhoto(id, hasPhoto, photoRevision, local?.photoPath, local?.sharePhotoRevision ?: 0L)
        dao.upsert(
            remote.copy(
                photoPath = photoPath,
                createdAt = local?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                shareId = id,
                isReceivedShare = true,
                sharedByName = ownerName,
                shareRevision = revision,
                sharePhotoRevision = appliedPhotoRevision,
            ),
        )
        return true
    }

    /**
     * La foto de un share recibido: se baja solo cuando cambia `photoRevision`.
     * Si no se puede bajar ahora, se queda la anterior y se reintenta la próxima vez.
     */
    private suspend fun receivedPhoto(id: String, hasPhoto: Boolean, revision: Long, localPath: String?, localRevision: Long): Pair<String?, Long> {
        if (!hasPhoto) {
            localPath?.let(container.photos::delete)
            return null to 0L
        }
        if (localPath != null && localRevision == revision) return localPath to revision
        val bytes = runCatching { Cloud.sharePhoto(id).get().await().getBlob("jpeg")?.toBytes() }
            .onFailure { Log.w(TAG, "No se pudo bajar la foto de $id", it) }
            .getOrNull() ?: return localPath to localRevision
        val saved = container.photos.save(bytes) ?: return localPath to localRevision
        localPath?.let(container.photos::delete)
        return saved to revision
    }

    /** Una categoría propia del dueño se crea igual aquí (mismo id), para pintarla con su color. */
    internal suspend fun ensureCategory(custom: CustomCategory) {
        val dao = container.database.categories()
        val all = dao.all()
        val existing = all.firstOrNull { it.id.equals(custom.id, ignoreCase = true) }
        if (existing == null) {
            dao.upsert(custom.copy(sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1))
        } else if (existing.name != custom.name || existing.colorHex != custom.colorHex || existing.iconKindRaw != custom.iconKindRaw || existing.iconValue != custom.iconValue) {
            dao.upsert(existing.copy(name = custom.name, colorHex = custom.colorHex, iconKindRaw = custom.iconKindRaw, iconValue = custom.iconValue))
        }
    }

    /** Quita la copia local de un share recibido (el dueño lo borró o sacó a esta persona). */
    private suspend fun removeReceived(id: String, notifyDeleted: Boolean): Boolean {
        var changed = false
        val reminders = container.database.reminders()
        reminders.byId(id)?.takeIf { it.isReceivedShare }?.let { r ->
            reminders.deleteById(r.id)
            r.photoPath?.let(container.photos::delete)
            if (notifyDeleted && ShareNotifications.isUpcoming(r)) ShareNotifications.deleted(app, r, r.sharedByName)
            changed = true
        }
        val countdowns = container.database.countdowns()
        countdowns.byId(id)?.takeIf { it.isReceivedShare }?.let { c ->
            countdowns.deleteById(c.id)
            c.photoPath?.let(container.photos::delete)
            changed = true
        }
        return changed
    }

    // -------------------------------------------------------------------- Push

    /**
     * Un mensaje de datos de las Cloud Functions (DESIGN.md › Push). Se lee lo
     * que cambió y se reprograma; el aviso de «cambió la hora» sale de comparar
     * con la copia local, igual venga del push o del listener.
     */
    suspend fun handlePush(data: Map<String, String>) {
        if (!available) return
        val uid = uid ?: return
        val id = data["id"] ?: return
        when (data["type"]) {
            "share" -> when (data["event"]) {
                "updated" -> syncShare(id)
                "deleted" -> {
                    val changed = applyLock.withLock { removeReceived(id, notifyDeleted = true) }
                    if (changed) container.onDataChanged()
                }
                "removed" -> {
                    // También llega cuando la persona salió sola (ya no tiene copia): no pasa nada.
                    val changed = applyLock.withLock { removeReceived(id, notifyDeleted = false) }
                    if (changed) container.onDataChanged()
                }
                // «joined» / «left» le llegan al dueño: su lista de personas se actualiza sola si está mirando.
            }
            "delegationReminder" -> data["principal"]?.let { principal ->
                if (principal == uid) delegation.syncPrincipalReminder(uid, id)
            }
        }
    }

    /** Lee un share del servidor y aplica lo nuevo; si ya no se puede leer, el dueño lo quitó. */
    suspend fun syncShare(id: String) {
        val snapshot = try {
            Cloud.share(id).get(Source.SERVER).await()
        } catch (e: FirebaseFirestoreException) {
            if (!e.isDenied()) throw e // pasajero (sin red): el trabajo del push reintenta
            val changed = applyLock.withLock { removeReceived(id, notifyDeleted = true) }
            if (changed) container.onDataChanged()
            return
        }
        val changed = applyLock.withLock {
            if (!snapshot.exists()) removeReceived(id, notifyDeleted = true) else applyReceived(snapshot, notify = true)
        }
        if (changed) container.onDataChanged()
    }

    // ----------------------------------------------------------------- Nombre

    /**
     * Cambiar el nombre: aquí al instante y, si hay sesión, en `users/{uid}` y en
     * cada sitio donde aparece (shares, invitaciones y listas de confianza). El
     * perfil de Firebase Auth lo cambia `AccountService.rename`.
     */
    fun rename(newName: String) {
        val clean = ShareMapping.validName(newName) ?: return
        if (clean == profile.name.value) return
        profile.setName(clean)
        propagateName(clean)
    }

    /** El nombre (ya guardado en `profile`) en `users/{uid}` y en todo lo compartido. */
    internal fun propagateName(clean: String) {
        val uid = uid ?: return
        scope.launch {
            try {
                Cloud.user(uid).set(mapOf("name" to clean, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
                uploadedProfile = null
                val shares = Cloud.shares().whereArrayContains("memberUids", uid).get().await()
                for (doc in shares.documents) {
                    if (doc.getString("ownerUid") == uid) {
                        val batch = Cloud.firestore().batch()
                        batch.update(
                            doc.reference,
                            mapOf("ownerName" to clean, "members.$uid.name" to clean, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()),
                        )
                        doc.getString("inviteToken")?.takeIf(InviteTokens::isValid)?.let { batch.update(Cloud.invite(it), "ownerName", clean) }
                        runCatching { batch.commit().await() }
                    } else {
                        runCatching { doc.reference.update("members.$uid.name", clean).await() }
                    }
                }
                delegation.rename(uid, clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cambiar el nombre en todos lados", e)
            }
        }
    }

    // ------------------------------------------------------------------ Ganchos

    /** Lo que hacen los repositorios después de guardar o borrar algo propio. */
    val hooks: SyncHooks = object : SyncHooks {
        override fun reminderSaved(saved: Reminder, previous: Reminder?) {
            if (saved.isReceivedShare || !available || uid == null) return
            scope.launch {
                if (saved.shareId != null) pushContent(reminderContent(saved))
                if (profile.delegationEnabled.value) delegation.mirror(saved)
            }
        }

        override fun reminderDeleted(deleted: Reminder) {
            if (!available || uid == null) return
            scope.launch {
                when {
                    deleted.isReceivedShare -> leave(deleted.shareId ?: deleted.id)
                    else -> {
                        if (deleted.shareId != null) unshare(ShareMapping.KIND_ALARM, deleted.id)
                        if (profile.delegationEnabled.value) delegation.mirrorDeleted(deleted.id)
                    }
                }
            }
        }

        override fun countdownSaved(saved: CountdownEvent, previous: CountdownEvent?) {
            if (saved.isReceivedShare || saved.shareId == null || !available || uid == null) return
            scope.launch { pushContent(countdownContent(saved)) }
        }

        override fun countdownDeleted(deleted: CountdownEvent) {
            if (!available || uid == null) return
            scope.launch {
                if (deleted.isReceivedShare) leave(deleted.shareId ?: deleted.id)
                else if (deleted.shareId != null) unshare(ShareMapping.KIND_COUNTDOWN, deleted.id)
            }
        }
    }

    companion object {
        private const val TAG = "Sharing"
    }
}

/** El aviso de arriba al aceptar una invitación (el `IncomingShareToast` de iOS). */
sealed interface IncomingShare {
    data class Adding(val kind: String) : IncomingShare
    data class Added(val kind: String, val id: String, val title: String, val ownerName: String?) : IncomingShare
}
