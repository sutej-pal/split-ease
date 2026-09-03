package com.splitease.app.presentation.ads

import android.app.Activity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Wraps the Google User Messaging Platform (UMP) consent flow.
 *
 * Ads must wait until [canRequestAds] is true before loading.
 */
object AdConsentManager {
    private val canRequestAdsState = mutableStateOf(false)
    private val privacyOptionsRequiredState = mutableStateOf(false)

    val canRequestAds: State<Boolean> = canRequestAdsState

    val privacyOptionsRequired: State<Boolean> = privacyOptionsRequiredState

    private var consentInformation: ConsentInformation? = null

    fun gatherConsent(activity: Activity) {
        if (!AdConfig.isEnabled) {
            canRequestAdsState.value = false
            return
        }

        val consentParams = ConsentRequestParameters.Builder().build()
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info

        info.requestConsentInfoUpdate(
            activity,
            consentParams,
            {
                privacyOptionsRequiredState.value =
                    info.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    refreshCanRequestAds()
                }
            },
            {
                refreshCanRequestAds()
            },
        )
    }

    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            refreshCanRequestAds()
        }
    }

    private fun refreshCanRequestAds() {
        canRequestAdsState.value =
            if (AdConfig.isEnabled) {
                consentInformation?.canRequestAds() ?: false
            } else {
                false
            }
    }
}
