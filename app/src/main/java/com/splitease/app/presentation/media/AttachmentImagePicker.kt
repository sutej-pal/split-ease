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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
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
import com.splitease.app.data.media.LocalMediaCleanup
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SePrimaryButton
import java.io.File

/** Launches gallery (multi-select) or camera for expense attachments (no crop step). */
@Composable
fun rememberAttachmentImagePicker(
    sourceTitle: String,
    sourceBody: String,
    maxSelection: Int = 10,
    onSelected: (List<String>) -> Unit,
): ImagePickerState {
    val context = LocalContext.current
    val cameraPermissionDenied = stringResource(R.string.msg_camera_permission_denied)
    val cameraCaptureFailed = stringResource(R.string.msg_camera_capture_failed)
    val state = remember { ImagePickerState() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxSelection),
        ) { uris ->
            if (uris.isNotEmpty()) {
                onSelected(uris.map { it.toString() })
            }
        }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) {
                onSelected(listOf(uri.toString()))
            } else if (uri != null) {
                LocalMediaCleanup.deleteCachedCapture(context, uri.toString())
            }
        }

    fun startCameraCapture() {
        val uri = createAttachmentCaptureUri(context)
        if (uri == null) {
            Toast.makeText(context, cameraCaptureFailed, Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCameraCapture()
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
        SeModal(
            onDismissRequest = state::dismiss,
            title = sourceTitle,
            icon = Icons.Filled.AttachFile,
            body = sourceBody,
            dismissLabel = stringResource(R.string.action_cancel),
        ) {
            SePrimaryButton(
                text = stringResource(R.string.expense_attachment_gallery),
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
                        startCameraCapture()
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
            )
        }
    }

    return state
}

private fun createAttachmentCaptureUri(context: Context): Uri? =
    runCatching {
        val dir = File(context.cacheDir, LocalMediaCleanup.ATTACHMENT_CAPTURE_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }.getOrNull()
