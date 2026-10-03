package com.splitease.app.domain.model

/**
 * Secondary email address linked to an auth user account.
 *
 * @property id Unique email row ID.
 * @property email Email address.
 * @property isVerified True when verified via 6-digit OTP code.
 * @property createdAtMs Creation timestamp.
 */
data class SecondaryEmail(
    val id: String,
    val email: String,
    val isVerified: Boolean,
    val createdAtMs: Long = 0L,
)
