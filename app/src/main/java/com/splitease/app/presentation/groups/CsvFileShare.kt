package com.splitease.app.presentation.groups

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Shares a CSV via the system share sheet ([Intent.ACTION_SEND]).
 *
 * Android's [android.webkit.MimeTypeMap] maps `.csv` to
 * `text/comma-separated-values`, not `text/csv`. Sending the RFC type
 * makes Gmail/Drive/Sheets/Files reject or fail to open the attachment.
 *
 * URI grants come from [Intent.FLAG_GRANT_READ_URI_PERMISSION] + [ClipData]
 * on the chooser so only the activity the user picks can read the file.
 */
internal object CsvFileShare {
    const val MIME = "text/comma-separated-values"

    /**
     * Shows the system share sheet for [share].
     *
     * @return true if an activity was started.
     */
    fun share(
        context: Context,
        share: PendingFileShare,
    ): Boolean =
        runCatching {
            val clip = ClipData.newUri(context.contentResolver, share.fileName, share.uri)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = MIME
                    putExtra(Intent.EXTRA_STREAM, share.uri)
                    putExtra(Intent.EXTRA_SUBJECT, share.fileName)
                    putExtra(Intent.EXTRA_TITLE, share.fileName)
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val chooser =
                Intent.createChooser(send, null).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = clip
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            context.startActivity(chooser)
        }.onFailure { err ->
            Log.e("GroupExport", "CSV share sheet failed", err)
        }.isSuccess
}
