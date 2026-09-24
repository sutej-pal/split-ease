package com.splitease.app.presentation.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

class GoogleCredentialFailureTest {
    @Test
    fun network_throwable_maps_to_offline() {
        val outcome =
            classifyGoogleCredentialFailure(
                error = UnknownHostException("Unable to resolve host google.com"),
                deviceOffline = false,
            )
        assertEquals(GoogleIdTokenOutcome.Offline, outcome)
    }

    @Test
    fun offline_device_maps_generic_failure_to_offline() {
        val outcome =
            classifyGoogleCredentialFailure(
                error = IllegalStateException("No credentials available"),
                deviceOffline = true,
            )
        assertEquals(GoogleIdTokenOutcome.Offline, outcome)
    }

    @Test
    fun online_unknown_failure_stays_failed() {
        val outcome =
            classifyGoogleCredentialFailure(
                error = IllegalStateException("internal error"),
                deviceOffline = false,
            )
        assertEquals(GoogleIdTokenOutcome.Failed, outcome)
    }

    @Test
    fun cancellation_wins_over_offline_device() {
        val outcome =
            classifyGoogleCredentialFailure(
                error = androidx.credentials.exceptions.GetCredentialCancellationException(),
                deviceOffline = true,
            )
        assertEquals(GoogleIdTokenOutcome.Cancelled, outcome)
    }
}
