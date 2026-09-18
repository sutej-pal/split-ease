package com.splitease.app.domain.account

import com.splitease.app.core.ErrorMessages
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Maps PostgREST / fake-RPC failures for [com.splitease.app.domain.repository.AuthRepository.deleteOwnAccount].
 */
object AccountDeletionErrors {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Converts a thrown RPC/network error into a typed deletion failure when possible.
     *
     * @param error Underlying throwable from the Supabase client or a repository fake.
     * @return [AccountDeletionBlockedException], [AccountDeletionOfflineException], or [error].
     */
    fun map(error: Throwable): Throwable {
        if (isOffline(error)) return AccountDeletionOfflineException()
        val text = flattenMessages(error)
        parseBalancePayload(text)?.let { groups ->
            return AccountDeletionBlockedException(groups)
        }
        if (text.contains("ACCOUNT_HAS_BALANCE")) {
            return AccountDeletionBlockedException(emptyList())
        }
        return error
    }

    /**
     * Parses `{"code":"ACCOUNT_HAS_BALANCE","groups":[...]}` from an exception message.
     *
     * @param raw Combined exception messages.
     * @return Blocking groups when the payload is present; null otherwise.
     */
    fun parseBalancePayload(raw: String): List<AccountDeletionBlockingGroup>? {
        val unescaped = raw.replace("\\\"", "\"")
        val blob = extractJsonObjectContaining(unescaped, "ACCOUNT_HAS_BALANCE") ?: return null
        val payload =
            runCatching { json.decodeFromString<BalanceErrorPayload>(blob) }.getOrNull()
                ?: return null
        if (payload.code != "ACCOUNT_HAS_BALANCE") return null
        return payload.groups.map { row ->
            AccountDeletionBlockingGroup(
                groupId = row.id.orEmpty(),
                groupName = row.name.orEmpty().ifBlank { row.id.orEmpty() },
            )
        }
    }

    private fun extractJsonObjectContaining(
        raw: String,
        token: String,
    ): String? {
        val tokenAt = raw.indexOf(token)
        if (tokenAt < 0) return null
        val start = raw.lastIndexOf('{', tokenAt)
        if (start < 0) return null
        var depth = 0
        for (index in start until raw.length) {
            when (raw[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return raw.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun isOffline(error: Throwable): Boolean =
        error is AccountDeletionOfflineException ||
            ErrorMessages.isNetworkError(error)

    private fun flattenMessages(error: Throwable): String =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")

    @Serializable
    private data class BalanceErrorPayload(
        val code: String? = null,
        val groups: List<BalanceErrorGroup> = emptyList(),
    )

    @Serializable
    private data class BalanceErrorGroup(
        val id: String? = null,
        @SerialName("name") val name: String? = null,
    )
}
