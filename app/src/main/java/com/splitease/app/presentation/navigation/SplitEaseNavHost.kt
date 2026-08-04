package com.splitease.app.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.settings.AppSettingsRepository
import com.splitease.app.presentation.onboarding.OnboardingViewModel
import kotlinx.coroutines.delay
import com.splitease.app.presentation.account.AccountProfileSettingsScreen
import com.splitease.app.presentation.account.AccountScreen
import com.splitease.app.presentation.activity.ActivityScreen
import com.splitease.app.presentation.auth.AuthViewModel
import com.splitease.app.presentation.auth.ForgotPasswordScreen
import com.splitease.app.presentation.auth.LoginScreen
import com.splitease.app.presentation.auth.PendingOtpPurpose
import com.splitease.app.presentation.auth.ResetPasswordOtpScreen
import com.splitease.app.presentation.auth.SignUpScreen
import com.splitease.app.presentation.auth.VerifyEmailScreen
import com.splitease.app.presentation.invite.InviteJoinSignUpScreen
import com.splitease.app.presentation.pinboard.PinBoardScreen
import com.splitease.app.presentation.invite.InviteLandingScreen
import com.splitease.app.presentation.expenses.AddExpenseScreen
import com.splitease.app.presentation.expenses.ExpenseDetailScreen
import com.splitease.app.presentation.expenses.FriendDetailScreen
import com.splitease.app.presentation.friends.EditContactScreen
import com.splitease.app.presentation.friends.FindPeopleScreen
import com.splitease.app.presentation.friends.FriendSettingsScreen
import com.splitease.app.presentation.friends.FriendsListScreen
import com.splitease.app.presentation.friends.ReviewFriendsScreen
import com.splitease.app.presentation.groups.CreateGroupScreen
import com.splitease.app.presentation.groups.GroupDetailScreen
import com.splitease.app.presentation.groups.NonGroupExpensesScreen
import com.splitease.app.presentation.groups.GroupInviteLinkScreen
import com.splitease.app.presentation.groups.GroupSettingsScreen
import com.splitease.app.presentation.home.GroupsHomeScreen
import com.splitease.app.presentation.imports.ImportTransactionsScreen
import com.splitease.app.presentation.search.SearchScreen
import com.splitease.app.presentation.settings.AppearanceSettingsScreen
import com.splitease.app.presentation.settings.CurrencySettingsScreen
import com.splitease.app.presentation.settings.LanguageSettingsScreen
import com.splitease.app.presentation.settings.SecuritySettingsScreen
import com.splitease.app.presentation.settings.SettingsScreen
import com.splitease.app.presentation.settlements.SettleUpScreen
import com.splitease.app.presentation.spending.SpendingTotalsScreen
import com.splitease.app.presentation.welcome.WelcomeScreen

