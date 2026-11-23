package com.mathyusolutions.calarmi.sharing

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.mathyusolutions.calarmi.AppContainer
import com.mathyusolutions.calarmi.data.keepingSyncFields
import com.mathyusolutions.calarmi.model.AlarmTone
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderIconKind
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * «Personas de confianza» (DESIGN.md › `delegations/{principalUid}`).
 *
 * - En el teléfono de quien delega (el principal) está la verdad: sube todas sus
 *   alarmas propias a `delegations/{uid}/reminders`, escucha lo que cambian sus
 *   personas de confianza (`updatedBy` ≠ él) y lo aplica. Si los dos tocaron la
 *   misma alarma, gana el cambio más reciente (`updatedAt`).
 * - Una persona de confianza nunca guarda esas alarmas en su teléfono (no deben
 *   sonarle a ella): las lee y escribe directo en el documento del principal.
 */
class DelegationService(private val container: AppContainer, private val sharing: SharingService) {
    private val profile get() = sharing.profile

    // ---------------------------------------------------------------- Principal

    /** «Permitir personas de confianza»: crea la lista (sin nadie todavía) y sube las alarmas. */
    suspend fun enable() {
        val uid = sharing.ensureSignedIn()
        val name = profile.name.value ?: throw ShareException(ShareFailure.NO_NAME)
        ensureDelegationDoc(uid, name)
        profile.setDelegationEnabled(true)
        // Sin red a medias: lo que falte se sube la próxima vez que la app vuelva a la vista.
        runCatching { mirrorAll(uid) }.onFailure { Log.w(TAG, "Espejo inicial incompleto", it) }
        sharing.onForeground()
    }

