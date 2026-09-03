package com.splitease.app.presentation.ads

import com.splitease.app.BuildConfig

/**
 * Resolves AdMob identifiers from [BuildConfig].
 *
 * Debug builds always use Google test IDs. Release builds read optional
 * unit IDs from `local.properties`.
 */
object AdConfig {
    val groupDetailBannerUnitId: String
        get() = BuildConfig.ADMOB_GROUP_DETAIL_BANNER_UNIT_ID.trim()

    val addExpenseBannerUnitId: String
        get() = BuildConfig.ADMOB_ADD_EXPENSE_BANNER_UNIT_ID.trim()

    val isEnabled: Boolean
        get() = groupDetailBannerUnitId.isNotBlank() || addExpenseBannerUnitId.isNotBlank()
}