/** Navigation route constants. */
object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val FORGOT_PASSWORD = "forgot_password"
    const val INVITE_LANDING = "invite_landing/{token}"
    const val INVITE_JOIN_SIGN_UP = "invite_join_sign_up"

    const val TAB_GROUPS = "tab_groups"
    const val TAB_FRIENDS = "tab_friends"
    const val TAB_ACTIVITY = "tab_activity"
    const val TAB_ACCOUNT = "tab_account"

    const val SETTINGS = "settings"
    const val ACCOUNT_PROFILE_SETTINGS = "account_profile_settings"
    const val APPEARANCE_SETTINGS = "appearance_settings"
    const val SECURITY_SETTINGS = "security_settings"
    const val LANGUAGE_SETTINGS = "language_settings"
    const val CURRENCY_SETTINGS = "currency_settings"
    const val SEARCH = "search"
    const val SPENDING = "spending"
    const val IMPORT = "import_transactions"
    const val ADD_FRIEND =
        "add_friend?groupId={groupId}&name={name}&contact={contact}"
    const val EDIT_CONTACT =
        "edit_contact?groupId={groupId}&friendUserId={friendUserId}&contactId={contactId}" +
            "&entryId={entryId}&confirmOnly={confirmOnly}&name={name}&contact={contact}"
    const val FIND_PEOPLE = "find_people?groupId={groupId}"
    const val REVIEW_FRIENDS = "review_friends?groupId={groupId}"
    const val FRIEND_DETAIL = "friend_detail/{friendUserId}"
    const val FRIEND_SETTINGS = "friend_settings/{friendUserId}"
    const val CREATE_GROUP = "create_group"
    const val GROUP_DETAIL = "group_detail/{groupId}"
    const val NON_GROUP_EXPENSES = "non_group_expenses"
    const val GROUP_SETTINGS = "group_settings/{groupId}"
    const val GROUP_INVITE_LINK = "group_invite_link/{groupId}"
    const val ADD_EXPENSE =
        "add_expense?groupId={groupId}&friendUserId={friendUserId}&expenseId={expenseId}"
    const val EXPENSE_DETAIL = "expense_detail/{expenseId}"
    const val PIN_BOARD = "pin_board/{groupId}"
    const val SETTLE_UP =
        "settle_up?fromUserId={fromUserId}&toUserId={toUserId}&amount={amount}&currency={currency}&groupId={groupId}&label={label}"

    fun groupDetail(groupId: String) = "group_detail/$groupId"

    fun groupSettings(groupId: String) = "group_settings/$groupId"

    fun groupInviteLink(groupId: String) = "group_invite_link/$groupId"

    fun pinBoard(groupId: String) = "pin_board/$groupId"

    fun friendDetail(friendUserId: String) = "friend_detail/$friendUserId"

    fun friendSettings(friendUserId: String) = "friend_settings/$friendUserId"

    fun expenseDetail(expenseId: String) = "expense_detail/$expenseId"

    fun findPeople(groupId: String? = null) =
        "find_people?groupId=${groupId.orEmpty()}"

    fun reviewFriends(groupId: String? = null) =
        "review_friends?groupId=${groupId.orEmpty()}"

    fun addFriend(
        groupId: String? = null,
        name: String = "",
        contact: String = "",
    ): String {
        val n = android.net.Uri.encode(name)
        val c = android.net.Uri.encode(contact)
        return "add_friend?groupId=${groupId.orEmpty()}&name=$n&contact=$c"
    }

    fun editContact(
        groupId: String? = null,
        friendUserId: String? = null,
        contactId: String? = null,
        entryId: String? = null,
        confirmOnly: Boolean = false,
        name: String = "",
        contact: String = "",
    ): String {
        val n = android.net.Uri.encode(name)
        val c = android.net.Uri.encode(contact)
        return "edit_contact?groupId=${groupId.orEmpty()}" +
            "&friendUserId=${friendUserId.orEmpty()}" +
            "&contactId=${contactId.orEmpty()}" +
            "&entryId=${entryId.orEmpty()}" +
            "&confirmOnly=$confirmOnly" +
            "&name=$n&contact=$c"
    }

    fun addExpenseForGroup(groupId: String) =
        "add_expense?groupId=$groupId&friendUserId=&expenseId="

    fun addExpenseForFriend(friendUserId: String) =
        "add_expense?groupId=&friendUserId=$friendUserId&expenseId="

    fun editExpense(
        expenseId: String,
        groupId: String? = null,
        friendUserId: String? = null,
    ) = "add_expense?groupId=${groupId.orEmpty()}&friendUserId=${friendUserId.orEmpty()}&expenseId=$expenseId"

    fun settleUp(
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        groupId: String? = null,
        label: String,
    ): String {
        val encodedLabel = android.net.Uri.encode(label)
        return "settle_up?fromUserId=$fromUserId&toUserId=$toUserId&amount=$amount&currency=$currency&groupId=${groupId.orEmpty()}&label=$encodedLabel"
    }

    fun inviteLanding(token: String) = "invite_landing/${android.net.Uri.encode(token)}"
}

private val tabRoutes =
    setOf(
        Routes.TAB_GROUPS,
        Routes.TAB_FRIENDS,
        Routes.TAB_ACTIVITY,
        Routes.TAB_ACCOUNT,
    )

/** Destinations that keep the main bottom navigation visible. */
private val bottomBarRoutes =
    tabRoutes +
        setOf(
            Routes.GROUP_DETAIL,
            Routes.NON_GROUP_EXPENSES,
            Routes.FRIEND_DETAIL,
        )

/**
 * Tab route used for bottom-bar selection highlighting.
 * Nested screens (e.g. group detail) stay under their parent tab.
 */
