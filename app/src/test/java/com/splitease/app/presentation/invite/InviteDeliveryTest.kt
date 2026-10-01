package com.splitease.app.presentation.invite

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InviteDeliveryTest {
    private val phone = "+919876543210"
    private val body = "Join me on SplitEase https://example.test/invite"

    @Test
    fun confirm_before_opening_shows_dialog_and_only_confirm_launches() {
        val action =
            resolveInviteDelivery(
                shareText = body,
                phone = phone,
                showSmsPrompt = true,
                confirmBeforeOpening = InviteDeliveryPolicy.EDIT_CONTACT_CONFIRMS,
            )
        assertEquals(InviteDeliveryAction.ShowDialog, action)
        assertTrue(InviteDeliveryPolicy.EDIT_CONTACT_CONFIRMS)

        assertNull(launchAfterConfirm(phone, body, confirmed = false, smsAppMissing = false))
        assertEquals(
            InviteLaunch.Sms(phone, body),
            launchAfterConfirm(phone, body, confirmed = true, smsAppMissing = false),
        )
    }

    @Test
    fun resend_opens_sms_once_without_a_dialog() {
        val action =
            resolveInviteDelivery(
                shareText = body,
                phone = phone,
                showSmsPrompt = true,
                confirmBeforeOpening = InviteDeliveryPolicy.RESEND_SKIPS_DIALOG,
            )
        assertFalse(InviteDeliveryPolicy.RESEND_SKIPS_DIALOG)
        assertEquals(InviteDeliveryAction.OpenSms(phone, body), action)

        val (first, token) = claimInviteOpen(openedToken = null, token = "$phone\u0000$body")
        assertTrue(first)
        val (second, _) = claimInviteOpen(openedToken = token, token = "$phone\u0000$body")
        assertFalse(second)

        assertEquals(
            InviteLaunch.Sms(phone, body),
            launchWithoutConfirm(action, smsAppMissing = false),
        )
        assertEquals(
            InviteLaunch.Chooser(body),
            launchWithoutConfirm(action, smsAppMissing = true),
        )
    }

    @Test
    fun missing_phone_does_not_open_sms() {
        val emailShare =
            resolveInviteDelivery(
                shareText = body,
                phone = null,
                showSmsPrompt = false,
                confirmBeforeOpening = InviteDeliveryPolicy.RESEND_SKIPS_DIALOG,
            )
        assertEquals(InviteDeliveryAction.OpenChooser(body), emailShare)
        assertEquals(InviteLaunch.Chooser(body), launchWithoutConfirm(emailShare, smsAppMissing = false))

        val promptWithoutPhone =
            resolveInviteDelivery(
                shareText = body,
                phone = null,
                showSmsPrompt = true,
                confirmBeforeOpening = InviteDeliveryPolicy.RESEND_SKIPS_DIALOG,
            )
        assertEquals(InviteDeliveryAction.None, promptWithoutPhone)
        assertNull(launchWithoutConfirm(promptWithoutPhone, smsAppMissing = false))
    }
}
