package com.splitease.app.data.repository

import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed [AuthRepository] that also upserts the local Room [User].
 *
 * @property supabase Supabase client.
 * @property userRepository Local user cache.
 * @property categoryRepository Used to seed default categories after first sign-in.
 */
@Singleton
class SupabaseAuthRepository
    @Inject
    constructor(
        private val supabase: SupabaseClient,
        private val userRepository: UserRepository,
        private val categoryRepository: CategoryRepository,
    ) : AuthRepository {
        override fun observeSession(): Flow<AuthSession> =
            supabase.auth.sessionStatus.map { status ->
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
        ): Result<Unit> =
            runCatching {
                supabase.auth.signUpWith(Email) {
                    this.email = email.trim()
                    this.password = password
                    data =
                        buildJsonObject {
                            put("display_name", displayName.trim())
                        }
                }
                persistCurrentUser()
                categoryRepository.ensureDefaults()
            }

        override suspend fun signIn(email: String, password: String): Result<Unit> =
            runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
                persistCurrentUser()
                categoryRepository.ensureDefaults()
            }

        override suspend fun sendPasswordReset(email: String): Result<Unit> =
            runCatching {
                supabase.auth.resetPasswordForEmail(email.trim())
            }

        override suspend fun signOut(): Result<Unit> =
            runCatching {
                supabase.auth.signOut()
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
    )
}
