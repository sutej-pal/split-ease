package com.splitease.app.presentation.invite

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.splitease.app.R
import com.splitease.app.data.social.ContactIdentifier
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone

/** Edit Contact still asks before opening Messages. Resend already confirmed. */
internal object InviteDeliveryPolicy {
    const val EDIT_CONTACT_CONFIRMS = true
    const val RESEND_SKIPS_DIALOG = false
}

internal sealed class InviteDeliveryAction {
    data object ShowDialog : InviteDeliveryAction()

    data class OpenSms(val phone: String, val body: String) : InviteDeliveryAction()

    data class OpenChooser(val body: String) : InviteDeliveryAction()

    data object None : InviteDeliveryAction()
}

internal sealed class InviteLaunch {
    data class Sms(val phone: String, val body: String) : InviteLaunch()

    data class Chooser(val body: String) : InviteLaunch()
}

/**
 * What to do with a pending share.
 *
 * [confirmBeforeOpening] true asks first when [showSmsPrompt] and [phone] are set.
 * False opens Messages immediately for a phone, and leaves email shares on the chooser.
 */
internal fun resolveInviteDelivery(
    shareText: String?,
    phone: String?,
    showSmsPrompt: Boolean,
    confirmBeforeOpening: Boolean,
): InviteDeliveryAction {
    val text = shareText?.takeIf { it.isNotBlank() } ?: return InviteDeliveryAction.None
    val smsPhone = phone?.takeIf { it.isNotBlank() }
    return when {
        showSmsPrompt && smsPhone != null && confirmBeforeOpening -> InviteDeliveryAction.ShowDialog
        smsPhone != null && !confirmBeforeOpening -> InviteDeliveryAction.OpenSms(smsPhone, text)
        !showSmsPrompt -> InviteDeliveryAction.OpenChooser(text)
        else -> InviteDeliveryAction.None
    }
}

/** True only the first time [token] is seen. A repeat (rotation) does not launch again. */
internal fun claimInviteOpen(openedToken: String?, token: String): Pair<Boolean, String> =
    if (openedToken == token) {
        false to token
    } else {
        true to token
    }

/** Confirm opens Messages, or the share chooser when no SMS app exists. Dismiss launches nothing. */
internal fun launchAfterConfirm(
    phone: String,
    body: String,
    confirmed: Boolean,
    smsAppMissing: Boolean,
): InviteLaunch? {
    if (!confirmed) return null
    return if (smsAppMissing) InviteLaunch.Chooser(body) else InviteLaunch.Sms(phone, body)
}

internal fun launchWithoutConfirm(
    action: InviteDeliveryAction,
    smsAppMissing: Boolean,
): InviteLaunch? =
    when (action) {
        is InviteDeliveryAction.OpenSms ->
            if (smsAppMissing) {
                InviteLaunch.Chooser(action.body)
            } else {
                InviteLaunch.Sms(action.phone, action.body)
            }
        is InviteDeliveryAction.OpenChooser -> InviteLaunch.Chooser(action.body)
        InviteDeliveryAction.ShowDialog, InviteDeliveryAction.None -> null
    }

/**
 * Delivers a pending invite.
 *
 * [confirmBeforeOpening] true (Edit Contact) asks before opening the user's SMS app.
 * False (resend) opens it immediately; the tap that started the resend is the confirmation.
 * Email contacts keep the share chooser. [onFinished] runs after either choice and after launch.
 */
@Composable
fun InviteDeliveryHandler(
    shareText: String?,
    phone: String?,
    showSmsPrompt: Boolean,
    onFinished: () -> Unit,
    chooserTitle: String = stringResource(R.string.action_share_invite),
    confirmBeforeOpening: Boolean = InviteDeliveryPolicy.EDIT_CONTACT_CONFIRMS,
) {
    val context = LocalContext.current
    val inviteSubject = stringResource(R.string.invite_email_subject)
    val action =
        resolveInviteDelivery(
            shareText = shareText,
            phone = phone,
            showSmsPrompt = showSmsPrompt,
            confirmBeforeOpening = confirmBeforeOpening,
        )
    var openedToken by rememberSaveable { mutableStateOf<String?>(null) }
    when (action) {
        InviteDeliveryAction.ShowDialog -> {
            val smsPhone = phone?.trim().orEmpty()
            val text = shareText?.trim().orEmpty()
            SeConfirmDialog(
                title = stringResource(R.string.invite_sms_title),
                body =
                    stringResource(
                        R.string.invite_sms_body,
                        ContactIdentifier.displayContact(smsPhone),
                    ),
                confirmLabel = stringResource(R.string.invite_sms_confirm),
                dismissLabel = stringResource(R.string.invite_sms_dismiss),
                onDismissRequest = onFinished,
                onConfirm = {
                    val opened =
                        try {
                            context.startActivity(smsIntent(smsPhone, text))
                            true
                        } catch (_: ActivityNotFoundException) {
                            false
                        }
                    if (!opened) {
                        context.startActivity(shareChooser(chooserTitle, inviteSubject, text))
                    }
                    onFinished()
                },
                icon = Icons.Filled.Sms,
                tone = SeConfirmTone.Primary,
            )
        }
        is InviteDeliveryAction.OpenSms -> {
            val token = "${action.phone}\u0000${action.body}"
            LaunchedEffect(token) {
                val (launch, nextToken) = claimInviteOpen(openedToken, token)
                if (launch) {
                    openedToken = nextToken
                    val opened =
                        try {
                            context.startActivity(smsIntent(action.phone, action.body))
                            true
                        } catch (_: ActivityNotFoundException) {
                            false
                        }
                    if (!opened) {
                        context.startActivity(shareChooser(chooserTitle, inviteSubject, action.body))
                    }
                }
                onFinished()
            }
        }
        is InviteDeliveryAction.OpenChooser -> {
            LaunchedEffect(action.body) {
                context.startActivity(shareChooser(chooserTitle, inviteSubject, action.body))
                onFinished()
            }
        }
        InviteDeliveryAction.None -> Unit
    }
}

private fun smsIntent(e164: String, body: String): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:$e164")
        putExtra("sms_body", body)
    }

private fun shareChooser(title: String, subject: String, text: String): Intent {
    val html = InviteLinks.htmlForShareText(text)
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            if (html != null) {
                putExtra(Intent.EXTRA_HTML_TEXT, html)
            }
        }
    return Intent.createChooser(send, title)
}
