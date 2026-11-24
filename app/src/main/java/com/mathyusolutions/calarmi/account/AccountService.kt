package com.mathyusolutions.calarmi.account

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.functions.FirebaseFunctionsException
import com.mathyusolutions.calarmi.AppContainer
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.sharing.Cloud
import com.mathyusolutions.calarmi.sharing.ShareMapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Si hay cuenta (lo único que decide si se ve la app). */
sealed interface AccountState {
    /** Antes del primer desbloqueo: la sesión vive en el almacenamiento cifrado y todavía no se puede leer. */
    data object Locked : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val uid: String, val email: String?) : AccountState
}

class AccountException(val failure: AuthFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/** Lo que falló, en lo que la pantalla sabe explicar. */
fun Throwable.authFailure(method: AuthMethod): AuthFailure = when (this) {
    is AccountException -> failure
    is GetCredentialCancellationException -> AuthFailure.CANCELLED
    is NoCredentialException -> AuthFailure.NO_GOOGLE_ACCOUNT
    is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException -> AuthFailure.GOOGLE_UNAVAILABLE
    is FirebaseAuthException -> AuthFailure.forFirebaseCode(errorCode, method)
    is FirebaseNetworkException -> AuthFailure.NETWORK
    is FirebaseTooManyRequestsException -> AuthFailure.TOO_MANY_REQUESTS
    is FirebaseFunctionsException -> when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AuthFailure.NETWORK
        else -> cause?.authFailure(method) ?: AuthFailure.OTHER
    }
    is java.io.IOException -> AuthFailure.NETWORK
    else -> cause?.authFailure(method) ?: AuthFailure.OTHER
}

/**
 * La cuenta obligatoria (DESIGN.md › Identidad): Google por Credential Manager o
 * correo y contraseña de Firebase Auth. Decide si se ve la app (`state`), y hace
 * lo que viene con entrar, salir o borrar la cuenta: «cambio de cuenta», el
 * nombre que sale de la cuenta, el token de push y los listeners.
 *
 * El motor de alarmas no pasa nunca por aquí: suena con o sin sesión. Y nada de
 * esto toca Firebase antes del primer desbloqueo (`AppContainer.isUserUnlocked`).
 */
class AccountService(private val container: AppContainer) {
    private val sharing get() = container.sharing
    private val profile get() = container.sharing.profile

    private val _state = MutableStateFlow<AccountState>(AccountState.Locked)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Entrar, salir y borrar van de a uno. */
    private val mutex = Mutex()
    /** Mientras se completa una operación, el listener de Auth no cambia `state` por su cuenta. */
    @Volatile private var busy = false
    @Volatile private var listening = false
    @Volatile private var reconciled = false

    /** En el almacenamiento normal, como la sesión de Firebase. */
    private val prefs by lazy { container.app.getSharedPreferences("account", Context.MODE_PRIVATE) }

    /** Un nombre que no se pudo escribir en Firebase Auth (sin red): se reintenta al volver. */
    private var pendingDisplayName: String?
        get() = prefs.getString("pendingDisplayName", null)
        set(value) = prefs.edit().apply { if (value == null) remove("pendingDisplayName") else putString("pendingDisplayName", value) }.apply()

    // ------------------------------------------------------------------- Estado

    /** Mira la sesión. Lo llama `MainActivity` al crearse y cada vez que vuelve. */
    fun refresh() {
        if (!container.isUserUnlocked) {
            _state.value = AccountState.Locked
            return
        }
        val auth = Cloud.auth()
        if (!listening) {
            listening = true
            auth.addAuthStateListener { onAuthChanged(it.currentUser) }
        }
        if (busy) return
        val user = auth.currentUser
        _state.value = stateOf(user)
        if (user != null && !user.isAnonymous) reconcile(user)
    }

    private fun stateOf(user: FirebaseUser?): AccountState =
        if (user != null && !user.isAnonymous) AccountState.SignedIn(user.uid, user.email) else AccountState.SignedOut

    /**
     * La sesión se cerró sola (la cuenta se borró desde otro teléfono, se desactivó o se
     * revocó el token): de vuelta a iniciar sesión. Los datos se quedan; si entra otra
     * cuenta, «cambio de cuenta» los limpia.
     */
    private fun onAuthChanged(user: FirebaseUser?) {
        if (busy) return
        if (_state.value is AccountState.SignedIn && stateOf(user) is AccountState.SignedOut) {
            sharing.stopListening()
            sharing.forgetSession()
            _state.value = AccountState.SignedOut
        }
    }

