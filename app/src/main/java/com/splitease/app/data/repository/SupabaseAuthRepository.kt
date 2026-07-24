package com.splitease.app.data.repository

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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        private val supabase: SupabaseClient,
        private val userRepository: UserRepository,
        private val categoryRepository: CategoryRepository,
        private val socialRemote: SocialRemoteDataSource,
        private val syncInteractor: Provider<SyncInteractor>,
    ) : AuthRepository {
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
        ): Result<SignUpResult> =
            runCatching {
                val trimmedEmail = email.trim()
                supabase.auth.signUpWith(Email) {
                    this.email = trimmedEmail
                    this.password = password
                    data =
                        buildJsonObject {
                            put("display_name", displayName.trim())
                        }
                }
                val session = supabase.auth.currentSessionOrNull()
                if (session == null) {
                    SignUpResult.PendingEmailConfirmation(trimmedEmail)
                } else {
                    persistCurrentUser()
                    categoryRepository.ensureDefaults()
                    hydrateCloudData()
                    SignUpResult.SignedIn
                }
            }

        override suspend fun signIn(email: String, password: String): Result<Unit> =
            runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
                persistCurrentUser()
                categoryRepository.ensureDefaults()
                hydrateCloudData()
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
                persistCurrentUser()
                categoryRepository.ensureDefaults()
                hydrateCloudData()
            }

        override suspend fun sendPasswordReset(email: String): Result<Unit> =
            runCatching {
                supabase.auth.resetPasswordForEmail(email.trim())
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
            val authUser = info.toAuthUser()
            val now = System.currentTimeMillis()
            val existing = userRepository.getUserById(authUser.userId)
            userRepository.upsert(
                User(
                    id = authUser.userId,
                    email = authUser.email,
                    displayName = authUser.displayName,
                    photoUrl = existing?.photoUrl,
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
                        photoUrl = existing?.photoUrl,
                        updatedAtEpochMs = now,
                    ),
                )
            }
        }
    }

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
