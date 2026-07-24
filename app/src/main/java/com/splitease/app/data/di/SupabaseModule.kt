package com.splitease.app.data.di

import com.splitease.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

/**
 * Provides the shared [SupabaseClient] configured for Auth + PostgREST.
 *
 * Auth deep-link scheme/host (`splitease://auth-callback`) remains for password-reset
 * and other Auth redirects. Signup email confirmation uses an in-app 6-digit OTP and
 * does not require this URI. Allow-list the redirect in Supabase Dashboard → Authentication
 * → URL configuration when using link-based flows.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    /** Custom scheme for Auth email confirmation / recovery redirects. */
    const val AUTH_DEEP_LINK_SCHEME = "splitease"

    /** Host segment for Auth redirects. */
    const val AUTH_DEEP_LINK_HOST = "auth-callback"

    /**
     * Builds a singleton Supabase client using BuildConfig credentials.
     *
     * @return Configured [SupabaseClient].
     * @throws IllegalStateException if URL or anon key is missing from `local.properties`.
     */
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        require(url.isNotBlank() && key.isNotBlank()) {
            "Missing SUPABASE_URL / SUPABASE_ANON_KEY in local.properties"
        }
        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key,
        ) {
            install(Auth) {
                scheme = AUTH_DEEP_LINK_SCHEME
                host = AUTH_DEEP_LINK_HOST
                // Avoid SessionStatus.Initializing on every background (onStop), which blanked
                // the whole app on a spinner until reload finished — and could hang forever.
                enableLifecycleCallbacks = false
            }
            install(Postgrest)
        }
    }
}
