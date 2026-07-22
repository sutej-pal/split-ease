package com.splitease.app.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.presentation.account.AccountScreen
import com.splitease.app.presentation.activity.ActivityScreen
import com.splitease.app.presentation.auth.AuthViewModel
import com.splitease.app.presentation.auth.ForgotPasswordScreen
import com.splitease.app.presentation.auth.LoginScreen
import com.splitease.app.presentation.auth.SignUpScreen
import com.splitease.app.presentation.expenses.AddExpenseScreen
import com.splitease.app.presentation.expenses.FriendDetailScreen
import com.splitease.app.presentation.friends.AddFriendScreen
import com.splitease.app.presentation.friends.FriendsListScreen
import com.splitease.app.presentation.groups.CreateGroupScreen
import com.splitease.app.presentation.groups.GroupDetailScreen
import com.splitease.app.presentation.home.GroupsHomeScreen
import com.splitease.app.presentation.settings.SettingsScreen
import com.splitease.app.presentation.welcome.WelcomeScreen

/** Navigation route constants. */
object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val FORGOT_PASSWORD = "forgot_password"

    const val TAB_GROUPS = "tab_groups"
    const val TAB_FRIENDS = "tab_friends"
    const val TAB_ACTIVITY = "tab_activity"
    const val TAB_ACCOUNT = "tab_account"

    const val SETTINGS = "settings"
    const val ADD_FRIEND = "add_friend"
    const val FRIEND_DETAIL = "friend_detail/{friendUserId}"
    const val CREATE_GROUP = "create_group"
    const val GROUP_DETAIL = "group_detail/{groupId}"
    const val ADD_EXPENSE = "add_expense?groupId={groupId}&friendUserId={friendUserId}"

    fun groupDetail(groupId: String) = "group_detail/$groupId"

    fun friendDetail(friendUserId: String) = "friend_detail/$friendUserId"

    fun addExpenseForGroup(groupId: String) =
        "add_expense?groupId=$groupId&friendUserId="

    fun addExpenseForFriend(friendUserId: String) =
        "add_expense?groupId=&friendUserId=$friendUserId"
}

private val tabRoutes =
    setOf(
        Routes.TAB_GROUPS,
        Routes.TAB_FRIENDS,
        Routes.TAB_ACTIVITY,
        Routes.TAB_ACCOUNT,
    )

/**
 * Root navigation graph gated by [AuthSession].
 */
@Composable
fun SplitEaseNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val session by authViewModel.session.collectAsStateWithLifecycle()
    val formState by authViewModel.formState.collectAsStateWithLifecycle()
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
            val navController = rememberNavController()
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
            SignedInNavHost(
                displayName = current.user.displayName,
                onSignOut = authViewModel::signOut,
            )
        }
    }
}

@Composable
private fun SignedInNavHost(
    displayName: String,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                SplitEaseBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TAB_GROUPS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TAB_GROUPS) {
                GroupsHomeScreen(
                    onOpenGroup = { id -> navController.navigate(Routes.groupDetail(id)) },
                    onAddFriend = { navController.navigate(Routes.ADD_FRIEND) },
                    onCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                    onAddExpenseForGroup = { id ->
                        navController.navigate(Routes.addExpenseForGroup(id))
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.TAB_FRIENDS) {
                FriendsListScreen(
                    onAddFriend = { navController.navigate(Routes.ADD_FRIEND) },
                    onOpenFriend = { friendUserId ->
                        navController.navigate(Routes.friendDetail(friendUserId))
                    },
                )
            }
            composable(Routes.TAB_ACTIVITY) {
                ActivityScreen()
            }
            composable(Routes.TAB_ACCOUNT) {
                AccountScreen(
                    displayName = displayName,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onSignOut = onSignOut,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ADD_FRIEND) {
                AddFriendScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.FRIEND_DETAIL,
                arguments = listOf(navArgument("friendUserId") { type = NavType.StringType }),
            ) { entry ->
                val friendUserId = entry.arguments?.getString("friendUserId").orEmpty()
                FriendDetailScreen(
                    friendUserId = friendUserId,
                    onBack = { navController.popBackStack() },
                    onAddExpense = {
                        navController.navigate(Routes.addExpenseForFriend(friendUserId))
                    },
                )
            }
            composable(Routes.CREATE_GROUP) {
                CreateGroupScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.navigate(Routes.groupDetail(id)) {
                            popUpTo(Routes.TAB_GROUPS)
                        }
                    },
                )
            }
            composable(
                route = Routes.GROUP_DETAIL,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty()
                GroupDetailScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onAddExpense = {
                        navController.navigate(Routes.addExpenseForGroup(groupId))
                    },
                )
            }
            composable(
                route = Routes.ADD_EXPENSE,
                arguments =
                    listOf(
                        navArgument("groupId") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = false
                        },
                        navArgument("friendUserId") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = false
                        },
                    ),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty().ifBlank { null }
                val friendUserId =
                    entry.arguments?.getString("friendUserId").orEmpty().ifBlank { null }
                AddExpenseScreen(
                    groupId = groupId,
                    friendUserId = friendUserId,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}
