package com.splitease.app.presentation.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManagerFactory
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SePreview

private const val PLAY_STORE_PACKAGE = "com.android.vending"

@Composable
fun RateAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var launching by remember { mutableStateOf(false) }

    SeModal(
        onDismissRequest = { if (!launching) onDismiss() },
        title = stringResource(R.string.rate_app_title),
        body = stringResource(R.string.rate_app_body),
        icon = Icons.Filled.Star,
        tone = SeConfirmTone.Primary,
        confirmLabel = stringResource(R.string.action_rate_on_play),
        confirmBusy = launching,
        onConfirm = {
            if (!launching) {
                launching = true
                launchPlayReviewOrStore(context) {
                    launching = false
                    onDismiss()
                }
            }
        },
        dismissLabel = stringResource(R.string.action_cancel),
    )
}

internal fun launchPlayReviewOrStore(
    context: Context,
    onComplete: () -> Unit = {},
) {
    val activity = context.findActivity()
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
        openPlayListing(context)
        onComplete()
        return
    }
    val manager = ReviewManagerFactory.create(activity)
    manager
        .requestReviewFlow()
        .addOnCompleteListener { request ->
            if (activity.isFinishing || activity.isDestroyed) {
                onComplete()
                return@addOnCompleteListener
            }
            if (request.isSuccessful) {
                manager
                    .launchReviewFlow(activity, request.result)
                    .addOnCompleteListener {
                        onComplete()
                    }
            } else {
                openPlayListing(context)
                onComplete()
            }
        }
}

internal fun openPlayListing(context: Context): Boolean {
    val pkg = context.packageName
    val marketIntent =
        Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()).apply {
            setPackage(PLAY_STORE_PACKAGE)
        }
    if (runCatching { context.startActivity(marketIntent) }.isSuccess) return true
    val webIntent =
        Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$pkg".toUri(),
        )
    return runCatching { context.startActivity(webIntent) }.isSuccess
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Preview(name = "Rate SplitEase", showBackground = true)
@Composable
private fun RateAppDialogPreview() {
    SePreview {
        RateAppDialog(onDismiss = {})
    }
}
