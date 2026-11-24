package com.mathyusolutions.calarmi.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La cuenta obligatoria (DESIGN.md › Identidad) en lo que no depende de Firebase. */
class AccountLogicTest {
    private val fallback = "Usuario de Calarmi"

    // ------------------------------------------------------------- Nombre

    @Test fun providerNameWinsWhenPresent() {
        assertEquals("Ana Pérez", AccountNames.resolve("Ana Pérez", "Ana", "Anita", "ana@x.com", fallback))
    }

    @Test fun chainFallsThroughInOrder() {
        // 2. el displayName de Firebase
        assertEquals("Ana", AccountNames.resolve(null, "Ana", "Anita", "ana@x.com", fallback))
        // 3. el nombre guardado en el teléfono
        assertEquals("Anita", AccountNames.resolve("  ", null, "Anita", "ana@x.com", fallback))
        // 4. la parte del correo, con mayúscula inicial
        assertEquals("Ana.perez", AccountNames.resolve(null, "", null, "ana.perez@gmail.com", fallback))
        // 5. «Usuario de Calarmi»
        assertEquals(fallback, AccountNames.resolve(null, null, null, null, fallback))
    }

    @Test fun blankNamesAreSkippedAndNamesAreTrimmed() {
        assertEquals("Ana", AccountNames.resolve("   ", " Ana ", null, null, fallback))
    }

    @Test fun namesAreCappedAtTheRulesLimit() {
        val long = "x".repeat(80)
        assertEquals(60, AccountNames.resolve(long, null, null, null, fallback).length)
    }

    @Test fun appleRelayEmailsAreNotNames() {
        assertNull(AccountNames.fromEmail("abc123xyz@privaterelay.appleid.com"))
        assertNull(AccountNames.fromEmail("abc123xyz@PrivateRelay.AppleID.com"))
        assertEquals(fallback, AccountNames.resolve(null, null, null, "abc123xyz@privaterelay.appleid.com", fallback))
    }

    @Test fun emailLocalPartGetsCapitalInitial() {
        assertEquals("Mathyu", AccountNames.fromEmail("mathyu@example.com"))
        assertEquals("Jhon.cardozo", AccountNames.fromEmail(" jhon.cardozo@entel.pe "))
        assertEquals("Ñandú", AccountNames.fromEmail("ñandú@x.com"))
        // Un relé de otro dominio sí sirve.
        assertEquals("Ana", AccountNames.fromEmail("ana@appleid.com"))
    }

    @Test fun malformedEmailsGiveNoName() {
        assertNull(AccountNames.fromEmail(null))
        assertNull(AccountNames.fromEmail(""))
        assertNull(AccountNames.fromEmail("sin-arroba"))
        assertNull(AccountNames.fromEmail("@x.com"))
    }

    // ------------------------------------------------------- Cambio de cuenta

    @Test fun sameAccountKeepsEverything() {
        assertEquals(AccountChange.SAME, AccountChange.decide("uid-a", "uid-a", hasLocalSharedData = true))
        assertFalse(AccountChange.SAME.clearsLocalData)
    }

    @Test fun anotherAccountClearsLocalData() {
        val change = AccountChange.decide("uid-a", "uid-b", hasLocalSharedData = false)
        assertEquals(AccountChange.SWITCHED, change)
        assertTrue(change.clearsLocalData)
    }

    @Test fun firstAccountWithNothingSharedClearsNothing() {
        val change = AccountChange.decide(null, "uid-a", hasLocalSharedData = false)
        assertEquals(AccountChange.FIRST, change)
        assertFalse(change.clearsLocalData)
    }

    @Test fun sharedDataWithoutLastUidComesFromABackup() {
        val change = AccountChange.decide(null, "uid-a", hasLocalSharedData = true)
        assertEquals(AccountChange.RESTORED, change)
        assertTrue(change.clearsLocalData)
    }

    // ------------------------------------------------------------ Errores

    @Test fun wrongPasswordAndUnknownUserShareOneMessage() {
        for (code in listOf("ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS")) {
            assertEquals(code, AuthFailure.INVALID_CREDENTIALS, AuthFailure.forFirebaseCode(code, AuthMethod.EMAIL_SIGN_IN))
        }
    }

