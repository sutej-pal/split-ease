package com.splitease.app.presentation.security

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.splitease.app.R
import com.splitease.app.domain.settings.AuthTimeout
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SePrimaryButton

/**
 * Wraps [content] and requires biometric / device-credential unlock when
 * [enabled] and the background idle time exceeds [timeout].
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    timeout: AuthTimeout,
    content: @Composable () -> Unit,
) {
    var locked by remember { mutableStateOf(false) }
    var lastStoppedAt by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, enabled, timeout) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        lastStoppedAt = SystemClock.elapsedRealtime()
                    }
                    Lifecycle.Event.ON_START -> {
                        if (!enabled) {
                            locked = false
                            return@LifecycleEventObserver
                        }
                        val coldStart = lastStoppedAt == 0L
                        val elapsed = SystemClock.elapsedRealtime() - lastStoppedAt
                        if (coldStart || elapsed >= timeout.millis) {
                            locked = true
                        }
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (enabled && locked) {
            AppLockOverlay(onUnlocked = { locked = false })
        }
    }
}

@Composable
private fun AppLockOverlay(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var promptError by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.security_unlock_title)
    val subtitle = stringResource(R.string.security_unlock_subtitle)
    val unavailable = stringResource(R.string.security_biometrics_unavailable)

    fun showPrompt() {
        if (activity == null) {
            promptError = unavailable
            return
        }
        authenticateWithBiometrics(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = {
                promptError = null
                onUnlocked()
            },
            onError = { message -> promptError = message },
            onUnavailable = { promptError = unavailable },
        )
    }

    LaunchedEffect(Unit) {
        showPrompt()
    }

    BackHandler(enabled = true) {
        activity?.moveTaskToBack(true)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(SplitEaseColors.Background)
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = SplitEaseColors.Primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Navy,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.NavyMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        if (promptError != null) {
            Text(
                text = promptError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        SePrimaryButton(
            text = stringResource(R.string.security_unlock_action),
            onClick = { showPrompt() },
        )
    }
}

/**
 * Result of checking whether the device can authenticate with biometrics
 * or device credential.
 */
sealed interface BiometricAvailability {
    data object Ready : BiometricAvailability

    data class Unavailable(
        val message: String
    ) : BiometricAvailability
}

/**
 * Checks whether biometric / device-credential auth can be used.
 */
fun biometricAvailability(
    activity: FragmentActivity,
    unavailableMessage: String,
): BiometricAvailability {
    val manager = BiometricManager.from(activity)
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return when (manager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Ready
        else -> BiometricAvailability.Unavailable(unavailableMessage)
    }
}

/**
 * Shows the system biometric / device-credential prompt.
 */
fun authenticateWithBiometrics(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onUnavailable: () -> Unit,
) {
    when (biometricAvailability(activity, "")) {
        is BiometricAvailability.Unavailable -> {
            onUnavailable()
            return
        }
        BiometricAvailability.Ready -> Unit
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt =
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // Keep overlay; user can retry.
                }
            },
        )

    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val promptInfo =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

    prompt.authenticate(promptInfo)
}
