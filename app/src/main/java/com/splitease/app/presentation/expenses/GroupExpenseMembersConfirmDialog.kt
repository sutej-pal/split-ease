package com.splitease.app.presentation.expenses

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalBody
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTextButton

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

    SeModal(onDismissRequest = onDismiss) {
        SeModalTitle(title)
        Spacer(modifier = Modifier.height(14.dp))
        SeModalBody(stringResource(R.string.expense_members_confirm_body))
        Spacer(modifier = Modifier.height(28.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_start_adding_expenses),
            onClick = onStartAddingExpenses,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SeTextButton(
            text = stringResource(R.string.action_edit_group_members),
            onClick = onEditGroupMembers,
        )
    }
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
