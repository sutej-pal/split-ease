package com.splitease.app.data.repository

import android.content.Context
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.ProfileDto
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.SignUpResult
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppCurrencies
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Supabase-backed [AuthRepository] that upserts local Room [User] and remote `profiles`.
 */
@Singleton
class SupabaseAuthRepository
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val supabase: SupabaseClient,
        private val userRepository: UserRepository,
        private val categoryRepository: CategoryRepository,
        private val socialRemote: SocialRemoteDataSource,
        private val syncInteractor: Provider<SyncInteractor>,
    ) : AuthRepository {
        override suspend fun getSignedInUserOrNull(): AuthUser? =
            supabase.auth.currentUserOrNull()?.toAuthUser()

        override fun observeSession(): Flow<AuthSession> =
            supabase.auth.sessionStatus
                .onEach { status ->
                    // Stale refresh tokens (e.g. after remote user wipe) leave RefreshFailure;
                    // clear local storage so the UI can leave the auth gate.
                    if (status is SessionStatus.RefreshFailure) {
                        runCatching { supabase.auth.clearSession() }
                    }
                }
                .map { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val info = status.session.user
                            if (info == null) {
                                AuthSession.SignedOut
                            } else {
                                AuthSession.SignedIn(info.toAuthUser())
                            }
                        }
                        SessionStatus.Initializing -> AuthSession.Loading
                        is SessionStatus.NotAuthenticated -> AuthSession.SignedOut
                        is SessionStatus.RefreshFailure -> AuthSession.SignedOut
                    }
                }

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String,
            phoneCountryCode: String,
            phoneNumber: String,
            currencyCode: String,
            photoUri: String?,
        ): Result<SignUpResult> =
            runCatching {
                val trimmedEmail = email.trim()
                val trimmedName = displayName.trim()
                val dialCode = phoneCountryCode.trim().ifBlank { "+91" }
                val nationalNumber = phoneNumber.trim()
                val currency = AppCurrencies.normalizeOrDefault(currencyCode)
                supabase.auth.signUpWith(Email) {
                    this.email = trimmedEmail
                    this.password = password
                    data =
                        buildJsonObject {
                            put("display_name", trimmedName)
                            put("phone_country_code", dialCode)
                            put("phone_number", nationalNumber)
                            put("preferred_currency", currency)
                            if (!photoUri.isNullOrBlank()) {
                                put("photo_url", photoUri.trim())
                            }
                        }
                }
                val session = supabase.auth.currentSessionOrNull()
                if (session == null) {
                    SignUpResult.PendingEmailConfirmation(trimmedEmail)
                } else {
                    // Do not write Room/profiles yet — OTP verify finalizes the account.
                    SignUpResult.SignedIn
                }
            }

        override suspend fun signIn(email: String, password: String): Result<Unit> =
            runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
            }

        override suspend fun isEmailRegistered(email: String): Result<Boolean> =
            runCatching {
                val trimmed = email.trim()
                if (trimmed.isEmpty()) return@runCatching false
                supabase.postgrest
                    .rpc(
                        function = "auth_email_registered",
                        parameters =
                            buildJsonObject {
                                put("p_email", trimmed)
                            },
                    ).decodeAs<Boolean>()
            }

        override suspend fun isPhoneRegistered(
            phoneCountryCode: String,
            phoneNumber: String,
        ): Result<Boolean> =
            runCatching {
                val digits = phoneNumber.filter { it.isDigit() }
                if (digits.isEmpty()) return@runCatching false
                val dial = phoneCountryCode.trim().ifBlank { "+91" }
                supabase.postgrest
                    .rpc(
                        function = "auth_phone_registered",
                        parameters =
                            buildJsonObject {
                                put("p_country_code", dial)
                                put("p_phone", digits)
                            },
                    ).decodeAs<Boolean>()
            }

        override suspend fun sendLoginOtp(email: String): Result<Unit> =
            runCatching {
                supabase.auth.signInWith(OTP) {
                    this.email = email.trim()
                    createUser = false
                }
            }

        override suspend fun verifyLoginOtp(email: String, token: String): Result<Unit> =
            runCatching {
                supabase.auth.verifyEmailOtp(
                    type = OtpType.Email.EMAIL,
                    email = email.trim(),
                    token = token.trim(),
                )
                finalizeAuthenticatedSession()
            }

        override suspend fun resendSignupConfirmation(email: String): Result<Unit> =
            runCatching {
                supabase.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
            }

        override suspend fun verifySignupOtp(email: String, token: String): Result<Unit> =
            runCatching {
                supabase.auth.verifyEmailOtp(
                    type = OtpType.Email.SIGNUP,
                    email = email.trim(),
                    token = token.trim(),
                )
                finalizeAuthenticatedSession()
            }

        private suspend fun finalizeAuthenticatedSession() {
            val sessionUser =
                supabase.auth.currentUserOrNull()
                    ?: error("Email verified but session is missing. Try signing in.")
            persistCurrentUser()
            val local = userRepository.getUserById(sessionUser.id)
            require(local != null) { "Could not save your local profile. Try signing in again." }
            categoryRepository.ensureDefaults()
            hydrateCloudData()
        }

        override suspend fun updateDisplayName(displayName: String): Result<Unit> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val trimmed = displayName.trim()
                    require(trimmed.isNotBlank()) { "Display name cannot be empty." }
                    supabase.auth.updateUser {
                        data = buildJsonObject { put("display_name", trimmed) }
                    }
                    persistCurrentUser()
                }
            }

        override suspend fun updateProfilePhoto(photoUri: String): Result<Unit> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val userId =
                        supabase.auth.currentUserOrNull()?.id
                            ?: error("Not signed in.")
                    val localPath = copyAvatarToInternalStorage(userId, photoUri)
                    supabase.auth.updateUser {
                        data = buildJsonObject { put("photo_url", localPath) }
                    }
                    persistCurrentUser()
                }
            }

        override suspend fun updatePreferredCurrency(currencyCode: String): Result<Unit> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val currency = currencyCode.trim().uppercase()
                    require(currency.length == 3) { "Currency code must be a 3-letter ISO code." }
                    require(AppCurrencies.isSupported(currency)) { "Unsupported currency: $currency" }
                    supabase.auth.updateUser {
                        data = buildJsonObject { put("preferred_currency", currency) }
                    }
                    persistCurrentUser()
                }
            }

        override suspend fun sendPasswordReset(email: String): Result<Unit> =
            runCatching {
                supabase.auth.resetPasswordForEmail(email.trim())
            }

        override suspend fun verifyRecoveryOtp(email: String, token: String): Result<Unit> =
            runCatching {
                supabase.auth.verifyEmailOtp(
                    type = OtpType.Email.RECOVERY,
                    email = email.trim(),
                    token = token.trim(),
                )
                // Session is required for updatePassword; hydrate after the new password is set.
                check(supabase.auth.currentUserOrNull() != null) {
                    "Recovery code verified but session is missing. Try again."
                }
            }

        override suspend fun updatePassword(newPassword: String): Result<Unit> =
            runCatching {
                val trimmed = newPassword.trim()
                require(trimmed.length >= 8) { "Password must be at least 8 characters." }
                supabase.auth.updateUser {
                    password = trimmed
                }
                finalizeAuthenticatedSession()
            }

        override suspend fun signOut(): Result<Unit> =
            runCatching {
                supabase.auth.signOut()
            }

        override suspend fun ensureLocalProfile(): Result<Unit> =
            runCatching {
                persistCurrentUser()
                categoryRepository.ensureDefaults()
                hydrateCloudData()
            }

        private suspend fun hydrateCloudData() {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return
            runCatching { syncInteractor.get().syncForUser(userId) }
        }

        private suspend fun persistCurrentUser() {
            val info = supabase.auth.currentUserOrNull() ?: return
            // Unverified accounts must not appear in public.profiles yet.
            if (info.emailConfirmedAt == null) return
            val authUser = info.toAuthUser()
            val now = System.currentTimeMillis()
            val existing = userRepository.getUserById(authUser.userId)
            val meta = info.userMetadata
            val phoneCountryCode =
                meta.stringMeta("phone_country_code") ?: existing?.phoneCountryCode
            val phoneNumber = meta.stringMeta("phone_number") ?: existing?.phoneNumber
            val preferredCurrency =
                meta.stringMeta("preferred_currency") ?: existing?.preferredCurrency
            val photoUrl = meta.stringMeta("photo_url") ?: existing?.photoUrl
            // Invite stubs may already own this email under a different local id.
            // Free the unique email index so the auth user row can be written.
            releaseEmailForUser(authUser.userId, authUser.email)
            userRepository.upsert(
                User(
                    id = authUser.userId,
                    email = authUser.email,
                    displayName = authUser.displayName,
                    photoUrl = photoUrl,
                    phoneCountryCode = phoneCountryCode,
                    phoneNumber = phoneNumber,
                    preferredCurrency = preferredCurrency,
                    remoteId = authUser.userId,
                    createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.SYNCED,
                ),
            )
            runCatching {
                socialRemote.upsertProfile(
                    ProfileDto(
                        id = authUser.userId,
                        email = authUser.email,
                        displayName = authUser.displayName,
                        photoUrl = photoUrl,
                        phoneCountryCode = phoneCountryCode,
                        phoneNumber = phoneNumber,
                        preferredCurrency = preferredCurrency,
                        updatedAtEpochMs = now,
                    ),
                )
            }
        }

        /**
         * Moves any other local user off [email] so [userId] can claim the unique index.
         */
        private suspend fun releaseEmailForUser(
            userId: String,
            email: String,
        ) {
            val trimmed = email.trim()
            if (trimmed.isEmpty()) return
            val conflict = userRepository.getUserByEmail(trimmed) ?: return
            if (conflict.id == userId) return
            userRepository.upsert(
                conflict.copy(
                    email = "local+${conflict.id}@users.local",
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }

        private fun copyAvatarToInternalStorage(
            userId: String,
            photoUri: String,
        ): String {
            val dir = File(appContext.filesDir, "avatars").apply { mkdirs() }
            // Unique path so observers and Compose remember() keys invalidate on replace.
            val dest = File(dir, "${userId}_${System.currentTimeMillis()}.jpg")
            val path =
                AvatarImageIO.copyScaledJpeg(
                    context = appContext,
                    photoUri = photoUri,
                    destFile = dest,
                )
            // Keep the newest couple of files so the UI can still decode the previous
            // path for one frame while profile StateFlow catches up.
            dir.listFiles()
                ?.filter { file ->
                    file.isFile &&
                        (
                            file.name.equals("$userId.jpg", ignoreCase = true) ||
                                (
                                    file.name.startsWith("${userId}_") &&
                                        file.name.endsWith(".jpg", ignoreCase = true)
                                )
                        )
                }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(2)
                ?.forEach { it.delete() }
            return path
        }
    }

private fun kotlinx.serialization.json.JsonObject?.stringMeta(key: String): String? =
    this
        ?.get(key)
        ?.toString()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() }

private fun UserInfo.toAuthUser(): AuthUser {
    val emailValue = email.orEmpty()
    val metaName =
        userMetadata
            ?.get("display_name")
            ?.toString()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
    val fallback = emailValue.substringBefore("@").ifBlank { "Friend" }
    return AuthUser(
        userId = id,
        email = emailValue,
        displayName = metaName ?: fallback,
        emailConfirmed = emailConfirmedAt != null,
    )
}
