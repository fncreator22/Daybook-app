package com.sr2ma.daybook.sync

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Thin wrapper around Android Credential Manager for Google Sign-In.
 *
 * Scopes are provided by the caller (incremental consent): when the user first
 * enables Calendar sync, the Calendar scope is requested; when Drive backup is
 * first enabled, the Drive appDataFolder scope is requested.
 *
 * Tokens are stored exclusively in [SyncPreferences] (EncryptedSharedPreferences).
 * No token is ever logged.
 *
 * IMPORTANT: This class performs a sign-in flow using the ID token only (for
 * display name / email).  The actual OAuth access token for Calendar and Drive
 * is obtained via the GoogleSignIn legacy API in [SyncManager], which supports
 * requestScopes(). Credential Manager does not yet support scope-based OAuth
 * flows directly on all API levels.
 */
class GoogleAuthClient(
    private val context: Context,
    private val syncPrefs: SyncPreferences,
    private val serverClientId: String,
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * Initiates a Google Sign-In using Credential Manager.
     * Returns the email on success, or null on cancellation/failure.
     * Tokens are stored in [syncPrefs] — never returned to caller.
     */
    suspend fun signIn(): String? {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                // Store email — never store the ID token itself in a log
                syncPrefs.accountEmail = email
                email
            } else {
                null
            }
        } catch (e: GetCredentialException) {
            throw Exception(e.errorMessage?.toString() ?: "Sign-in failed (${e.type})")
        }
    }

    /**
     * Signs out: clears the credential state from Credential Manager and wipes
     * all tokens from EncryptedSharedPreferences.
     */
    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Not a fatal error — we still clear local state below.
        }
        syncPrefs.clearAuth()
    }
}

