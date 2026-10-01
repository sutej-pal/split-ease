package com.splitease.app.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException

class ErrorMessagesTest {
    @Test
    fun unknown_host_is_network_error() {
        assertTrue(ErrorMessages.isNetworkError(UnknownHostException("Unable to resolve host api.example.com")))
    }

    @Test
    fun connect_exception_is_network_error() {
        assertTrue(ErrorMessages.isNetworkError(ConnectException("Failed to connect to adcshfmveuskukfobius.supabase.co/443")))
    }

    @Test
    fun connection_abort_message_is_network_error() {
        assertTrue(ErrorMessages.isNetworkError(SocketException("Software caused connection abort")))
    }

    @Test
    fun google_one_tap_status_7_is_network_error() {
        assertTrue(
            ErrorMessages.isNetworkError(
                IllegalStateException("During begin sign in, failure response from one tap: 7: "),
            ),
        )
    }

    @Test
    fun wrapped_timeout_is_network_error() {
        assertTrue(
            ErrorMessages.isNetworkError(
                RuntimeException(
                    "signInWith failed",
                    IllegalStateException("Request timeout has expired [url=https://example.supabase.co/auth/v1/token]"),
                ),
            ),
        )
    }

    @Test
    fun user_wait_timeout_is_not_network_error() {
        assertFalse(
            ErrorMessages.isNetworkError(
                IllegalStateException("The operation has timed out."),
            ),
        )
    }

    @Test
    fun auth_identity_conflict_is_not_network_error() {
        assertFalse(ErrorMessages.isNetworkError(IllegalStateException("identity_already_exists")))
    }
}