private fun selectedTabRoute(currentRoute: String?): String? =
    when {
        currentRoute == Routes.GROUP_DETAIL ||
            currentRoute == Routes.NON_GROUP_EXPENSES ->
            Routes.TAB_GROUPS
        currentRoute == Routes.FRIEND_DETAIL ||
            currentRoute?.startsWith("friend_detail/") == true ->
            Routes.TAB_FRIENDS
        else -> currentRoute?.takeIf { it in tabRoutes }
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
    val pendingInviteToken by authViewModel.pendingInviteToken.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val googleSoon = stringResource(R.string.google_sign_in_soon)

    val pendingEmail = formState.pendingConfirmationEmail
    // OTP gate after signup / password-reset — blocks Home until the flow completes.
    if (pendingEmail != null && session !is AuthSession.Loading) {
        when (formState.pendingOtpPurpose) {
            PendingOtpPurpose.RECOVERY ->
                ResetPasswordOtpScreen(
                    email = pendingEmail,
                    formState = formState,
                    onSubmit = { code, newPassword, confirmPassword ->
                        authViewModel.completePasswordReset(
                            email = pendingEmail,
                            token = code,
                            newPassword = newPassword,
                            confirmPassword = confirmPassword,
                        )
                    },
                    onResend = { authViewModel.resendConfirmation(pendingEmail) },
                    onBackToLogin = {
                        authViewModel.clearPendingConfirmation()
                        authViewModel.clearMessages()
                    },
                )
            else ->
                VerifyEmailScreen(
                    email = pendingEmail,
                    formState = formState,
                    onVerify = { code -> authViewModel.verifyPendingOtp(pendingEmail, code) },
                    onResend = { authViewModel.resendConfirmation(pendingEmail) },
                    onBackToLogin = {
                        authViewModel.clearPendingConfirmation()
                        authViewModel.clearMessages()
                    },
                )
        }
        return
    }
    // Password auth emits SignedIn before OTP is armed — hold Home closed.
    if (formState.holdSignedInForOtp && session is AuthSession.SignedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    when (val current = session) {
        AuthSession.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthSession.SignedOut -> {
            val navController = rememberNavController()
            val startDestination =
                if (!pendingInviteToken.isNullOrBlank()) {
                    Routes.inviteLanding(pendingInviteToken!!)
                } else {
                    Routes.WELCOME
                }
            LaunchedEffect(pendingInviteToken) {
                val token = pendingInviteToken
                if (!token.isNullOrBlank()) {
                    val route = Routes.inviteLanding(token)
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    val onInviteFlow =
                        currentRoute == Routes.INVITE_LANDING ||
                            currentRoute == Routes.INVITE_JOIN_SIGN_UP
                    if (!onInviteFlow) {
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        onGetStarted = { navController.navigate(Routes.SIGN_UP) },
                        onLogIn = { navController.navigate(Routes.LOGIN) },
                        onOpenInviteLink = authViewModel::openInviteFromPastedText,
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
                        onSignUp = { email, password, displayName, dial, phone, currency, photo ->
                            authViewModel.signUp(
                                email = email,
                                password = password,
                                displayName = displayName,
                                phoneCountryCode = dial,
                                phoneNumber = phone,
                                currencyCode = currency,
                                photoUri = photo,
                            )
                        },
                        onBack = {
                            authViewModel.clearMessages()
                            navController.popBackStack()
                        },
                    )
                }
                composable(Routes.FORGOT_PASSWORD) {
                    ForgotPasswordScreen(
                        formState = formState,
                        onSendReset = { email ->
                            authViewModel.sendPasswordReset(email)
                        },
                        onNavigateBack = {
                            authViewModel.clearMessages()
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    route = Routes.INVITE_LANDING,
                    arguments = listOf(navArgument("token") { type = NavType.StringType }),
                ) { entry ->
                    val token =
                        android.net.Uri.decode(entry.arguments?.getString("token").orEmpty())
                    InviteLandingScreen(
                        token = token,
                        onJoinAsNew = {
                            authViewModel.clearMessages()
                            navController.navigate(Routes.INVITE_JOIN_SIGN_UP)
                        },
                        onAlreadyHaveAccount = {
                            authViewModel.clearMessages()
                            navController.navigate(Routes.LOGIN)
                        },
                        onDismiss = {
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.INVITE_JOIN_SIGN_UP) {
                    InviteJoinSignUpScreen(
                        formState = formState,
                        onSignUp = { email, password, displayName ->
                            authViewModel.signUp(email, password, displayName)
                        },
                        onNavigateLogin = {
                            authViewModel.clearMessages()
                            navController.navigate(Routes.LOGIN)
                        },
                        onBack = {
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
            // Welcome email only — display name was already collected at signup.
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            LaunchedEffect(current.user.userId) {
                onboardingViewModel.onSignedInWelcome(
                    userId = current.user.userId,
                    email = current.user.email,
                    displayName = current.user.displayName,
                )
            }
            SignedInNavHost(
                userId = current.user.userId,
                onSignOut = authViewModel::signOut,
                pendingInviteToken = pendingInviteToken,
                claimInviteAndConsumeOpenTarget = authViewModel::claimInviteAndConsumeOpenTarget,
                observePendingNotificationGroupId = {
                    authViewModel.observePendingNotificationGroupId()
                },
                consumePendingNotificationGroupId = authViewModel::consumePendingNotificationGroupId,
            )
        }
    }
}

@Composable
private fun SignedInNavHost(
    userId: String,
    onSignOut: () -> Unit,
    pendingInviteToken: String?,
    claimInviteAndConsumeOpenTarget: suspend () -> String?,
    observePendingNotificationGroupId: () -> kotlinx.coroutines.flow.Flow<String?>,
    consumePendingNotificationGroupId: suspend () -> String?,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarRoutes
    val bottomBarSelectedRoute = selectedTabRoute(currentRoute)
    val pendingNotificationGroupId by
        observePendingNotificationGroupId().collectAsStateWithLifecycle(null)

    // Claim on sign-in (post-OTP open target) and again when a deep-link token arrives
    // while already signed in. Key on non-blank token only so clearing the token after
    // accept can restart the effect and still navigate if the prior run was cancelled.
    val inviteTokenKey = pendingInviteToken?.takeIf { it.isNotBlank() }
    LaunchedEffect(userId, inviteTokenKey) {
        val target =
            claimInviteOpenTargetWithRetry(
                hasPendingToken = !inviteTokenKey.isNullOrBlank(),
                claim = claimInviteAndConsumeOpenTarget,
            ) ?: return@LaunchedEffect
        navController.navigateInviteOpenTarget(target)
    }

    LaunchedEffect(userId, pendingNotificationGroupId) {
        val groupId = pendingNotificationGroupId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val consumed = consumePendingNotificationGroupId() ?: groupId
        navController.navigateInviteOpenTarget(consumed)
    }

    Scaffold(
        // Child screens own status-bar insets via their TopAppBars; only reserve bottom-bar space here.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                SplitEaseBottomBar(
                    currentRoute = bottomBarSelectedRoute,
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
                    onOpenNonGroup = { navController.navigate(Routes.NON_GROUP_EXPENSES) },
                    onCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                    onAddExpenseForGroup = { id ->
                        navController.navigate(Routes.addExpenseForGroup(id))
                    },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.TAB_FRIENDS) {
                FriendsListScreen(
                    onAddFriend = { navController.navigate(Routes.findPeople()) },
                    onOpenFriend = { friendUserId ->
                        navController.navigate(Routes.friendDetail(friendUserId))
                    },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onAddExpenseForFriend = { friendUserId ->
                        navController.navigate(Routes.addExpenseForFriend(friendUserId))
                    },
                )
            }
            composable(Routes.TAB_ACTIVITY) {
                ActivityScreen(
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
                )
            }
            composable(Routes.TAB_ACCOUNT) {
                AccountScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAccountProfile = { navController.navigate(Routes.ACCOUNT_PROFILE_SETTINGS) },
                    onOpenSpending = { navController.navigate(Routes.SPENDING) },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                    onSignOut = onSignOut,
                )
            }
            composable(Routes.ACCOUNT_PROFILE_SETTINGS) {
                AccountProfileSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCurrency = { navController.navigate(Routes.CURRENCY_SETTINGS) },
                    onOpenLanguage = { navController.navigate(Routes.LANGUAGE_SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAppearance = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                    onOpenSecurity = { navController.navigate(Routes.SECURITY_SETTINGS) },
                )
            }
            composable(Routes.APPEARANCE_SETTINGS) {
                AppearanceSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SECURITY_SETTINGS) {
                SecuritySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LANGUAGE_SETTINGS) {
                LanguageSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CURRENCY_SETTINGS) {
                CurrencySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SPENDING) {
                SpendingTotalsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.IMPORT) {
                ImportTransactionsScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.FIND_PEOPLE,
                arguments =
                    listOf(
                        navArgument("groupId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty().ifBlank { null }
                FindPeopleScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onManualAdd = {
                        navController.navigate(
                            Routes.editContact(
                                groupId = groupId,
                                confirmOnly = true,
                            ),
                        )
                    },
                    onReviewSelected = {
                        navController.navigate(Routes.reviewFriends(groupId))
                    },
                )
            }
            composable(
                route = Routes.REVIEW_FRIENDS,
                arguments =
                    listOf(
                        navArgument("groupId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty().ifBlank { null }
                ReviewFriendsScreen(
                    onBack = { navController.popBackStack() },
                    onEditEntry = { reviewEntry ->
                        navController.navigate(
                            Routes.editContact(
                                groupId = groupId,
                                contactId = reviewEntry.contactId,
                                entryId = reviewEntry.id,
                                confirmOnly = true,
                                name = reviewEntry.displayName,
                                contact = reviewEntry.contactValue,
                            ),
                        )
                    },
                    onDone = {
                        navController.popBackStack(Routes.findPeople(groupId), inclusive = true)
                    },
                )
            }
            composable(
                route = Routes.EDIT_CONTACT,
                arguments =
                    listOf(
                        navArgument("groupId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("friendUserId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("contactId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("entryId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("confirmOnly") {
                            type = NavType.StringType
                            defaultValue = "false"
                        },
                        navArgument("name") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("contact") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty().ifBlank { null }
                val confirmOnly =
                    entry.arguments?.getString("confirmOnly").orEmpty() == "true"
                EditContactScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                    onConfirmedForReview = {
                        val cameFromReview =
                            navController.previousBackStackEntry
                                ?.destination
                                ?.route
                                ?.startsWith("review_friends") == true
                        navController.popBackStack()
                        if (confirmOnly && !cameFromReview) {
                            navController.navigate(Routes.reviewFriends(groupId))
                        }
                    },
                )
            }
            composable(
                route = Routes.ADD_FRIEND,
                arguments =
                    listOf(
                        navArgument("groupId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("name") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("contact") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty().ifBlank { null }
                val name = entry.arguments?.getString("name").orEmpty()
                val contact = entry.arguments?.getString("contact").orEmpty()
                // Legacy route — redirect into Edit contact.
                LaunchedEffect(groupId, name, contact) {
                    navController.navigate(
                        Routes.editContact(groupId = groupId, name = name, contact = contact),
                    ) {
                        popUpTo(Routes.ADD_FRIEND) { inclusive = true }
                    }
                }
            }
            composable(
                route = Routes.FRIEND_DETAIL,
                arguments = listOf(navArgument("friendUserId") { type = NavType.StringType }),
            ) { entry ->
                val friendUserId = entry.arguments?.getString("friendUserId").orEmpty()
                FriendDetailScreen(
                    friendUserId = friendUserId,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = {
                        navController.navigate(Routes.friendSettings(friendUserId))
                    },
                    onAddExpense = {
                        navController.navigate(Routes.addExpenseForFriend(friendUserId))
                    },
                    onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
                    onSettleUp = { from, to, amount, currency, label ->
                        navController.navigate(
                            Routes.settleUp(
                                fromUserId = from,
                                toUserId = to,
                                amount = amount,
                                currency = currency,
                                label = label,
                            ),
                        )
                    },
                )
            }
            composable(
                route = Routes.FRIEND_SETTINGS,
                arguments = listOf(navArgument("friendUserId") { type = NavType.StringType }),
            ) { entry ->
                val friendUserId = entry.arguments?.getString("friendUserId").orEmpty()
                FriendSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onRemoved = {
                        navController.popBackStack(Routes.TAB_FRIENDS, inclusive = false)
                    },
                    onEditContact = {
                        navController.navigate(
                            Routes.editContact(friendUserId = friendUserId),
                        )
                    },
                    onOpenGroup = { groupId ->
                        navController.navigate(Routes.groupDetail(groupId))
                    },
                )
            }
            composable(Routes.CREATE_GROUP) {
                CreateGroupScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.navigate(Routes.groupDetail(id)) {
                            popUpTo(Routes.TAB_GROUPS) { inclusive = false }
                            launchSingleTop = true
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
                    onOpenSettings = { navController.navigate(Routes.groupSettings(groupId)) },
                    onAddExpense = {
                        navController.navigate(Routes.addExpenseForGroup(groupId))
                    },
                    onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
                    onOpenSpending = { navController.navigate(Routes.SPENDING) },
                    onOpenPinBoard = {
                        navController.navigate(Routes.pinBoard(groupId))
                    },
                    onSettleDebt = { from, to, amount, currency, label ->
                        navController.navigate(
                            Routes.settleUp(
                                fromUserId = from,
                                toUserId = to,
                                amount = amount,
                                currency = currency,
                                groupId = groupId,
                                label = label,
                            ),
                        )
                    },
                )
            }
            composable(Routes.NON_GROUP_EXPENSES) {
                NonGroupExpensesScreen(
                    onBack = { navController.popBackStack() },
                    onAddExpenseForFriend = { friendUserId ->
                        navController.navigate(Routes.addExpenseForFriend(friendUserId))
                    },
                    onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
                    onOpenSpending = { navController.navigate(Routes.SPENDING) },
                    onSettleDebt = { from, to, amount, currency, label ->
                        navController.navigate(
                            Routes.settleUp(
                                fromUserId = from,
                                toUserId = to,
                                amount = amount,
                                currency = currency,
                                label = label,
                            ),
                        )
                    },
                    onAddFriend = { navController.navigate(Routes.findPeople()) },
                )
            }
            composable(
                route = Routes.GROUP_SETTINGS,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty()
                GroupSettingsScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onLeftOrDeleted = {
                        navController.popBackStack(Routes.TAB_GROUPS, inclusive = false)
                    },
                    onAddPeople = { navController.navigate(Routes.findPeople(groupId)) },
                    onInviteViaLink = { navController.navigate(Routes.groupInviteLink(groupId)) },
                )
            }
            composable(
                route = Routes.GROUP_INVITE_LINK,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) {
                GroupInviteLinkScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PIN_BOARD,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty()
                PinBoardScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
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
                        navArgument("expenseId") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = false
                        },
                    ),
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId").orEmpty().ifBlank { null }
                val friendUserId =
                    entry.arguments?.getString("friendUserId").orEmpty().ifBlank { null }
                val expenseId =
                    entry.arguments?.getString("expenseId").orEmpty().ifBlank { null }
                AddExpenseScreen(
                    groupId = groupId,
                    friendUserId = friendUserId,
                    expenseId = expenseId,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                    onEditGroupMembers =
                        groupId?.let { id ->
                            {
                                navController.navigate(Routes.groupSettings(id))
                            }
                        },
                )
            }
            composable(
                route = Routes.EXPENSE_DETAIL,
                arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
            ) { entry ->
                val expenseId = entry.arguments?.getString("expenseId").orEmpty()
                ExpenseDetailScreen(
                    expenseId = expenseId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id ->
                        navController.navigate(Routes.editExpense(id))
                    },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.SETTLE_UP,
                arguments =
                    listOf(
                        navArgument("fromUserId") { type = NavType.StringType },
                        navArgument("toUserId") { type = NavType.StringType },
                        navArgument("amount") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("currency") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("groupId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("label") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { entry ->
                SettleUpScreen(
                    fromUserId = entry.arguments?.getString("fromUserId").orEmpty(),
                    toUserId = entry.arguments?.getString("toUserId").orEmpty(),
                    counterpartyLabel =
                        android.net.Uri.decode(entry.arguments?.getString("label").orEmpty())
                            .ifBlank { "friend" },
                    amountPrefill = entry.arguments?.getString("amount").orEmpty(),
                    currencyCode = entry.arguments?.getString("currency").orEmpty(),
                    groupId = entry.arguments?.getString("groupId").orEmpty().ifBlank { null },
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Retries invite claim while a deep-link token may still be settling after cold start.
 *
 * @param hasPendingToken When false, stops after the first unsuccessful claim.
 * @param claim Accepts invite and returns navigation target when ready.
 */
private suspend fun claimInviteOpenTargetWithRetry(
    hasPendingToken: Boolean,
    claim: suspend () -> String?,
): String? {
    repeat(4) { attempt ->
        val target = claim()
        if (target != null) return target
        if (!hasPendingToken) return null
        delay(1_200L * (attempt + 1))
    }
    return null
}

/** Navigates to friends or a group detail from a claimed invite open target. */
private fun NavHostController.navigateInviteOpenTarget(target: String) {
    when {
        target == AppSettingsRepository.PENDING_INVITE_OPEN_FRIENDS -> {
            navigate(Routes.TAB_FRIENDS) {
                popUpTo(Routes.TAB_GROUPS) { inclusive = false }
                launchSingleTop = true
            }
        }
        target.isNotBlank() -> {
            navigate(Routes.groupDetail(target)) {
                launchSingleTop = true
            }
        }
    }
}
