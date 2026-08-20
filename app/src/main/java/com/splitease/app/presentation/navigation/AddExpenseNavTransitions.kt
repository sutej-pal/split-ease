package com.splitease.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry

/** Duration for add-expense expand / fold-into-FAB transitions. */
const val ADD_EXPENSE_TRANSITION_MS = 220

/**
 * Pivot near the extended FAB (end + 16.dp, above the nav-host bottom).
 * (1f, 1f) is the screen corner, which sits below-right of the actual button.
 */
private val FabTransformOrigin = TransformOrigin(0.86f, 0.93f)

/** Scale of the screen when it matches the FAB — not 0, or it vanishes into the corner. */
private const val FAB_SCALE = 0.14f

/**
 * True when [entry] is the add-expense form in create mode (not editing).
 * Uses the `expenseId` nav arg instead of [NavBackStackEntry.destination.route]
 * because resolved routes omit the query-parameter template from [Routes.ADD_EXPENSE].
 */
fun isCreatingExpense(entry: NavBackStackEntry): Boolean =
    entry.arguments?.containsKey("expenseId") == true &&
        entry.arguments?.getString("expenseId").orEmpty().isBlank()

fun NavBackStackEntry.isAddExpensePicker(): Boolean =
    destination.route == Routes.ADD_EXPENSE_PICKER

/** Expand from FAB when opening create from a list/detail that has the button. */
fun shouldExpandAddExpenseFromFab(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): Boolean = isCreatingExpense(to) && !from.isAddExpensePicker()

/** Fold into FAB when backing to that list/detail, not when returning to the picker. */
fun shouldFoldAddExpenseIntoFab(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): Boolean = isCreatingExpense(from) && !to.isAddExpensePicker()

/** Screen grows out of the add-expense FAB. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.addExpenseEnterTransition(): EnterTransition =
    scaleIn(
        initialScale = FAB_SCALE,
        transformOrigin = FabTransformOrigin,
        animationSpec = tween(ADD_EXPENSE_TRANSITION_MS, easing = FastOutSlowInEasing),
    ) +
        fadeIn(
            animationSpec = tween(ADD_EXPENSE_TRANSITION_MS, easing = FastOutSlowInEasing),
        )

/** Screen folds back into the add-expense FAB. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.addExpensePopExitTransition(): ExitTransition =
    scaleOut(
        targetScale = FAB_SCALE,
        transformOrigin = FabTransformOrigin,
        animationSpec = tween(ADD_EXPENSE_TRANSITION_MS, easing = FastOutSlowInEasing),
    ) +
        fadeOut(
            animationSpec =
                tween(
                    durationMillis = ADD_EXPENSE_TRANSITION_MS / 2,
                    delayMillis = ADD_EXPENSE_TRANSITION_MS / 2,
                    easing = FastOutSlowInEasing,
                ),
        )
