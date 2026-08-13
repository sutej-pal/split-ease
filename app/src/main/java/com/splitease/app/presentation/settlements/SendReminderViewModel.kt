package com.splitease.app.presentation.settlements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.MailRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.presentation.common.MoneyFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class SendReminderUiState(
    val body: String = "",
    val isReady: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val sent: Boolean = false,
)

@HiltViewModel
class SendReminderViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository,
        private val friendRepository: FriendRepository,
        private val mailRepository: MailRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SendReminderUiState())
        val uiState: StateFlow<SendReminderUiState> = _uiState.asStateFlow()

        private var fromUserId: String = ""
        private var toUserId: String = ""

        fun prepare(
            fromUserId: String,
            toUserId: String,
            fromLabel: String,
            toLabel: String,
            amount: String,
            currencyCode: String,
            groupName: String?,
        ) {
            this.fromUserId = fromUserId
            this.toUserId = toUserId
            viewModelScope.launch {
                val me = currentUserId()
                val youLabel = appContext.getString(R.string.you_label)
                val debtorLabel = displayName(fromLabel, youLabel)
                val creditorLabel = displayName(toLabel, youLabel)
                val iOwe =
                    (me != null && me == fromUserId) ||
                        fromLabel.equals(youLabel, ignoreCase = true)
                val theyOweMe =
                    (me != null && me == toUserId) ||
                        toLabel.equals(youLabel, ignoreCase = true)
                val money =
                    MoneyFormat.format(
                        amount.toBigDecimalOrZero(),
                        currencyCode.ifBlank { "INR" },
                    )
                val sender = resolveSenderSignOff(me)
                val group = groupName?.trim().orEmpty()
                val body =
                    when {
                        // Third party: neither debtor nor creditor.
                        !iOwe && !theyOweMe ->
                            if (group.isNotEmpty()) {
                                appContext.getString(
                                    R.string.remind_template_third_party,
                                    debtorLabel,
                                    creditorLabel,
                                    money,
                                    group,
                                    sender,
                                )
                            } else {
                                appContext.getString(
                                    R.string.remind_template_third_party_no_group,
                                    debtorLabel,
                                    creditorLabel,
                                    money,
                                    sender,
                                )
                            }
                        iOwe ->
                            if (group.isNotEmpty()) {
                                appContext.getString(
                                    R.string.remind_template_owe_them,
                                    creditorLabel,
                                    money,
                                    group,
                                    sender,
                                )
                            } else {
                                appContext.getString(
                                    R.string.remind_template_owe_them_no_group,
                                    creditorLabel,
                                    money,
                                    sender,
                                )
                            }
                        else ->
                            if (group.isNotEmpty()) {
                                appContext.getString(
                                    R.string.remind_template_they_owe,
                                    debtorLabel,
                                    money,
                                    group,
                                    sender,
                                )
                            } else {
                                appContext.getString(
                                    R.string.remind_template_they_owe_no_group,
                                    debtorLabel,
                                    money,
                                    sender,
                                )
                            }
                    }
                _uiState.update {
                    it.copy(body = body, isReady = true, errorMessage = null, sent = false)
                }
            }
        }

        /** Prefer a real name over the localized "You" label in email copy. */
        private fun displayName(
            label: String,
            youLabel: String,
        ): String {
            val trimmed = label.trim()
            return if (trimmed.equals(youLabel, ignoreCase = true)) youLabel else trimmed
        }

        fun updateBody(value: String) {
            _uiState.update { it.copy(body = value, errorMessage = null) }
        }

        fun clearError() {
            _uiState.update { it.copy(errorMessage = null) }
        }

        fun send() {
            viewModelScope.launch {
                if (_uiState.value.isSending || _uiState.value.sent) return@launch
                _uiState.update { it.copy(isSending = true, errorMessage = null) }
                val emails = resolveEmails(fromUserId, toUserId)
                if (emails.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = appContext.getString(R.string.msg_reminder_no_email),
                        )
                    }
                    return@launch
                }
                val result =
                    mailRepository.sendBalanceReminderEmail(
                        toEmails = emails,
                        subject = appContext.getString(R.string.remind_email_subject),
                        body = _uiState.value.body,
                        settleUpNote = appContext.getString(R.string.remind_settle_up_note),
                    )
                _uiState.update {
                    if (result.isSuccess) {
                        it.copy(isSending = false, sent = true, errorMessage = null)
                    } else {
                        it.copy(
                            isSending = false,
                            errorMessage =
                                result.exceptionOrNull()?.message
                                    ?: appContext.getString(R.string.msg_reminder_failed),
                        )
                    }
                }
            }
        }

        private suspend fun currentUserId(): String? {
            val session = authRepository.observeSession().first { it !is AuthSession.Loading }
            return (session as? AuthSession.SignedIn)?.user?.userId
        }

        private suspend fun resolveSenderSignOff(userId: String?): String {
            if (userId.isNullOrBlank()) return "SplitEase user"
            val user = userRepository.getUserById(userId)
            val name = user?.displayName?.trim().orEmpty()
            if (name.isNotBlank()) return name
            val email = user?.email?.trim().orEmpty()
            return email.substringBefore("@").ifBlank { "SplitEase user" }
        }

        private suspend fun resolveEmails(vararg userIds: String): List<String> =
            userIds.mapNotNull { id -> resolveEmail(id) }.distinctBy { it.lowercase() }

        private suspend fun resolveEmail(userId: String): String? {
            if (userId.isBlank()) return null
            userRepository.getUserById(userId)?.email?.trim()?.let { email ->
                if (isUsableEmail(email)) return email
            }
            friendRepository.getByFriendUserId(userId)?.emailSnapshot?.trim()?.let { email ->
                if (isUsableEmail(email)) return email
            }
            return null
        }

        private fun isUsableEmail(email: String): Boolean =
            email.contains("@") && !email.endsWith("@splitease.invalid", ignoreCase = true)

        private fun String.toBigDecimalOrZero(): BigDecimal =
            trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
