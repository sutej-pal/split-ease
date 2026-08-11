package com.splitease.app.presentation.media

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalBody
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTextButton
import java.io.File

/** Gallery / camera picker that always opens a cropper before [onCropped]. Call [launch]. */
class ImagePickerState internal constructor() {
    internal var showSheet by mutableStateOf(false)
        private set

    fun launch() {
        showSheet = true
    }

    internal fun dismiss() {
        showSheet = false
    }
}

/**
 * Picks an image from gallery or camera, then opens [ImageCropDialog] before calling [onCropped].
 */
@Composable
fun rememberImagePicker(
    sourceTitle: String,
    sourceBody: String,
    cropTitle: String,
    cropBody: String,
    cropSpec: ImageCropSpec,
    onCropped: (uri: String) -> Unit,
): ImagePickerState {
    val context = LocalContext.current
    val cameraPermissionDenied = stringResource(R.string.msg_camera_permission_denied)
    val state = remember { ImagePickerState() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCropUri by remember { mutableStateOf<String?>(null) }

    fun openCrop(uri: String) {
        pendingCropUri = uri
    }

    val galleryPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.toString()?.let(::openCrop)
        }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) {
                openCrop(uri.toString())
            }
        }

    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val uri =
                    createCameraCaptureUri(context, cropSpec.cacheSubdir)
                        ?: return@rememberLauncherForActivityResult
                pendingCameraUri = uri
                takePicture.launch(uri)
            } else {
                Toast
                    .makeText(
                        context,
                        cameraPermissionDenied,
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }

    if (state.showSheet) {
        SeModal(onDismissRequest = state::dismiss) {
            SeModalTitle(text = sourceTitle)
            Spacer(modifier = Modifier.height(8.dp))
            SeModalBody(text = sourceBody)
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
                        val uri =
                            createCameraCaptureUri(context, cropSpec.cacheSubdir)
                                ?: return@SePrimaryButton
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

    pendingCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            cropSpec = cropSpec,
            cropTitle = cropTitle,
            cropBody = cropBody,
            onDismiss = { pendingCropUri = null },
            onCropped = { cropped ->
                pendingCropUri = null
                onCropped(cropped)
            },
        )
    }

    return state
}

private fun createCameraCaptureUri(
    context: Context,
    cacheSubdir: String,
): Uri? =
    runCatching {
        val dir = File(context.cacheDir, cacheSubdir).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }.getOrNull()

@Preview(name = "Image picker modal")
@Composable
private fun ImagePickerPreview() {
    SePreview {
        val picker =
            rememberImagePicker(
                sourceTitle = "Change photo",
                sourceBody = "Pick a photo from your gallery or take a new one.",
                cropTitle = "Crop photo",
                cropBody = "Adjust the photo to fit.",
                cropSpec = ImagePickPresets.Avatar,
                onCropped = {},
            )
        LaunchedEffect(Unit) {
            picker.launch()
        }
    }
}
