package com.splitease.app

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.domain.settings.AppSettingsRepository
import com.splitease.app.domain.settings.AuthTimeout
import com.splitease.app.domain.settings.ThemeMode
import com.splitease.app.presentation.navigation.SplitEaseNavHost
import com.splitease.app.presentation.security.AppLockGate
import com.splitease.app.presentation.theme.SplitEaseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host for all Compose destinations.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            SystemBarStyle.light(lightScrim, darkScrim)
                        },
                    navigationBarStyle =
                        if (darkTheme) {
                            SystemBarStyle.dark(darkScrim)
                        } else {
                            SystemBarStyle.light(lightScrim, darkScrim)
                        },
                )
            }

            SplitEaseTheme(darkTheme = darkTheme, dynamicColor = false) {
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
}
