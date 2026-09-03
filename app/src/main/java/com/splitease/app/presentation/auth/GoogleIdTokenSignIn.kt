package com.splitease.app.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.splitease.app.BuildConfig
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

/**
 * Outcome of the Google account picker + ID-token request (before Supabase).
 */
sealed interface GoogleIdTokenOutcome {
    /** User selected an account and an ID token was issued. */
    data class Success(
        val idToken: String,
        val rawNonce: String,
    ) : GoogleIdTokenOutcome

    /** User dismissed the account picker. */
    data object Cancelled : GoogleIdTokenOutcome

    /** [BuildConfig] / caller did not provide a Web client ID. */
    data object NotConfigured : GoogleIdTokenOutcome

    /** No Google account on the device, or Play Services cannot serve one. */
    data object NoAccount : GoogleIdTokenOutcome

    /** Picker or token parse failed for another reason. */
    data object Failed : GoogleIdTokenOutcome
}

/**
 * Prompts for a Google account and returns an OIDC ID token for Supabase.
 *
 * Uses [GetSignInWithGoogleOption] (explicit button), not One Tap.
 *
 * @param activity Host activity (required by Credential Manager UI).
 * @param webClientId Google Cloud **Web** OAuth client ID (not the Android client).
 * @return [GoogleIdTokenOutcome] for the ViewModel to map into form errors / sign-in.
 */
suspend fun requestGoogleIdToken(
    activity: Activity,
    webClientId: String,
): GoogleIdTokenOutcome {
    val serverClientId = webClientId.trim()
    if (serverClientId.isEmpty()) return GoogleIdTokenOutcome.NotConfigured

    val rawNonce = UUID.randomUUID().toString()
    val hashedNonce = sha256Hex(rawNonce)
    val googleOption =
        GetSignInWithGoogleOption
            .Builder(serverClientId)
            .setNonce(hashedNonce)
            .build()
    val request =
        GetCredentialRequest
            .Builder()
            .addCredentialOption(googleOption)
            .build()

    return try {
        val result =
            CredentialManager.create(activity).getCredential(
                context = activity,
                request = request,
            )
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val idToken = googleIdTokenCredential.idToken.trim()
        if (idToken.isEmpty()) {
            GoogleIdTokenOutcome.Failed
        } else {
            GoogleIdTokenOutcome.Success(idToken = idToken, rawNonce = rawNonce)
        }
    } catch (_: GetCredentialCancellationException) {
        GoogleIdTokenOutcome.Cancelled
    } catch (_: NoCredentialException) {
        GoogleIdTokenOutcome.NoAccount
    } catch (_: GoogleIdTokenParsingException) {
        GoogleIdTokenOutcome.Failed
    } catch (_: GetCredentialException) {
        GoogleIdTokenOutcome.Failed
    }
}

/** Walks [ContextWrapper]s to the host [Activity], if any. */
fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Account-picker + Supabase exchange for **Continue with Google**.
 *
 * @param authViewModel Session-aware auth ViewModel.
 * @return Click handler for login / signup / invite-join screens.
 */
@Composable
fun rememberContinueWithGoogle(authViewModel: AuthViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(authViewModel, context, scope) {
        {
            val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            if (webClientId.isBlank()) {
                authViewModel.onGoogleSignInFailed(GoogleIdTokenOutcome.NotConfigured)
            } else {
                val activity = context.findActivity()
                if (activity == null) {
                    authViewModel.onGoogleSignInFailed(GoogleIdTokenOutcome.Failed)
                } else {
                    scope.launch {
                        authViewModel.onGoogleSignInStarted()
                        when (val outcome = requestGoogleIdToken(activity, webClientId)) {
                            GoogleIdTokenOutcome.Cancelled ->
                                authViewModel.onGoogleSignInCancelled()
                            is GoogleIdTokenOutcome.Success ->
                                authViewModel.signInWithGoogle(
                                    idToken = outcome.idToken,
                                    rawNonce = outcome.rawNonce,
                                )
                            GoogleIdTokenOutcome.NotConfigured,
                            GoogleIdTokenOutcome.NoAccount,
                            GoogleIdTokenOutcome.Failed,
                            ->
                                authViewModel.onGoogleSignInFailed(outcome)
                        }
                    }
                }
            }
        }
    }
}

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
