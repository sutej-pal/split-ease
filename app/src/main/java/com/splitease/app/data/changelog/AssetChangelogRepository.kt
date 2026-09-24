package com.splitease.app.data.changelog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the Keep-a-Changelog file packaged as an asset from the repo root `CHANGELOG.md`.
 *
 * @property context Application context.
 */
@Singleton
class AssetChangelogRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Loads changelog markdown from assets.
         *
         * @return File contents, or empty when the asset is missing.
         */
        fun readMarkdown(): String =
            try {
                context.assets
                    .open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() }
            } catch (_: IOException) {
                ""
            }

        companion object {
            const val ASSET_NAME = "CHANGELOG.md"
        }
    }
