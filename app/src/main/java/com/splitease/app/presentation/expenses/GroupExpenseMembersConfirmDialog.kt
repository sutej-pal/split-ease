package com.splitease.app.presentation.expenses

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SePreview

/**
 * Confirms group membership before the add-expense form for a group.
 */
@Composable
fun GroupExpenseMembersConfirmDialog(
    memberCount: Int,
    onStartAddingExpenses: () -> Unit,
    onEditGroupMembers: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = memberCount.coerceAtLeast(0)
    val title =
        pluralStringResource(
            R.plurals.expense_split_with_people,
            count,
            count,
        )

    SeConfirmDialog(
        title = title,
        body = stringResource(R.string.expense_members_confirm_body),
        confirmLabel = stringResource(R.string.action_start_adding_expenses),
        onDismissRequest = onDismiss,
        onConfirm = onStartAddingExpenses,
        dismissLabel = stringResource(R.string.action_edit_group_members),
        onDismissClick = onEditGroupMembers,
        icon = Icons.Filled.Group,
        tone = SeConfirmTone.Primary,
    )
}

@Preview(name = "Group expense members confirm")
@Composable
private fun GroupExpenseMembersConfirmDialogPreview() {
    SePreview {
        GroupExpenseMembersConfirmDialog(
            memberCount = 3,
            onStartAddingExpenses = {},
            onEditGroupMembers = {},
            onDismiss = {},
        )
    }
}
