package com.splitease.app.data.repository

import android.content.Context
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.data.media.SupabaseImageAuth
import com.splitease.app.data.media.LocalMediaCleanup
import com.splitease.app.data.media.MediaStorageCleanup
import com.splitease.app.data.remote.ProfilePhotoStorage
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.StorageObjectPaths
import com.splitease.app.data.remote.dto.ProfileDto
import com.splitease.app.data.remote.mapper.isRemoteMediaUrl
import com.splitease.app.data.session.LocalUserDataCleanup
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.SignUpResult
import com.splitease.app.domain.model.SocialSignInResult
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
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val PENDING_SIGNUP_PHOTO_NAME = "pending_signup.jpg"
private const val PROFILE_UPSERT_COALESCE_MS = 2_000L

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
        private val profilePhotoStorage: ProfilePhotoStorage,
        private val mediaStorageCleanup: MediaStorageCleanup,
        private val localUserDataCleanup: LocalUserDataCleanup,
        private val syncInteractor: Provider<SyncInteractor>,
    ) : AuthRepository {
        private val persistUserMutex = Mutex()

        @Volatile
        private var lastProfileUpsertUserId: String? = null

        @Volatile
        private var lastProfileUpsertAtMs: Long = 0L
        override suspend fun getSignedInUserOrNull(): AuthUser? =
            supabase.auth.currentUserOrNull()?.toAuthUser()

        override fun observeSession(): Flow<AuthSession> =
            supabase.auth.sessionStatus
                .onEach { status ->
                    SupabaseImageAuth.update(
                        when (status) {
                            is SessionStatus.Authenticated -> status.session.accessToken
                            else -> null
                        },
                    )
                    // Stale refresh tokens (e.g. after remote user wipe) leave RefreshFailure;
                    // clear local storage so the UI can leave the auth gate.
                    if (status is SessionStatus.RefreshFailure) {
                        runCatching { supabase.auth.clearSession() }
                    }
                }.map { status ->
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
                // Compress into filesDir now. Cache crop URIs do not survive until OTP,
                // and local paths must not be written to Auth metadata.
                withContext(Dispatchers.IO) { persistPendingSignupPhoto(photoUri) }
                supabase.auth.signUpWith(Email) {
                    this.email = trimmedEmail
                    this.password = password
                    data =
                        buildJsonObject {
                            put("display_name", trimmedName)
                            put("phone_country_code", dialCode)
                            put("phone_number", nationalNumber)
                            put("preferred_currency", currency)
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

        override suspend fun signInWithGoogle(
            idToken: String,
            rawNonce: String,
        ): Result<SocialSignInResult> =
            runCatching {
                val token = idToken.trim()
                require(token.isNotEmpty()) { "Google sign-in token is missing." }
                supabase.auth.signInWith(IDToken) {
                    this.idToken = token
                    provider = Google
                    nonce = rawNonce.trim().takeIf { it.isNotEmpty() }
                }
                val info =
                    supabase.auth.currentUserOrNull()
                        ?: error("Google sign-in succeeded but session is missing.")
                SocialSignInResult(isNewUser = info.isNewlyCreatedAccount())
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
                    persistCurrentUser(forceRemoteUpsert = true)
                }
            }

        override suspend fun updateProfilePhoto(photoUri: String): Result<Unit> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val userId =
                        supabase.auth.currentUserOrNull()?.id
                            ?: error("Not signed in.")
                    val previousPhotoUrl =
                        userRepository.getUserById(userId)?.photoUrl
                            ?: supabase.auth.currentUserOrNull()?.userMetadata?.stringMeta("photo_url")
                    val localPath = copyAvatarToInternalStorage(userId, photoUri)
                    val stored = persistPhotoForCloud(userId, localPath) ?: localPath
                    supabase.auth.updateUser {
                        data = buildJsonObject { put("photo_url", stored) }
                    }
                    if (!previousPhotoUrl.isNullOrBlank() && previousPhotoUrl != stored) {
                        mediaStorageCleanup.purgeProfilePhoto(previousPhotoUrl)
                    }
                    persistCurrentUser(forceRemoteUpsert = true)
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
                    persistCurrentUser(forceRemoteUpsert = true)
                }
            }

        override suspend fun requestPasswordReset(email: String): Result<Unit> {
            // Soft-success for unknown addresses (Supabase returns 200 with no mail).
            // Propagate rate-limit / hook failures so the UI can ask the user to wait —
            // those errors do not reveal whether the email is registered once the
            // project-wide email rate limit is raised above the Free default of 2/hour.
            return runCatching {
                withContext(Dispatchers.IO) {
                    supabase.auth.resetPasswordForEmail(email.trim())
                }
            }.onFailure { err ->
                android.util.Log.w("AuthRepo", "requestPasswordReset send failed", err)
            }
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
                check(supabase.auth.currentUserOrNull() != null) {
                    "Session expired. Request a new reset code and try again."
                }
                supabase.auth.updateUser {
                    password = trimmed
                }
                // Password is already changed on the server — don't fail the whole
                // reset if local profile hydrate hiccups (e.g. offline / RLS).
                runCatching { finalizeAuthenticatedSession() }
                    .onFailure { err ->
                        android.util.Log.w(
                            "AuthRepo",
                            "Password updated but session hydrate failed",
                            err,
                        )
                    }
            }

        override suspend fun signOut(): Result<Unit> =
            runCatching {
                supabase.auth.signOut()
                lastProfileUpsertUserId = null
                lastProfileUpsertAtMs = 0L
                // Drop Room + media + user prefs so the next account cannot see leftovers.
                localUserDataCleanup.clearAll()
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

        private suspend fun persistCurrentUser(forceRemoteUpsert: Boolean = false) {
            persistUserMutex.withLock {
                val info = supabase.auth.currentUserOrNull() ?: return
                // Unverified accounts must not appear in public.profiles yet.
                if (info.emailConfirmedAt == null) return
                val now = System.currentTimeMillis()
                if (
                    !forceRemoteUpsert &&
                    lastProfileUpsertUserId == info.id &&
                    now - lastProfileUpsertAtMs < PROFILE_UPSERT_COALESCE_MS
                ) {
                    return
                }
                persistCurrentUserLocked(info)
                lastProfileUpsertUserId = info.id
                lastProfileUpsertAtMs = System.currentTimeMillis()
            }
        }

        private suspend fun persistCurrentUserLocked(info: UserInfo) {
            val authUser = info.toAuthUser()
            val now = System.currentTimeMillis()
            val existing = userRepository.getUserById(authUser.userId)
            val meta = info.userMetadata
            val phoneCountryCode =
                meta.stringMeta("phone_country_code") ?: existing?.phoneCountryCode
            val phoneNumber = meta.stringMeta("phone_number") ?: existing?.phoneNumber
            val preferredCurrency =
                meta.stringMeta("preferred_currency") ?: existing?.preferredCurrency
            val metaPhotoUrl =
                meta.stringMeta("photo_url")
                    ?: meta.stringMeta("avatar_url")
                    ?: meta.stringMeta("picture")
            val pendingPath = pendingSignupPhotoFile().takeIf { it.isFile }?.absolutePath
            val sourcePhotoUrl =
                resolveSignupPhotoSource(
                    metaPhotoUrl = metaPhotoUrl,
                    pendingPath = pendingPath,
                    existingPhotoUrl = existing?.photoUrl,
                    isOurAvatarUrl = ::isOurAvatarUrl,
                )
            val photoUrl = persistPhotoForCloud(authUser.userId, sourcePhotoUrl)
            if (
                pendingPath != null &&
                (
                    sourcePhotoUrl != pendingPath ||
                        (photoUrl != null && photoUrl != pendingPath)
                )
            ) {
                clearPendingSignupPhoto()
            }
            if (
                photoUrl != null &&
                photoUrl.isRemoteMediaUrl() &&
                photoUrl != metaPhotoUrl
            ) {
                runCatching {
                    supabase.auth.updateUser {
                        data = buildJsonObject { put("photo_url", photoUrl) }
                    }
                }
            }
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
                        photoUrl =
                            photoUrl?.takeIf { it.isRemoteMediaUrl() }
                                ?: existing?.photoUrl?.takeIf { it.isRemoteMediaUrl() },
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

        /**
         * Returns an https Storage URL when upload succeeds, otherwise a device-local path
         * that this install can still decode.
         *
         * External https avatars (Google) are downloaded, compressed, and re-uploaded to
         * `user-avatars` so we do not keep full-size third-party files on disk.
         */
        private suspend fun persistPhotoForCloud(
            userId: String,
            raw: String?,
        ): String? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (value.isRemoteMediaUrl() && isOurAvatarUrl(value)) {
                runCatching { AvatarImageIO.cacheRemoteImage(appContext, value) }
                return value
            }
            val localPath =
                if (isExistingLocalJpeg(value)) {
                    value
                } else {
                    runCatching { copyAvatarToInternalStorage(userId, value) }.getOrNull()
                } ?: return value.takeIf { it.isRemoteMediaUrl() }
            val uploaded =
                runCatching { profilePhotoStorage.uploadPhoto(userId, localPath) }.getOrNull()
                    ?: return localPath
            AvatarImageIO.seedRemoteImageCache(appContext, uploaded, File(localPath))
            return uploaded
        }

        private fun isOurAvatarUrl(url: String): Boolean =
            StorageObjectPaths.objectPathFromPublicUrl(url, ProfilePhotoStorage.BUCKET) != null

        private fun pendingSignupPhotoFile(): File =
            File(File(appContext.filesDir, "avatars").apply { mkdirs() }, PENDING_SIGNUP_PHOTO_NAME)

        /**
         * Writes a 512px JPEG into [pendingSignupPhotoFile], or deletes a leftover pending
         * file when [photoUri] is blank.
         */
        private fun persistPendingSignupPhoto(photoUri: String?) {
            val dest = pendingSignupPhotoFile()
            val uri = photoUri?.trim()?.takeIf { it.isNotEmpty() }
            if (uri == null) {
                runCatching { dest.delete() }
                return
            }
            runCatching {
                AvatarImageIO.copyScaledJpeg(
                    context = appContext,
                    photoUri = uri,
                    destFile = dest,
                    maxSidePx = AvatarImageIO.STORED_MAX_SIDE_PX,
                    quality = AvatarImageIO.AVATAR_STORED_JPEG_QUALITY,
                )
            }.onFailure {
                runCatching { dest.delete() }
            }
        }

        private fun clearPendingSignupPhoto() {
            runCatching { pendingSignupPhotoFile().delete() }
        }

        private fun isExistingLocalJpeg(path: String): Boolean {
            if (path.startsWith("content:", ignoreCase = true)) return false
            val filePath =
                if (path.startsWith("file:", ignoreCase = true)) {
                    android.net.Uri.parse(path).path
                } else {
                    path
                }
            return !filePath.isNullOrBlank() && File(filePath).isFile
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
                    maxSidePx = AvatarImageIO.STORED_MAX_SIDE_PX,
                    quality = AvatarImageIO.AVATAR_STORED_JPEG_QUALITY,
                )
            LocalMediaCleanup.deleteUserAvatars(appContext, userId, keepNewest = 2)
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
        userMetadata.stringMeta("display_name")
            ?: userMetadata.stringMeta("full_name")
            ?: userMetadata.stringMeta("name")
    val fallback = emailValue.substringBefore("@").ifBlank { "Friend" }
    return AuthUser(
        userId = id,
        email = emailValue,
        displayName = metaName ?: fallback,
        emailConfirmed = emailConfirmedAt != null,
    )
}

/**
 * True when this session looks like the account's first sign-in (welcome mail).
 */
private fun UserInfo.isNewlyCreatedAccount(): Boolean {
    val created = createdAt ?: return false
    val lastSignIn = lastSignInAt ?: created
    return kotlin.math.abs(created.epochSeconds - lastSignIn.epochSeconds) < 120
}

/**
 * Chooses which photo to persist after signup OTP / Google sign-in.
 *
 * Our Storage URL always wins so we do not re-upload on every hydrate. A leftover
 * pending JPEG must not replace Google's `picture` URL. Local pending files beat
 * cache `file://` metadata leftovers from older builds.
 */
internal fun resolveSignupPhotoSource(
    metaPhotoUrl: String?,
    pendingPath: String?,
    existingPhotoUrl: String?,
    isOurAvatarUrl: (String) -> Boolean,
): String? {
    val meta = metaPhotoUrl?.trim()?.takeIf { it.isNotEmpty() }
    val pending = pendingPath?.trim()?.takeIf { it.isNotEmpty() }
    val existing = existingPhotoUrl?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        meta != null && isOurAvatarUrl(meta) -> meta
        pending != null && (meta == null || !meta.isRemoteMediaUrl()) -> pending
        meta != null -> meta
        pending != null -> pending
        else -> existing
    }
}
