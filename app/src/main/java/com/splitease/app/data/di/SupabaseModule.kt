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
 * Deep-link scheme/host must match [com.splitease.app.MainActivity] intent filters and
 * Supabase Dashboard → Authentication → URL configuration redirect allow-list:
 * `splitease://auth-callback`
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
            }
            install(Postgrest)
        }
    }
}
