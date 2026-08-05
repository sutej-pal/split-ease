package com.splitease.app.presentation.groups

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalBody
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTextButton
import java.io.File

/** Gallery / camera picker for group photos. Call [launch] to open the source sheet. */
class GroupPhotoPickerState internal constructor() {
    internal var showSheet by mutableStateOf(false)
        private set

    fun launch() {
        showSheet = true
    }

    internal fun dismiss() {
        showSheet = false
    }
}

@Composable
fun rememberGroupPhotoPicker(
    title: String = stringResource(R.string.group_photo_source_title),
    body: String = stringResource(R.string.group_photo_source_body),
    onPicked: (uri: String) -> Unit,
): GroupPhotoPickerState {
    val context = LocalContext.current
    val state = remember { GroupPhotoPickerState() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.toString()?.let(onPicked)
        }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) {
                onPicked(uri.toString())
            }
        }

    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val uri = createGroupCameraCaptureUri(context) ?: return@rememberLauncherForActivityResult
                pendingCameraUri = uri
                takePicture.launch(uri)
            } else {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.msg_camera_permission_denied),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }

    if (state.showSheet) {
        SeModal(onDismissRequest = state::dismiss) {
            SeModalTitle(text = title)
            Spacer(modifier = Modifier.height(8.dp))
            SeModalBody(text = body)
            Spacer(modifier = Modifier.height(16.dp))
            SePrimaryButton(
                text = stringResource(R.string.account_photo_gallery),
                onClick = {
                    state.dismiss()
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SePrimaryButton(
                text = stringResource(R.string.account_photo_camera),
                onClick = {
                    state.dismiss()
                    val hasPermission =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        val uri = createGroupCameraCaptureUri(context) ?: return@SePrimaryButton
                        pendingCameraUri = uri
                        takePicture.launch(uri)
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            SeTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = state::dismiss,
            )
        }
    }

    return state
}

/**
 * Picks a gallery/camera image, then opens a header-aspect cropper before calling [onCropped].
 */
@Composable
fun rememberGroupCoverPicker(onCropped: (uri: String) -> Unit): GroupPhotoPickerState {
    var pendingCropUri by remember { mutableStateOf<String?>(null) }
    val picker =
        rememberGroupPhotoPicker(
            title = stringResource(R.string.group_cover_source_title),
            body = stringResource(R.string.group_cover_source_body),
        ) { uri ->
            pendingCropUri = uri
        }

    pendingCropUri?.let { uri ->
        CoverImageCropDialog(
            sourceUri = uri,
            onDismiss = { pendingCropUri = null },
            onCropped = { cropped ->
                pendingCropUri = null
                onCropped(cropped)
            },
        )
    }

    return picker
}

private fun createGroupCameraCaptureUri(context: Context): Uri? =
    runCatching {
        val dir = File(context.cacheDir, "group_photos").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }.getOrNull()
