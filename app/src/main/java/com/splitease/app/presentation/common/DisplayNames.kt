package com.splitease.app.presentation.common

/**
 * Splitwise-style short label for tree/branch copy: "Deepak joshi" → "Deepak j.".
 * Strips an optional "(invited)" suffix before shortening.
 */
fun shortDisplayName(name: String): String {
    val cleaned = name.replace(Regex("\\s*\\(invited\\)\\s*", RegexOption.IGNORE_CASE), "").trim()
    val parts = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0]} ${parts[1].first().lowercaseChar()}."
        parts.isNotEmpty() -> parts[0]
        else -> name
    }
}
