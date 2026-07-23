package com.splitease.app.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

enum class ActivityKind {
    EXPENSE,
    PAYMENT,
    GROUP_CREATED,
}

data class ActivityUiItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val sortEpochMs: Long,
)

@HiltViewModel
class ActivityViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val groupRepository: GroupRepository,
        private val userRepository: UserRepository,
        private val friendRepository: FriendRepository,
        private val syncInteractor: SyncInteractor,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val items: StateFlow<List<ActivityUiItem>> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            expenseRepository.observeInvolvingUser(me),
                            paymentRepository.observeInvolvingUser(me),
                            groupRepository.observeGroupsForUser(me),
                            userRepository.observeUsers(),
                            friendRepository.observeFriends(me),
                        ) { expenses, payments, groups, users, friends ->
                            val groupNames = groups.associate { it.id to it.name }
                            val userNames = users.associate { it.id to it.displayName }
                            val friendNames =
                                friends.associate { it.friendUserId to it.displayNameSnapshot }

                            fun nameOf(id: String): String =
                                when (id) {
                                    me -> "You"
                                    else ->
                                        friendNames[id]
                                            ?: userNames[id]
                                            ?: id.take(8)
                                }

                            val expenseItems =
                                expenses.map { expense ->
                                    expense.toUi(me, groupNames, ::nameOf)
                                }
                            val paymentItems =
                                payments.map { payment ->
                                    payment.toUi(me, groupNames, ::nameOf)
                                }
                            val groupCreatedItems =
                                groups
                                    .filter { it.createdByUserId == me }
                                    .map { it.toCreatedUi() }
                            (expenseItems + paymentItems + groupCreatedItems)
                                .sortedByDescending { it.sortEpochMs }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        init {
            viewModelScope.launch {
                userId.collect { me ->
                    if (me != null) {
                        runCatching { syncInteractor.syncForUser(me) }
                    }
                }
            }
        }

        private fun Group.toCreatedUi(): ActivityUiItem {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(createdAtEpochMs))
            return ActivityUiItem(
                id = "group-created-$id",
                kind = ActivityKind.GROUP_CREATED,
                title = "You created \"$name\"",
                subtitle = date,
                amountLabel = "",
                sortEpochMs = createdAtEpochMs,
            )
        }

        private fun Expense.toUi(
            me: String,
            groupNames: Map<String, String>,
            nameOf: (String) -> String,
        ): ActivityUiItem {
            val context =
                groupId?.let { groupNames[it] } ?: "Non-group"
            val payer =
                if (paidByUserId == me) {
                    "you paid"
                } else {
                    "${nameOf(paidByUserId)} paid"
                }
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expenseDateEpochMs))
            return ActivityUiItem(
                id = "expense-$id",
                kind = ActivityKind.EXPENSE,
                title = description,
                subtitle = "$context · $payer · $date",
                amountLabel = "$currencyCode ${amount.toPlainString()}",
                sortEpochMs = expenseDateEpochMs.coerceAtLeast(createdAtEpochMs),
            )
        }

        private fun Payment.toUi(
            me: String,
            groupNames: Map<String, String>,
            nameOf: (String) -> String,
        ): ActivityUiItem {
            val title =
                when {
                    fromUserId == me -> "You paid ${nameOf(toUserId)}"
                    toUserId == me -> "${nameOf(fromUserId)} paid you"
                    else -> "${nameOf(fromUserId)} paid ${nameOf(toUserId)}"
                }
            val context = groupId?.let { groupNames[it] }
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(paidAtEpochMs))
            val parts =
                listOfNotNull(
                    context,
                    note?.takeIf { it.isNotBlank() },
                    date,
                )
            return ActivityUiItem(
                id = "payment-$id",
                kind = ActivityKind.PAYMENT,
                title = title,
                subtitle = parts.joinToString(" · "),
                amountLabel = "$currencyCode ${amount.toPlainString()}",
                sortEpochMs = paidAtEpochMs.coerceAtLeast(createdAtEpochMs),
            )
        }
    }
