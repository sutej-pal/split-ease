package com.splitease.app.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
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
import javax.inject.Inject

data class ExpensePickerFriend(
    val friend: Friend,
    val photoUrl: String?,
    val email: String? = null,
    val phoneCountryCode: String? = null,
    val phoneNumber: String? = null,
)

data class AddExpensePickerUi(
    val groups: List<Group> = emptyList(),
    val friends: List<ExpensePickerFriend> = emptyList(),
)

@HiltViewModel
class AddExpensePickerViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        groupRepository: GroupRepository,
        friendRepository: FriendRepository,
        userRepository: UserRepository,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val ui: StateFlow<AddExpensePickerUi> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(AddExpensePickerUi())
                    } else {
                        combine(
                            groupRepository.observeGroupsForUser(me),
                            friendRepository.observeFriends(me),
                            userRepository.observeUsers().map { users ->
                                users.associateBy { it.id }
                            },
                        ) { groups, friends, usersById ->
                            AddExpensePickerUi(
                                groups = groups,
                                friends =
                                    friends.map { friend ->
                                        val user = usersById[friend.friendUserId]
                                        ExpensePickerFriend(
                                            friend = friend,
                                            photoUrl = user?.photoUrl,
                                            email = user?.email,
                                            phoneCountryCode = user?.phoneCountryCode,
                                            phoneNumber = user?.phoneNumber,
                                        )
                                    },
                            )
                        }
                    }
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    AddExpensePickerUi(),
                )
    }
