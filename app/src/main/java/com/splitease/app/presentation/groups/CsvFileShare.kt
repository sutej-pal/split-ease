package com.splitease.app.presentation.groups

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Opens or shares a CSV via a FileProvider URI.
 *
 * Android's [android.webkit.MimeTypeMap] maps `.csv` to
 * `text/comma-separated-values`, not `text/csv`. Sending the RFC type
 * makes Gmail/Drive/Sheets/Files reject or fail to open the attachment.
 */
internal object CsvFileShare {
    const val MIME = "text/comma-separated-values"

    private val MIME_ALTERNATES =
        arrayOf(
            MIME,
            "text/csv",
            "application/csv",
            "text/plain",
            "application/vnd.ms-excel",
        )

    /**
     * Opens [share] in Sheets/Excel/Files when a viewer exists; otherwise the share sheet.
     *
     * @return true if an activity was started.
     */
    fun openOrShare(
        context: Context,
        share: PendingFileShare,
        chooserTitle: String,
    ): Boolean {
        grantRead(context, share.uri)
        val view = viewIntent(context, share)
        val send = sendIntent(context, share)
        val canView = canHandle(context, view)
        val target = if (canView) view else send
        val extra = if (canView) send else null
        return startChooser(context, target, extra, chooserTitle, share.uri, share.fileName)
    }

    private fun startChooser(
        context: Context,
        target: Intent,
        extra: Intent?,
        chooserTitle: String,
        uri: Uri,
        fileName: String,
    ): Boolean =
        runCatching {
            val clip = ClipData.newUri(context.contentResolver, fileName, uri)
            val chooser =
                Intent.createChooser(target, chooserTitle).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = clip
                    extra?.let { putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it)) }
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            context.startActivity(chooser)
        }.onFailure { err ->
            Log.e("GroupExport", "CSV chooser failed", err)
        }.isSuccess

    private fun viewIntent(
        context: Context,
        share: PendingFileShare,
    ): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(share.uri, MIME)
            clipData = ClipData.newUri(context.contentResolver, share.fileName, share.uri)
            putExtra(Intent.EXTRA_TITLE, share.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun sendIntent(
        context: Context,
        share: PendingFileShare,
    ): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, share.uri)
            putExtra(Intent.EXTRA_SUBJECT, share.fileName)
            putExtra(Intent.EXTRA_TITLE, share.fileName)
            putExtra(Intent.EXTRA_MIME_TYPES, MIME_ALTERNATES)
            clipData = ClipData.newUri(context.contentResolver, share.fileName, share.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun canHandle(
        context: Context,
        intent: Intent,
    ): Boolean = queryHandlers(context, intent).isNotEmpty()

    private fun grantRead(
        context: Context,
        uri: Uri,
    ) {
        val actions = arrayOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)
        for (action in actions) {
            for (mime in MIME_ALTERNATES) {
                val probe =
                    Intent(action).apply {
                        setDataAndType(uri, mime)
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }
                queryHandlers(context, probe).forEach { resolve ->
                    runCatching {
                        context.grantUriPermission(
                            resolve.activityInfo.packageName,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
        }
    }

    private fun queryHandlers(
        context: Context,
        intent: Intent,
    ): List<ResolveInfo> {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_ALL
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags)
        }
    }
}
