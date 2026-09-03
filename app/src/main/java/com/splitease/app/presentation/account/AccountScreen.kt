package com.splitease.app.presentation.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.media.ImagePickPresets
import com.splitease.app.presentation.media.rememberImagePicker
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePageHeader
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeTextButton

@Composable
fun AccountScreen(
    onOpenSettings: () -> Unit,
    onOpenAccountProfile: () -> Unit,
    onOpenSpending: () -> Unit,
    onOpenImport: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val photoPicker =
        rememberImagePicker(
            sourceTitle = stringResource(R.string.account_photo_source_title),
            sourceBody = stringResource(R.string.account_photo_source_body),
            cropTitle = stringResource(R.string.image_crop_title),
            cropBody = stringResource(R.string.image_crop_body),
            cropSpec = ImagePickPresets.Avatar,
            onCropped = viewModel::updatePhoto,
        )

    LaunchedEffect(settings.infoMessage, settings.errorMessage) {
        val message = settings.infoMessage ?: settings.errorMessage
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SePageHeader(title = stringResource(R.string.nav_account))
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AccountProfileHeader(
                    displayName = profile.displayName.ifBlank { stringResource(R.string.account_name_fallback) },
                    email = profile.email,
                    photoUrl = profile.photoUrl,
                    isBusy = settings.isSaving,
                    onEditProfile = onOpenAccountProfile,
                    onChangePhoto = photoPicker::launch,
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SplitEaseColors.Surface)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                AccountHubRow(
                    title = stringResource(R.string.settings_title),
                    subtitle = stringResource(R.string.settings_hub_subtitle),
                    icon = Icons.Filled.Settings,
                    tint = SplitEaseColors.Primary,
                    onClick = onOpenSettings,
                )
                AccountHubRow(
                    title = stringResource(R.string.spending_title),
                    subtitle = stringResource(R.string.spending_hub_subtitle),
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    tint = SplitEaseColors.OwedToYou,
                    onClick = onOpenSpending,
                )
                AccountHubRow(
                    title = stringResource(R.string.import_title),
                    subtitle = stringResource(R.string.import_hub_subtitle),
                    icon = Icons.Filled.Download,
                    tint = SplitEaseColors.Accent,
                    onClick = onOpenImport,
                    showDivider = false,
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            SeOutlinedButton(text = stringResource(R.string.action_sign_out), onClick = onSignOut)
        }
    }
}

@Composable
private fun AccountHubRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    SeListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        showDivider = showDivider,
        leading = { SeIconTile(icon = icon, tint = tint, size = 44) },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SplitEaseColors.NavyMuted,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
private fun AccountProfileHeader(
    displayName: String,
    email: String,
    photoUrl: String?,
    isBusy: Boolean,
    onEditProfile: () -> Unit,
    onChangePhoto: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .clickable(onClick = onChangePhoto),
        ) {
            SeAvatarBadge(
                name = displayName,
                photoUrl = photoUrl,
                size = 96.dp,
                borderWidth = 3.dp,
                borderColor = SplitEaseColors.PrimarySoft,
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SplitEaseColors.Surface)
                        .border(1.dp, SplitEaseColors.Outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = SplitEaseColors.Primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.cd_change_profile_photo),
                        tint = SplitEaseColors.Navy,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = SplitEaseColors.Navy,
        )
        if (email.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )
        }
        SeTextButton(
            text = stringResource(R.string.action_edit),
            onClick = onEditProfile,
        )
    }
}

@Preview(showBackground = true, heightDp = 520)
@Composable
private fun AccountScreenPreview() {
    SePreview {
        Column(modifier = Modifier.padding(20.dp)) {
            AccountProfileHeader(
                displayName = "sutejpal234",
                email = "sutejpal234@gmail.com",
                photoUrl = null,
                isBusy = false,
                onEditProfile = {},
                onChangePhoto = {},
            )
        }
    }
}
