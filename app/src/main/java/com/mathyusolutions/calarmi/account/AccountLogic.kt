package com.mathyusolutions.calarmi.account

import com.mathyusolutions.calarmi.sharing.ShareMapping
import java.util.Locale

/**
 * Las decisiones de la cuenta que no dependen de Firebase (DESIGN.md › Identidad),
 * separadas para probarlas sin teléfono.
 */
object AccountNames {
    /** Los correos de relé de «Ocultar mi correo» de Apple: su parte local no es un nombre. */
    const val APPLE_RELAY_DOMAIN = "privaterelay.appleid.com"

    /**
     * El nombre al iniciar sesión, el primero que exista:
     * 1. el del proveedor (Google: el del perfil; correo: el «Nombre» de «Crear cuenta»),
     * 2. el `displayName` que ya tiene el usuario de Firebase,
     * 3. el que el teléfono ya tenía guardado (`SharingProfile.name`),
     * 4. la parte del correo antes de la `@`, con mayúscula inicial (salvo relés de Apple),
     * 5. «Usuario de Calarmi» en el idioma de la app (`fallback`).
     */
    fun resolve(providerName: String?, accountName: String?, cachedName: String?, email: String?, fallback: String): String =
        ShareMapping.validName(providerName)
            ?: ShareMapping.validName(accountName)
            ?: ShareMapping.validName(cachedName)
            ?: fromEmail(email)
            ?: ShareMapping.validName(fallback)
            ?: fallback

    /** «ana.perez@gmail.com» → «Ana.perez». `null` si no hay correo o es un relé de Apple. */
    fun fromEmail(email: String?): String? {
        val clean = email?.trim().orEmpty()
        val at = clean.lastIndexOf('@')
        if (at <= 0) return null
        val domain = clean.substring(at + 1)
        if (domain.equals(APPLE_RELAY_DOMAIN, ignoreCase = true)) return null
        val local = clean.substring(0, at).trim()
        return ShareMapping.validName(local.replaceFirstChar { it.titlecase(Locale.ROOT) })
    }
}

/** Qué hacer con lo de este teléfono cuando entra una cuenta (DESIGN.md › Cambio de cuenta). */
enum class AccountChange(val clearsLocalData: Boolean) {
    /** Vuelve la misma cuenta (o una sesión anónima vieja se vinculó y conservó su `uid`): todo sigue igual. */
    SAME(false),
    /** Primera cuenta en este teléfono y nada compartido guardado: no hay nada que limpiar. */
    FIRST(false),
    /** Entra otra cuenta: se borran las copias de lo que otros compartieron con la anterior, etc. */
    SWITCHED(true),
    /**
     * No hay `lastUid` pero sí cosas compartidas: vienen de una copia de seguridad restaurada
     * (la sesión y `lastUid` no viajan en ella). No se sabe de qué cuenta son: se limpian como
     * en un cambio de cuenta, y lo que sí es de esta cuenta vuelve solo al sincronizar.
     */
    RESTORED(true);

    companion object {
        fun decide(lastUid: String?, newUid: String, hasLocalSharedData: Boolean): AccountChange = when {
            lastUid == newUid -> SAME
            lastUid != null -> SWITCHED
            hasLocalSharedData -> RESTORED
            else -> FIRST
        }
    }
}

/** Qué se intentaba: el mismo código de Firebase se explica distinto según el caso. */
enum class AuthMethod {
    GOOGLE,
    EMAIL_SIGN_IN,
    EMAIL_CREATE,
    /** Cerrar sesión, borrar la cuenta, cambiar el nombre: nada que haya escrito la persona. */
    ACCOUNT,
}

/** Por qué no se pudo entrar, en lo que la pantalla sabe explicar. */
enum class AuthFailure {
    /** Correo o contraseña incorrectos (también «no existe esa cuenta»: un solo mensaje). */
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    /** Ese correo ya tiene cuenta con otro método (p. ej. Apple, que no existe en Android). */
    OTHER_PROVIDER,
    USER_DISABLED,
    NETWORK,
    TOO_MANY_REQUESTS,
    /** La persona cerró el selector de Google: no se muestra nada. */
    CANCELLED,
    /** No hay ninguna cuenta de Google en el teléfono. */
    NO_GOOGLE_ACCOUNT,
    /** Este teléfono no puede iniciar sesión con Google (sin servicios de Google Play, p. ej.). */
    GOOGLE_UNAVAILABLE,
    OTHER;

    /** ¿Se explica con un mensaje? (cancelar no). */
    val isSilent: Boolean get() = this == CANCELLED

    companion object {
        /** Los `errorCode` de `FirebaseAuthException` (ERROR_…). */
        fun forFirebaseCode(code: String?, method: AuthMethod): AuthFailure = when (code) {
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" ->
                when (method) {
                    // Con un token de Google, «credencial inválida» no es culpa de lo que escribió.
                    AuthMethod.GOOGLE, AuthMethod.ACCOUNT -> OTHER
                    // Al crear una cuenta (vinculando una sesión vieja), el correo ya existía y la contraseña no era la suya.
                    AuthMethod.EMAIL_CREATE -> EMAIL_IN_USE
                    AuthMethod.EMAIL_SIGN_IN -> INVALID_CREDENTIALS
                }
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE" -> EMAIL_IN_USE
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> OTHER_PROVIDER
            "ERROR_WEAK_PASSWORD" -> WEAK_PASSWORD
            "ERROR_INVALID_EMAIL" -> INVALID_EMAIL
            "ERROR_USER_DISABLED" -> USER_DISABLED
            "ERROR_TOO_MANY_REQUESTS" -> TOO_MANY_REQUESTS
            "ERROR_NETWORK_REQUEST_FAILED" -> NETWORK
            else -> OTHER
        }
    }
}

/** Lo mínimo que se pide antes de mandar el formulario. */
object AccountForm {
    const val MIN_PASSWORD = 8

    /** Un correo con forma de correo (la validación de verdad la hace Firebase). */
    fun looksLikeEmail(raw: String): Boolean {
        val email = raw.trim()
        val at = email.indexOf('@')
        return at > 0 && at == email.lastIndexOf('@') && email.indexOf('.', at) > at + 1 &&
            !email.endsWith(".") && email.none { it.isWhitespace() }
    }

    fun passwordLongEnough(password: String): Boolean = password.length >= MIN_PASSWORD
}
