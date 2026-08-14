package com.splitease.app.data.media

/**
 * Holds the current Supabase user JWT for HTTP image fetches outside the Supabase client.
 *
 * Public buckets (`expense-receipts`, `user-avatars`) allow Storage `select` without a
 * user JWT once migrations are applied. The JWT is still forwarded when present so
 * buckets that keep authenticated-only policies continue to work.
 */
object SupabaseImageAuth {
    @Volatile
    var accessToken: String? = null
        private set

    fun update(accessToken: String?) {
        this.accessToken = accessToken?.trim()?.takeIf { it.isNotEmpty() }
    }
}