    private suspend fun ensureDelegationDoc(uid: String, name: String) {
        val ref = Cloud.delegation(uid)
        try {
            val existing = ref.get(Source.SERVER).await()
            if (existing.exists()) {
                ref.update(mapOf("principalName" to name, "updatedAt" to FieldValue.serverTimestamp())).await()
            } else {
                ref.set(
                    mapOf(
                        "principalName" to name,
                        "helpers" to emptyMap<String, Any>(),
                        "helperUids" to emptyList<String>(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw ShareException(e.shareFailure(), e)
        }
    }

    /**
     * Apagarlo: revoca la invitación, borra el espejo y la lista (las personas de
     * confianza dejan de ver las alarmas). Las alarmas propias siguen intactas.
     */
    suspend fun disable() {
        val uid = sharing.uid
        if (uid != null) {
            try {
                profile.delegationInviteToken?.let { runCatching { Cloud.invite(it).update("revoked", true).await() } }
                val docs = Cloud.delegatedReminders(uid).get(Source.SERVER).await().documents
                docs.chunked(400).forEach { chunk ->
                    val batch = Cloud.firestore().batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                Cloud.delegation(uid).delete().await()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw ShareException(e.shareFailure(), e)
            }
        }
        profile.setDelegationEnabled(false)
        // Deja de escuchar el espejo que ya no existe.
        sharing.onForeground()
    }

    /** La invitación de personas de confianza (una sola; se reutiliza mientras siga vigente). */
    suspend fun invite(): SharingService.Invite {
        val uid = sharing.ensureSignedIn()
        val name = profile.name.value ?: throw ShareException(ShareFailure.NO_NAME)
        if (!profile.delegationEnabled.value) enable()
        val preview = ShareMapping.previewMap(
            title = name,
            date = System.currentTimeMillis(),
            hasTime = false,
            timeZone = TimeZone.getDefault().id,
            language = sharing.language(),
            symbol = "person.2.fill",
        )
        try {
            val old = profile.delegationInviteToken?.takeIf(InviteTokens::isValid)
            val reusable = old != null && runCatching {
                Cloud.invite(old).get(Source.SERVER).await().let { it.exists() && it.getBoolean("revoked") != true }
            }.getOrDefault(false)
            if (reusable && old != null) {
                Cloud.invite(old).update(mapOf("ownerName" to name, "preview" to preview)).await()
                return SharingService.Invite(old, InviteLinks.url(old))
            }
            val token = InviteTokens.generate()
            Cloud.invite(token).set(
                mapOf(
                    "kind" to ShareMapping.KIND_DELEGATION,
                    "targetId" to uid,
                    "ownerUid" to uid,
                    "ownerName" to name,
                    "preview" to preview,
                    "revoked" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            profile.delegationInviteToken = token
            return SharingService.Invite(token, InviteLinks.url(token))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw ShareException(e.shareFailure(), e)
        }
    }

    /** «Revocar»: la persona sale de `helpers`/`helperUids` y deja de ver las alarmas. */
    suspend fun removeHelper(helperUid: String) {
        val uid = sharing.uid ?: return
        Cloud.delegation(uid).update(
            mapOf(
                "helpers.$helperUid" to FieldValue.delete(),
                "helperUids" to FieldValue.arrayRemove(helperUid),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Las personas de confianza de este teléfono, mientras alguien mira. */
    fun observeHelpers(): Flow<List<ShareMapping.Person>?> {
        val uid = sharing.uid ?: return flowOf(null)
        return flow {
            sharing.listen("delegation") {
                Cloud.delegation(uid).snapshots().collect { snapshot ->
                    emit(if (snapshot.exists()) ShareMapping.people(snapshot.get("helpers"), dateKey = "addedAt") else emptyList())
                }
            }
        }
    }

    /** Mientras la app está a la vista: espejo al día y lo que cambian las personas de confianza. */
    suspend fun whileForeground(uid: String) {
        if (!profile.delegationEnabled.value) return
        runCatching { mirrorAll(uid) }.onFailure { Log.w(TAG, "Espejo incompleto", it) }
        sharing.listen("delegation reminders") {
            Cloud.delegatedReminders(uid).snapshots().conflate().collect { snapshot ->
                var changed = false
                sharing.applyLock.withLock {
                    for (doc in snapshot.documents) {
                        changed = runCatching { applyHelperChange(doc, uid) }
                            .onFailure { Log.w(TAG, "No se pudo aplicar ${doc.id}", it) }
                            .getOrDefault(false) || changed
                    }
                }
                if (changed) container.onDataChanged()
            }
        }
    }

    /**
     * Sube lo propio que cambió desde la última vez, marca como borrado lo que
     * ya no existe aquí (se borró sin red o con esto apagado) y limpia las marcas
     * de borrado viejas.
     */
    suspend fun mirrorAll(uid: String) {
        val own = container.database.reminders().own()
        val mirrored = profile.mirrored()
        own.filter { mirrored[it.id] != it.updatedAt }.forEach { mirror(it) }
        val remote = Cloud.delegatedReminders(uid).get(Source.SERVER).await().documents
        val ownIds = own.map { it.id }.toSet()
        val cutoff = System.currentTimeMillis() - TOMBSTONE_TTL
        var changed = false
        sharing.applyLock.withLock {
            for (doc in remote) {
                val deleted = doc.getBoolean("deleted") == true
                val by = doc.getString("updatedBy")
                when {
                    deleted && by == uid && (updatedAt(doc) ?: Long.MAX_VALUE) < cutoff -> runCatching { doc.reference.delete() }
                    deleted -> changed = applyHelperChange(doc, uid) || changed
                    doc.id !in ownIds && by == uid -> softDelete(uid, doc.id)
                    by != uid -> changed = applyHelperChange(doc, uid) || changed
                }
            }
        }
        if (changed) container.onDataChanged()
    }

    /** Sube una alarma propia (nueva o cambiada). Sin red queda en la cola de Firestore. */
    suspend fun mirror(reminder: Reminder) {
        if (reminder.isReceivedShare) return
        val uid = sharing.uid ?: return
        val custom = reminder.customCategoryId?.let { id -> container.database.categories().all().firstOrNull { it.id == id } }
        profile.setMirrored(reminder.id, reminder.updatedAt)
        Cloud.delegatedReminders(uid).document(reminder.id).set(
            mapOf(
                "alarm" to replacingAlarm(ShareMapping.alarmMap(reminder, custom)),
                "deleted" to false,
                "revision" to FieldValue.increment(1),
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid,
            ),
            // `photo`, si otra app la puso, no se toca.
            SetOptions.merge(),
        ).addOnFailureListener { Log.w(TAG, "No se pudo subir ${reminder.id}", it) }
    }

    /** El principal borró una alarma: borrado suave, para que les llegue a sus personas de confianza. */
    fun mirrorDeleted(id: String) {
        val uid = sharing.uid ?: return
        profile.setMirrored(id, null)
        softDelete(uid, id)
    }

    private fun softDelete(uid: String, id: String) {
        Cloud.delegatedReminders(uid).document(id).set(
            mapOf(
                "deleted" to true,
                "revision" to FieldValue.increment(1),
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid,
            ),
            SetOptions.merge(),
        ).addOnFailureListener { Log.w(TAG, "No se pudo marcar $id como borrada", it) }
    }

    /** Un push de `delegationReminder` en el teléfono del principal: leer esa alarma y aplicarla. */
    suspend fun syncPrincipalReminder(uid: String, id: String) {
        if (!profile.delegationEnabled.value) return
        // Si falla (sin red un momento), el trabajo del push reintenta.
        val doc = Cloud.delegatedReminders(uid).document(id).get(Source.SERVER).await()
        val changed = sharing.applyLock.withLock { applyHelperChange(doc, uid) }
        if (changed) container.onDataChanged()
    }

    /**
     * Aplica lo que escribió una persona de confianza. El documento muestra la
     * última escritura del servidor, así que si la hizo otra persona es más nueva
     * que lo último que subió este teléfono; solo se duda si aquí hay un cambio
     * todavía sin subir, y entonces gana el `updatedAt` más reciente.
     */
    private suspend fun applyHelperChange(doc: DocumentSnapshot, uid: String): Boolean {
        if (!doc.exists() || doc.getString("updatedBy") == uid) return false
        val remoteAt = updatedAt(doc) ?: return false
        val dao = container.database.reminders()
        val local = dao.byId(doc.id)
        if (local?.isReceivedShare == true) return false
        if (local != null && local.updatedAt == remoteAt) return false
        val pendingHere = local != null && profile.mirrored()[local.id] != local.updatedAt
        if (pendingHere && local != null && local.updatedAt > remoteAt) return false

        if (doc.getBoolean("deleted") == true) {
            if (local == null) return false
            dao.deleteById(local.id)
            local.photoPath?.let(container.photos::delete)
            profile.setMirrored(local.id, null)
            if (local.shareId != null) sharing.unshare(ShareMapping.KIND_ALARM, local.id)
            return true
        }
        val remote = ShareMapping.alarm(doc.id, doc.get("alarm")) ?: return false
        remote.customCategory?.let { sharing.ensureCategory(it) }
        val keepPhoto = remote.reminder.iconKind == ReminderIconKind.PHOTO
        if (!keepPhoto) local?.photoPath?.let(container.photos::delete)
        val updated = remote.reminder.copy(
            photoPath = if (keepPhoto) local?.photoPath else null,
            // Un tono importado no viaja: si el cambio no trae otro, se queda el de aquí.
            tone = remote.reminder.tone ?: local?.tone?.takeIf { it is AlarmTone.Imported },
            createdAt = local?.createdAt ?: System.currentTimeMillis(),
            updatedAt = remoteAt,
        ).keepingSyncFields(local)
        dao.upsert(updated)
        profile.setMirrored(updated.id, updated.updatedAt)
        // Si además estaba compartida con otras personas, a ellas también les llega
        // (sin volver a subirla al espejo: eso sería devolverle su propio cambio).
        sharing.pushShareOf(updated)
        return true
    }

    private fun updatedAt(doc: DocumentSnapshot): Long? =
        doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.let { ShareMapping.millis(it) }

    // --------------------------------------------------------- Persona de confianza

    /** Aceptar una invitación de personas de confianza: entrar en `helpers`/`helperUids` con el token. */
    suspend fun join(info: SharingService.InviteInfo, uid: String): SharingService.Accepted {
        if (info.targetId == uid) return SharingService.Accepted(ShareMapping.KIND_DELEGATION, uid, info.ownerName, info.ownerName, isMine = true)
        val ref = Cloud.delegation(info.targetId)
        try {
            val current = try {
                ref.get(Source.SERVER).await().takeIf { it.exists() }
            } catch (e: FirebaseFirestoreException) {
                if (e.isDenied()) null else throw e
            }
            @Suppress("UNCHECKED_CAST")
            val isHelper = (current?.get("helperUids") as? List<String>)?.contains(uid) == true
            if (!isHelper) {
                if (info.revoked) throw ShareException(ShareFailure.INVITE_REVOKED)
                val name = profile.name.value ?: throw ShareException(ShareFailure.NO_NAME)
                try {
                    ref.update(
                        mapOf(
                            "helpers.$uid" to mapOf("name" to name, "addedAt" to FieldValue.serverTimestamp()),
                            "helperUids" to FieldValue.arrayUnion(uid),
                            "joinToken" to info.token,
                        ),
                    ).await()
                } catch (e: FirebaseFirestoreException) {
                    throw ShareException(if (e.isDenied()) ShareFailure.INVITE_REVOKED else e.shareFailure(), e)
                }
            }
            val principalName = ShareMapping.validName(current?.getString("principalName")) ?: info.ownerName
            sharing.showIncoming(IncomingShare.Added(ShareMapping.KIND_DELEGATION, info.targetId, principalName, principalName))
            return SharingService.Accepted(ShareMapping.KIND_DELEGATION, info.targetId, principalName, principalName, isMine = false)
        } catch (e: Exception) {
            if (e is CancellationException || e is ShareException) throw e
            throw ShareException(e.shareFailure(), e)
        }
    }

    /** Una lista que esta persona administra. */
    data class Principal(val uid: String, val name: String)

    /** «Listas que administro», mientras alguien mira. Sin sesión, ninguna. */
    fun observePrincipals(): Flow<List<Principal>> {
        val uid = sharing.uid ?: return flowOf(emptyList())
        return flow {
            sharing.listen("principals") {
                Cloud.delegations().whereArrayContains("helperUids", uid).snapshots().collect { snapshot ->
                    emit(
                        snapshot.documents.map { Principal(it.id, ShareMapping.validName(it.getString("principalName")) ?: "") }
                            .sortedBy { it.name.lowercase() },
                    )
                }
            }
        }
    }

    /** Una alarma de la lista de otra persona, tal como está en su documento. */
    data class Delegated(val remote: ShareMapping.RemoteAlarm, val updatedAt: Long?)

    fun observeDelegated(principal: String): Flow<List<Delegated>?> {
        if (sharing.uid == null) return flowOf(null)
        return flow {
            sharing.listen("delegated $principal") {
                Cloud.delegatedReminders(principal).whereEqualTo("deleted", false).snapshots().collect { snapshot ->
                    emit(snapshot.documents.mapNotNull { doc -> ShareMapping.alarm(doc.id, doc.get("alarm"))?.let { Delegated(it, updatedAt(doc)) } })
                }
            }
        }
    }

    /** Para abrir una en el editor (de la caché si está, que también funciona sin red). */
    suspend fun fetchDelegated(principal: String, id: String): ShareMapping.RemoteAlarm? {
        val ref = Cloud.delegatedReminders(principal).document(id)
        val doc = runCatching { ref.get(Source.CACHE).await() }.getOrNull()?.takeIf { it.exists() }
            ?: runCatching { ref.get().await() }.getOrNull()
        if (doc == null || !doc.exists() || doc.getBoolean("deleted") == true) return null
        return ShareMapping.alarm(doc.id, doc.get("alarm"))
    }

    /**
     * Crea o cambia una alarma DE OTRA PERSONA: va directo a su documento (nunca a
     * esta base), así suena en su teléfono y no en este. No espera al servidor:
     * sin red queda en la cola.
     */
    fun saveDelegated(principal: String, reminder: Reminder, custom: CustomCategory?) {
        val uid = sharing.uid ?: return
        Cloud.delegatedReminders(principal).document(reminder.id).set(
            mapOf(
                "alarm" to replacingAlarm(ShareMapping.alarmMap(reminder, custom)),
                "deleted" to false,
                "revision" to FieldValue.increment(1),
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid,
            ),
            SetOptions.merge(),
        ).addOnFailureListener { Log.w(TAG, "No se pudo guardar ${reminder.id} de $principal", it) }
    }

    /** Borrado suave: el teléfono del principal la borra y el documento queda marcado. */
    fun deleteDelegated(principal: String, id: String) {
        val uid = sharing.uid ?: return
        Cloud.delegatedReminders(principal).document(id).set(
            mapOf(
                "deleted" to true,
                "revision" to FieldValue.increment(1),
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid,
            ),
            SetOptions.merge(),
        ).addOnFailureListener { Log.w(TAG, "No se pudo borrar $id de $principal", it) }
    }

    /** Dejar de administrar la lista de alguien. */
    suspend fun leaveList(principal: String) {
        val uid = sharing.uid ?: return
        Cloud.delegation(principal).update(
            mapOf("helpers.$uid" to FieldValue.delete(), "helperUids" to FieldValue.arrayRemove(uid)),
        ).await()
    }

    /** El nombre nuevo, como principal (y en su invitación) y como persona de confianza. */
    suspend fun rename(uid: String, name: String) {
        runCatching {
            val own = Cloud.delegation(uid).get().await()
            if (own.exists()) Cloud.delegation(uid).update(mapOf("principalName" to name, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
        profile.delegationInviteToken?.let { token ->
            runCatching { Cloud.invite(token).update(mapOf("ownerName" to name, "preview.title" to name)).await() }
        }
        runCatching {
            Cloud.delegations().whereArrayContains("helperUids", uid).get().await().documents.forEach { doc ->
                runCatching { doc.reference.update("helpers.$uid.name", name).await() }
            }
        }
    }

    companion object {
        private const val TAG = "Delegation"
        /**
         * Un `Alarm` para escribir con `SetOptions.merge()`: lo opcional que no trae se
         * borra explícitamente, porque el merge profundo dejaría lo viejo (unas notas
         * quitadas, el código de un reto QR). No se usa `mergeFields`: con un campo
         * transformado en la lista (`revision` + 1), el SDK lo vacía antes de sumar y
         * la revisión volvería a 1.
         */
        internal fun replacingAlarm(alarm: Map<String, Any>): Map<String, Any> {
            val out = LinkedHashMap(alarm)
            for (key in listOf("notes", "symbolName", "customCategory", "challenge", "tone")) {
                if (key !in out) out[key] = FieldValue.delete()
            }
            @Suppress("UNCHECKED_CAST")
            (out["challenge"] as? Map<String, Any>)?.let { challenge ->
                if ("code" !in challenge) out["challenge"] = challenge + ("code" to FieldValue.delete())
            }
            return out
        }
        /** Las marcas de borrado se quedan un mes: de sobra para que las vea quien estaba sin red. */
        private const val TOMBSTONE_TTL = 30L * 24 * 3600 * 1000
    }
}
