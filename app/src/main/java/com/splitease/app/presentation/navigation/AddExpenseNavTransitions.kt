package com.splitease.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry

/** Duration for add-expense expand / fold-into-FAB transitions. */
const val ADD_EXPENSE_TRANSITION_MS = 200

/** Bottom-right — matches extended FAB placement on list/detail screens. */
private val FabTransformOrigin = TransformOrigin(1f, 1f)

/** True when the route is add-expense in create mode (not editing). */
fun isCreatingExpense(entry: NavBackStackEntry): Boolean =
    entry.arguments?.getString("expenseId").orEmpty().isBlank()

/** Screen grows out of the add-expense FAB corner. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.addExpenseEnterTransition(): EnterTransition =
    scaleIn(
        initialScale = 0f,
        transformOrigin = FabTransformOrigin,
        animationSpec = tween(ADD_EXPENSE_TRANSITION_MS, easing = FastOutSlowInEasing),
    ) +
        fadeIn(
            animationSpec = tween(ADD_EXPENSE_TRANSITION_MS / 2, easing = FastOutSlowInEasing),
        )

/** Screen folds back into the add-expense FAB corner. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.addExpensePopExitTransition(): ExitTransition =
    scaleOut(
        targetScale = 0f,
        transformOrigin = FabTransformOrigin,
        animationSpec = tween(ADD_EXPENSE_TRANSITION_MS, easing = FastOutLinearInEasing),
    ) +
        fadeOut(
            animationSpec = tween(ADD_EXPENSE_TRANSITION_MS / 2, easing = FastOutLinearInEasing),
        )
