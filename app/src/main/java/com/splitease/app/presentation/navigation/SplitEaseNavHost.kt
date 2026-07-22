package com.splitease.app.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.presentation.auth.AuthViewModel
import com.splitease.app.presentation.auth.ForgotPasswordScreen
import com.splitease.app.presentation.auth.LoginScreen
import com.splitease.app.presentation.auth.SignUpScreen
import com.splitease.app.presentation.home.HomeScreen
import com.splitease.app.presentation.welcome.WelcomeScreen

/** Navigation route constants. */
object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val FORGOT_PASSWORD = "forgot_password"
    const val HOME = "home"
}

/**
 * Root navigation graph gated by [AuthSession].
 */
@Composable
fun SplitEaseNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val session by authViewModel.session.collectAsStateWithLifecycle()
    val formState by authViewModel.formState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current
    val googleSoon = stringResource(R.string.google_sign_in_soon)
    val resetSent = stringResource(R.string.reset_sent)

    when (val current = session) {
        AuthSession.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthSession.SignedOut -> {
            NavHost(
                navController = navController,
                startDestination = Routes.WELCOME,
            ) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        onGetStarted = { navController.navigate(Routes.SIGN_UP) },
                        onLogIn = { navController.navigate(Routes.LOGIN) },
                    )
                }
                composable(Routes.LOGIN) {
                    LoginScreen(
                        formState = formState,
                        onSignIn = authViewModel::signIn,
                        onNavigateSignUp = {
                            authViewModel.clearMessages()
                            navController.navigate(Routes.SIGN_UP)
                        },
                        onNavigateForgot = {
                            authViewModel.clearMessages()
                            navController.navigate(Routes.FORGOT_PASSWORD)
                        },
                        onGoogleStub = {
                            Toast.makeText(context, googleSoon, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                composable(Routes.SIGN_UP) {
                    SignUpScreen(
                        formState = formState,
                        onSignUp = authViewModel::signUp,
                        onNavigateLogin = {
                            authViewModel.clearMessages()
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(Routes.WELCOME)
                            }
                        },
                    )
                }
                composable(Routes.FORGOT_PASSWORD) {
                    ForgotPasswordScreen(
                        formState = formState,
                        onSendReset = { email ->
                            authViewModel.sendPasswordReset(email, resetSent)
                        },
                        onNavigateBack = {
                            authViewModel.clearMessages()
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
        is AuthSession.SignedIn -> {
            LaunchedEffect(current.user.userId) {
                authViewModel.clearMessages()
            }
            HomeScreen(
                displayName = current.user.displayName,
                onSignOut = authViewModel::signOut,
            )
        }
    }
}