    /**
     * Una vez por proceso, con sesión: el nombre de Firebase Auth es la fuente de verdad
     * (pudo cambiar en otro dispositivo) y la copia del teléfono lo sigue; si aquí quedó
     * un cambio sin subir, se sube primero.
     */
    private fun reconcile(user: FirebaseUser) {
        if (reconciled) return
        reconciled = true
        container.scope.launch {
            pendingDisplayName?.let { pending ->
                if (setDisplayName(user, pending)) pendingDisplayName = null
                return@launch
            }
            runCatching { user.reload().await() }
            val fresh = Cloud.auth().currentUser?.takeIf { !it.isAnonymous && it.uid == user.uid } ?: return@launch
            val remote = ShareMapping.validName(fresh.displayName)
            if (remote != null) {
                if (remote != profile.name.value) profile.setName(remote)
            } else {
                // Sin nombre en la cuenta (no debería pasar): el de aquí pasa a ser el de la cuenta.
                val name = sharing.myName()
                profile.setName(name)
                if (!setDisplayName(fresh, name)) pendingDisplayName = name
            }
            if (!busy && _state.value is AccountState.SignedIn) _state.value = stateOf(fresh)
        }
    }

    // ----------------------------------------------------------------- Entrar

    /**
     * «Continuar con Google»: el selector de cuentas de Credential Manager (necesita la
     * actividad), el token de Google cambiado por una credencial de Firebase.
     */
    suspend fun signInWithGoogle(activity: Activity) {
        val google = try {
            val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw AccountException(AuthFailure.OTHER)
            }
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw failed(e, AuthMethod.GOOGLE)
        }
        operation(AuthMethod.GOOGLE) {
            val result = signIn(GoogleAuthProvider.getCredential(google.idToken, null), usableAnonymousUser())
            finishSignIn(result, providerName = google.displayName)
        }
    }

    /** «Iniciar sesión» con correo y contraseña. */
    suspend fun signInWithEmail(email: String, password: String) = operation(AuthMethod.EMAIL_SIGN_IN) {
        val result = signIn(EmailAuthProvider.getCredential(email.trim(), password), usableAnonymousUser())
        finishSignIn(result, providerName = null)
    }

    /** «Crear cuenta»: el nombre que escribe la persona es el primero de la cadena. */
    suspend fun createAccount(name: String, email: String, password: String) = operation(AuthMethod.EMAIL_CREATE) {
        val anonymous = usableAnonymousUser()
        val result = if (anonymous != null) {
            signIn(EmailAuthProvider.getCredential(email.trim(), password), anonymous)
        } else {
            Cloud.auth().createUserWithEmailAndPassword(email.trim(), password).await()
        }
        finishSignIn(result, providerName = name)
    }

    /** «¿Olvidaste tu contraseña?»: Firebase manda el enlace (en el idioma de la app). */
    suspend fun sendPasswordReset(email: String) = operation(AuthMethod.EMAIL_SIGN_IN) {
        val auth = Cloud.auth()
        auth.setLanguageCode(sharing.language())
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Entrar con una credencial. Una sesión anónima de una versión anterior se vincula
     * (conserva el `uid` y lo compartido); si la credencial ya es de otra cuenta, se
     * entra en esa y después se aplica «cambio de cuenta».
     */
    private suspend fun signIn(credential: AuthCredential, anonymousUser: FirebaseUser?): AuthResult {
        val auth = Cloud.auth()
        val anonymous = anonymousUser ?: return auth.signInWithCredential(credential).await()
        return try {
            anonymous.linkWithCredential(credential).await()
        } catch (e: FirebaseAuthUserCollisionException) {
            // credential-already-in-use / email-already-in-use
            auth.signInWithCredential(e.updatedCredential ?: credential).await()
        }
    }

    /**
     * La sesión anónima de una versión anterior, si todavía sirve para vincular. Si el
     * servidor ya no la reconoce (se borró o caducó: `INVALID_REFRESH_TOKEN`, usuario
     * inexistente) se descarta y se entra como cualquiera; su `uid` queda en `lastUid`,
     * así que lo que dependía de ella se limpia como en un «cambio de cuenta». Sin red,
     * se dice que no hay conexión en vez de perderla.
     */
    private suspend fun usableAnonymousUser(): FirebaseUser? {
        val auth = Cloud.auth()
        val current = auth.currentUser?.takeIf { it.isAnonymous } ?: return null
        return try {
            current.getIdToken(true).await()
            current
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.authFailure(AuthMethod.ACCOUNT) == AuthFailure.NETWORK) throw e
            Log.w(TAG, "La sesión anónima anterior ya no sirve; se descarta", e)
            auth.signOut()
            null
        }
    }

    /**
     * Lo que sigue a entrar: «cambio de cuenta» si entró otra, `lastUid`, el nombre
     * (DESIGN.md › El nombre sale de la cuenta) en Auth, en `users/{uid}` y en lo
     * compartido, y recién entonces la app.
     */
    private suspend fun finishSignIn(result: AuthResult, providerName: String?) {
        val user = result.user ?: throw AccountException(AuthFailure.OTHER)
        val change = AccountChange.decide(sharing.lastUid, user.uid, sharing.hasLocalSharedData())
        if (change.clearsLocalData) sharing.clearAccountData()
        sharing.lastUid = user.uid

        // El nombre de la cuenta manda una vez que existe (es la fuente de verdad, y se edita
        // en Ajustes › Cuenta): el del proveedor solo lo estrena (cuenta nueva o sin nombre).
        val accountName = ShareMapping.validName(user.displayName)
        val isNew = result.additionalUserInfo?.isNewUser == true
        val cached = profile.name.value
        val name = AccountNames.resolve(
            providerName = providerName.takeIf { isNew || accountName == null },
            accountName = accountName,
            cachedName = cached,
            email = user.email,
            fallback = container.app.getString(R.string.account_default_name),
        )
        if (name != accountName) pendingDisplayName = if (setDisplayName(user, name)) null else name
        profile.setName(name)

        Cloud.restartAfterSignIn()
        _state.value = AccountState.SignedIn(user.uid, user.email)
        sharing.onSignedIn()
        // Lo compartido con el nombre de antes (una sesión anónima que se vinculó, p. ej.).
        if (cached != null && cached != name) sharing.propagateName(name)
        Log.i(TAG, "Sesión iniciada (${change.name})")
    }

    private suspend fun setDisplayName(user: FirebaseUser, name: String): Boolean = withTimeoutOrNull(10_000) {
        runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await() }
            .onFailure { Log.w(TAG, "No se pudo guardar el nombre en la cuenta", it) }
            .isSuccess
    } == true

    // ------------------------------------------------------------------ Nombre

    /** Ajustes › Cuenta › Nombre: Auth, `users/{uid}` y lo compartido (como antes). */
    fun rename(newName: String) {
        val clean = ShareMapping.validName(newName) ?: return
        if (clean == profile.name.value) return
        sharing.rename(clean)
        container.scope.launch {
            val user = Cloud.auth().currentUser?.takeIf { !it.isAnonymous } ?: return@launch
            pendingDisplayName = if (setDisplayName(user, clean)) null else clean
        }
    }

    // ------------------------------------------------------------------ Salir

    /**
     * «Cerrar sesión»: quita el token de push, deja de escuchar, cierra la sesión de
     * Firebase y la de Credential Manager. Las alarmas del teléfono se quedan y suenan.
     */
    suspend fun signOut(context: Context) = operation(AuthMethod.ACCOUNT) {
        withContext(NonCancellable) {
            sharing.prepareSignOut()
            Cloud.auth().signOut()
            clearCredentialState(context)
            _state.value = AccountState.SignedOut
        }
    }

    /**
     * «Borrar cuenta»: la función `deleteAccount` borra los datos y el usuario (con
     * privilegios de administrador, sin pedir un inicio de sesión reciente); después el
     * teléfono se limpia como en «cambio de cuenta» y vuelve a iniciar sesión.
     */
    suspend fun deleteAccount(context: Context) = operation(AuthMethod.ACCOUNT) {
        sharing.stopListening()
        try {
            Cloud.functions().getHttpsCallable(DELETE_FUNCTION).call().await()
        } catch (e: Exception) {
            sharing.resumeListening()
            throw e
        }
        withContext(NonCancellable) {
            Cloud.auth().signOut()
            clearCredentialState(context)
            sharing.clearAccountData()
            sharing.lastUid = null
            pendingDisplayName = null
            Cloud.clearCache()
            _state.value = AccountState.SignedOut
        }
    }

    private suspend fun clearCredentialState(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { Log.w(TAG, "No se pudo limpiar Credential Manager", it) }
    }

    // ---------------------------------------------------------------- Utilidades

    /**
     * Corre en el alcance de la app: si la pantalla se va a mitad (girar, salir), entrar
     * o borrar terminan igual y `state` queda bien.
     */
    private suspend fun <T> operation(method: AuthMethod, block: suspend () -> T): T {
        val work = container.scope.async {
            mutex.withLock {
                busy = true
                try {
                    block()
                } finally {
                    busy = false
                }
            }
        }
        return try {
            work.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw failed(e, method)
        }
    }

    private fun failed(e: Exception, method: AuthMethod): AccountException {
        if (e is AccountException) return e
        val failure = e.authFailure(method)
        if (!failure.isSilent) Log.w(TAG, "Cuenta: $method falló ($failure)", e)
        return AccountException(failure, e)
    }

    companion object {
        private const val TAG = "Account"
        /** DESIGN.md › Borrar la cuenta. */
        const val DELETE_FUNCTION = "deleteAccount"
    }
}
