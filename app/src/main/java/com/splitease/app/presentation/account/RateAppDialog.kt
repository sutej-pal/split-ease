package com.splitease.app.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeTextField

private const val RATE_COMMENT_MAX = 280

@Composable
fun RateAppDialog(onDismiss: () -> Unit) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    if (submitted) {
        SeModal(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.rate_app_thanks_title),
            body = stringResource(R.string.rate_app_thanks_body),
            icon = Icons.Filled.Star,
            tone = SeConfirmTone.Primary,
            dismissLabel = stringResource(R.string.action_done),
        )
        return
    }

    SeModal(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.rate_app_title),
        body = stringResource(R.string.rate_app_body),
        icon = Icons.Filled.Star,
        tone = SeConfirmTone.Primary,
        confirmLabel = stringResource(R.string.action_submit),
        onConfirm = { submitted = true },
        confirmEnabled = stars in 1..5,
        content = {
            RatingStars(
                rating = stars,
                onRatingChange = { stars = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextField(
                value = comment,
                onValueChange = { next ->
                    if (next.length <= RATE_COMMENT_MAX) comment = next
                },
                label = stringResource(R.string.rate_app_comment_label),
                placeholder = stringResource(R.string.rate_app_comment_placeholder),
                singleLine = false,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
            )
        },
    )
}

@Composable
private fun RatingStars(
    rating: Int,
    onRatingChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        for (star in 1..5) {
            val selected = star <= rating
            IconButton(onClick = { onRatingChange(star) }) {
                Icon(
                    imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.cd_rate_star, star),
                    tint = if (selected) SplitEaseColors.Accent else SplitEaseColors.IconDefault,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Preview(name = "Rate SplitEase", showBackground = true)
@Composable
private fun RateAppDialogPreview() {
    SePreview {
        RateAppDialog(onDismiss = {})
    }
}