    @Test fun invalidCredentialDependsOnWhatWasTried() {
        // Con Google no es culpa de lo escrito; al crear (vinculando) el correo ya era de otra cuenta.
        assertEquals(AuthFailure.OTHER, AuthFailure.forFirebaseCode("ERROR_INVALID_CREDENTIAL", AuthMethod.GOOGLE))
        assertEquals(AuthFailure.EMAIL_IN_USE, AuthFailure.forFirebaseCode("ERROR_WRONG_PASSWORD", AuthMethod.EMAIL_CREATE))
        assertEquals(AuthFailure.OTHER, AuthFailure.forFirebaseCode("ERROR_INVALID_CREDENTIAL", AuthMethod.ACCOUNT))
    }

    @Test fun firebaseCodesMapToFriendlyFailures() {
        val m = AuthMethod.EMAIL_CREATE
        assertEquals(AuthFailure.EMAIL_IN_USE, AuthFailure.forFirebaseCode("ERROR_EMAIL_ALREADY_IN_USE", m))
        assertEquals(AuthFailure.EMAIL_IN_USE, AuthFailure.forFirebaseCode("ERROR_CREDENTIAL_ALREADY_IN_USE", m))
        assertEquals(AuthFailure.WEAK_PASSWORD, AuthFailure.forFirebaseCode("ERROR_WEAK_PASSWORD", m))
        assertEquals(AuthFailure.INVALID_EMAIL, AuthFailure.forFirebaseCode("ERROR_INVALID_EMAIL", m))
        assertEquals(AuthFailure.TOO_MANY_REQUESTS, AuthFailure.forFirebaseCode("ERROR_TOO_MANY_REQUESTS", m))
        assertEquals(AuthFailure.USER_DISABLED, AuthFailure.forFirebaseCode("ERROR_USER_DISABLED", m))
        assertEquals(AuthFailure.OTHER_PROVIDER, AuthFailure.forFirebaseCode("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", AuthMethod.GOOGLE))
        assertEquals(AuthFailure.NETWORK, AuthFailure.forFirebaseCode("ERROR_NETWORK_REQUEST_FAILED", m))
        assertEquals(AuthFailure.OTHER, AuthFailure.forFirebaseCode("ERROR_SOMETHING_NEW", m))
        assertEquals(AuthFailure.OTHER, AuthFailure.forFirebaseCode(null, m))
    }

    @Test fun googlePickerOutcomes() {
        // Cerrar el selector no dice nada; sin cuentas de Google se explica qué hacer.
        assertEquals(AuthFailure.CANCELLED, GetCredentialCancellationException().authFailure(AuthMethod.GOOGLE))
        assertTrue(AuthFailure.CANCELLED.isSilent)
        assertEquals(AuthFailure.NO_GOOGLE_ACCOUNT, NoCredentialException().authFailure(AuthMethod.GOOGLE))
        assertEquals(AuthFailure.GOOGLE_UNAVAILABLE, GetCredentialProviderConfigurationException().authFailure(AuthMethod.GOOGLE))
        assertFalse(AuthFailure.NO_GOOGLE_ACCOUNT.isSilent)
    }

    @Test fun wrappedErrorsAreUnwrapped() {
        assertEquals(AuthFailure.NO_GOOGLE_ACCOUNT, RuntimeException(NoCredentialException()).authFailure(AuthMethod.GOOGLE))
        assertEquals(AuthFailure.NETWORK, java.net.UnknownHostException("calarmi").authFailure(AuthMethod.ACCOUNT))
        assertEquals(AuthFailure.EMAIL_IN_USE, AccountException(AuthFailure.EMAIL_IN_USE).authFailure(AuthMethod.GOOGLE))
        assertEquals(AuthFailure.OTHER, IllegalStateException().authFailure(AuthMethod.ACCOUNT))
    }

    // ---------------------------------------------------------- Formulario

    @Test fun emailShape() {
        assertTrue(AccountForm.looksLikeEmail("ana@gmail.com"))
        assertTrue(AccountForm.looksLikeEmail(" ana.perez+calarmi@sub.example.co "))
        assertFalse(AccountForm.looksLikeEmail("ana"))
        assertFalse(AccountForm.looksLikeEmail("ana@"))
        assertFalse(AccountForm.looksLikeEmail("ana@gmail"))
        assertFalse(AccountForm.looksLikeEmail("ana@.com"))
        assertFalse(AccountForm.looksLikeEmail("ana@@gmail.com"))
        assertFalse(AccountForm.looksLikeEmail("ana perez@gmail.com"))
        assertFalse(AccountForm.looksLikeEmail("ana@gmail.com."))
    }

    @Test fun passwordNeedsEightCharacters() {
        assertFalse(AccountForm.passwordLongEnough("1234567"))
        assertTrue(AccountForm.passwordLongEnough("12345678"))
    }
}
