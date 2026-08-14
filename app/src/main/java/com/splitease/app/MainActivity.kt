package com.splitease.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.splitease.app.data.di.SupabaseModule
import com.splitease.app.data.push.SplitEaseMessagingService
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.settings.AppSettingsRepository
import com.splitease.app.domain.settings.AuthTimeout
import com.splitease.app.domain.settings.ThemeMode
import com.splitease.app.presentation.ads.AdConfig
import com.splitease.app.presentation.ads.AdConsentManager
import com.splitease.app.presentation.navigation.SplitEaseNavHost
import com.splitease.app.presentation.security.AppLockGate
import com.splitease.app.presentation.theme.SplitEaseTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single-activity host for all Compose destinations.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Inject
    lateinit var supabase: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by
                appSettingsRepository.observeThemeMode().collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val biometricLock by
                appSettingsRepository.observeBiometricLockEnabled().collectAsStateWithLifecycle(false)
            val authTimeout by
                appSettingsRepository.observeAuthTimeout().collectAsStateWithLifecycle(AuthTimeout.DEFAULT)
            val systemDark = isSystemInDarkTheme()
            val darkTheme =
                when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> systemDark
                }

            SideEffect {
                val lightScrim = Color.TRANSPARENT
                val darkScrim = Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle =
                        if (darkTheme) {
                            SystemBarStyle.dark(darkScrim)
                        } else {
                            SystemBarStyle.light(lightScrim, lightScrim)
                        },
                    navigationBarStyle =
                        if (darkTheme) {
                            SystemBarStyle.dark(darkScrim)
                        } else {
                            SystemBarStyle.light(lightScrim, lightScrim)
                        },
                )
            }

            SplitEaseTheme(darkTheme = darkTheme, dynamicColor = false) {
                LaunchedEffect(Unit) {
                    if (AdConfig.isEnabled) {
                        AdConsentManager.gatherConsent(this@MainActivity)
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppLockGate(
                        enabled = biometricLock,
                        timeout = authTimeout,
                    ) {
                        SplitEaseNavHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val openGroupId =
            intent
                .getStringExtra(SplitEaseMessagingService.EXTRA_OPEN_GROUP_ID)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        if (openGroupId != null) {
            lifecycleScope.launch {
                appSettingsRepository.setPendingNotificationGroupId(openGroupId)
            }
            intent.removeExtra(SplitEaseMessagingService.EXTRA_OPEN_GROUP_ID)
        }
        if (intent.data == null) return
        val inviteToken = InviteLinks.tokenFromUri(intent.data)
        if (!inviteToken.isNullOrBlank()) {
            lifecycleScope.launch {
                appSettingsRepository.setPendingInviteToken(inviteToken)
            }
            return
        }
        val host = intent.data?.host
        if (host != SupabaseModule.AUTH_DEEP_LINK_HOST) return
        lifecycleScope.launch {
            runCatching { supabase.handleDeeplinks(intent) }
        }
    }
}
