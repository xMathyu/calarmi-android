package com.mathyusolutions.calarmi.sharing

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Lo que este teléfono recuerda de lo compartido, aparte de la base: el nombre
 * con el que la persona aparece ante los demás, si deja que la ayuden personas
 * de confianza y la contabilidad del espejo de sus alarmas.
 *
 * En el almacenamiento protegido por dispositivo, como los ajustes: la copia de
 * seguridad de Google se lleva el nombre al cambiar de teléfono.
 */
class SharingProfile(storage: Context) {
    private val prefs: SharedPreferences = storage.getSharedPreferences("sharing", Context.MODE_PRIVATE)

    private val _name = MutableStateFlow(prefs.getString(K.NAME, null)?.let(ShareMapping::validName))
    /** El nombre que ven los demás; `null` hasta que la persona pone uno. */
    val name: StateFlow<String?> = _name.asStateFlow()

    fun setName(value: String) {
        val clean = ShareMapping.validName(value) ?: return
        prefs.edit().putString(K.NAME, clean).apply()
        _name.value = clean
    }

    private val _delegationEnabled = MutableStateFlow(prefs.getBoolean(K.DELEGATION, false))
    /** «Permitir personas de confianza»: sus alarmas se reflejan en `delegations/{uid}/reminders`. */
    val delegationEnabled: StateFlow<Boolean> = _delegationEnabled.asStateFlow()

    fun setDelegationEnabled(value: Boolean) {
        prefs.edit().putBoolean(K.DELEGATION, value).apply()
        _delegationEnabled.value = value
        if (!value) {
            delegationInviteToken = null
            clearMirrored()
        }
    }

    /** La invitación vigente para personas de confianza (una sola, como la de un share). */
    var delegationInviteToken: String?
        get() = prefs.getString(K.DELEGATION_TOKEN, null)
        set(value) = prefs.edit().apply { if (value == null) remove(K.DELEGATION_TOKEN) else putString(K.DELEGATION_TOKEN, value) }.apply()

    /** El token de FCM ya guardado en `users/{uid}.tokens` (para quitar el viejo cuando cambia). */
    var registeredPushToken: String?
        get() = prefs.getString(K.PUSH_TOKEN, null)
        set(value) = prefs.edit().apply { if (value == null) remove(K.PUSH_TOKEN) else putString(K.PUSH_TOKEN, value) }.apply()

    /** El uid con el que se registró ese token: si la sesión cambia, se vuelve a registrar. */
    var registeredPushUid: String?
        get() = prefs.getString(K.PUSH_UID, null)
        set(value) = prefs.edit().apply { if (value == null) remove(K.PUSH_UID) else putString(K.PUSH_UID, value) }.apply()

    // --- Espejo de personas de confianza: id de la alarma → `updatedAt` ya subido.
    // Cambia solo lo que cambió desde la última vez y no devuelve lo que llegó de
    // una persona de confianza (si no, cada cambio rebotaría de un lado a otro).

    @Synchronized
    fun mirrored(): Map<String, Long> {
        val json = runCatching { JSONObject(prefs.getString(K.MIRRORED, "{}")!!) }.getOrDefault(JSONObject())
        return json.keys().asSequence().associateWith { json.optLong(it) }
    }

    @Synchronized
    fun setMirrored(id: String, updatedAt: Long?) {
        val json = runCatching { JSONObject(prefs.getString(K.MIRRORED, "{}")!!) }.getOrDefault(JSONObject())
        if (updatedAt == null) json.remove(id) else json.put(id, updatedAt)
        prefs.edit().putString(K.MIRRORED, json.toString()).apply()
    }

    @Synchronized
    private fun clearMirrored() {
        prefs.edit().remove(K.MIRRORED).apply()
    }

    private object K {
        const val NAME = "sharing.name"
        const val DELEGATION = "sharing.delegationEnabled"
        const val DELEGATION_TOKEN = "sharing.delegationInviteToken"
        const val PUSH_TOKEN = "sharing.pushToken"
        const val PUSH_UID = "sharing.pushUid"
        const val MIRRORED = "sharing.delegationMirrored"
    }
}
