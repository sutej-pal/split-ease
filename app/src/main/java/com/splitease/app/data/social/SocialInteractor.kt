package com.splitease.app.data.social

import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.FriendDto
import com.splitease.app.data.remote.dto.GroupDto
import com.splitease.app.data.remote.dto.GroupMemberDto
import com.splitease.app.data.remote.dto.InviteDto
import com.splitease.app.domain.model.AddPersonOutcome
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.Invite
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.InviteStatus
import com.splitease.app.domain.model.MemberRole
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.InviteRepository
import com.splitease.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Friends & groups write/sync orchestration (Room first, then PostgREST).
 */
@Singleton
class SocialInteractor
    @Inject
    constructor(
        private val friendRepository: FriendRepository,
        private val groupRepository: GroupRepository,
        private val inviteRepository: InviteRepository,
        private val userRepository: UserRepository,
        private val expenseRepository: ExpenseRepository,
        private val remote: SocialRemoteDataSource,
        private val expenseInteractor: com.splitease.app.data.expense.ExpenseInteractor,
    ) {
        /**
         * Adds a friend by email. Existing SplitEase users are linked immediately;
         * otherwise a pending friend + email invite is created.
         *
         * @param ownerUserId Current user id.
         * @param email Friend email.
         * @return [Result] with [AddPersonOutcome] (share text when invite pending).
         */
        suspend fun addFriendByEmail(ownerUserId: String, email: String): Result<AddPersonOutcome> =
            runCatching {
                val normalized = email.trim()
                require(normalized.isNotBlank()) { "Email is required." }
                require(!normalized.equals(userRepository.getUserById(ownerUserId)?.email, ignoreCase = true)) {
                    "You can't add yourself."
                }

                friendRepository.getByOwnerAndEmail(ownerUserId, normalized)?.let { existing ->
                    val pendingInvite = inviteRepository.getByFriendRowId(existing.id)
                    val share =
                        if (pendingInvite?.status == InviteStatus.PENDING) {
                            InviteLinks.friendShareText(
                                inviterName = userRepository.getUserById(ownerUserId)?.displayName ?: "A friend",
                                token = pendingInvite.token,
                            )
                        } else {
                            null
                        }
                    return@runCatching AddPersonOutcome(
                        friend = existing,
                        inviteShareText = share,
                        isInvitePending = share != null,
                    )
                }

                val profile = remote.findProfileByEmail(normalized)
                if (profile != null) {
                    require(profile.id != ownerUserId) { "You can't add yourself." }
                    return@runCatching linkExistingFriend(ownerUserId, profile.id, profile.email, profile.displayName)
                }

                createPendingFriendInvite(ownerUserId, normalized, groupId = null)
            }

        /**
         * Invites an email into a group. Existing users are added as members;
         * non-users get a group invite link to share.
         *
         * @param ownerUserId Current user (group creator / inviter).
         * @param groupId Target group.
         * @param email Recipient email.
         * @return Outcome with optional share text.
         */
        suspend fun inviteToGroupByEmail(
            ownerUserId: String,
            groupId: String,
            email: String,
        ): Result<AddPersonOutcome> =
            runCatching {
                val normalized = email.trim()
                require(normalized.isNotBlank()) { "Email is required." }
                val group =
                    groupRepository.getGroupById(groupId)
                        ?: throw IllegalStateException("Group not found.")
                require(!normalized.equals(userRepository.getUserById(ownerUserId)?.email, ignoreCase = true)) {
                    "You can't invite yourself."
                }

                val profile = remote.findProfileByEmail(normalized)
                if (profile != null) {
                    require(profile.id != ownerUserId) { "You can't invite yourself." }
                    val friendOutcome =
                        linkExistingFriend(ownerUserId, profile.id, profile.email, profile.displayName)
                    addMemberToGroup(groupId, profile.id).getOrThrow()
                    return@runCatching friendOutcome
                }

                createPendingFriendInvite(ownerUserId, normalized, groupId = groupId, groupName = group.name)
            }

        /**
         * After sign-in/sign-up, claim any pending invites for this account's email.
         * Then refresh friends/groups into Room.
         *
         * @param userId Newly authenticated user id.
         */
        suspend fun acceptPendingInvitesForCurrentUser(userId: String) {
            remote.acceptPendingInvites()
            refreshFriends(userId)
            refreshGroups(userId)
            refreshSentInvites(userId)
            runCatching { expenseInteractor.refreshExpensesForUser(userId) }
        }

        /**
         * Pulls remote friends into Room for [ownerUserId].
         *
         * @param ownerUserId Current user id.
         */
        suspend fun refreshFriends(ownerUserId: String) {
            val remoteFriends = remote.fetchFriends(ownerUserId)
            remoteFriends.forEach { dto ->
                val previous = friendRepository.getById(dto.id)
                if (previous != null && previous.friendUserId != dto.friendUserId) {
                    expenseRepository.remapUserId(previous.friendUserId, dto.friendUserId)
                    groupRepository.remapMemberUserId(previous.friendUserId, dto.friendUserId)
                }
                friendRepository.upsert(
                    Friend(
                        id = dto.id,
                        ownerUserId = dto.ownerUserId,
                        friendUserId = dto.friendUserId,
                        emailSnapshot = dto.emailSnapshot,
                        displayNameSnapshot = dto.displayNameSnapshot,
                        remoteId = dto.id,
                        createdAtEpochMs = dto.updatedAtEpochMs,
                        updatedAtEpochMs = dto.updatedAtEpochMs,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }

        /**
         * Pulls invites sent by [inviterUserId] into Room.
         *
         * @param inviterUserId Current user id.
         */
        suspend fun refreshSentInvites(inviterUserId: String) {
            remote.fetchInvitesSentBy(inviterUserId).forEach { dto ->
                inviteRepository.upsert(
                    Invite(
                        id = dto.id,
                        token = dto.token,
                        inviterUserId = dto.inviterUserId,
                        email = dto.email,
                        kind = runCatching { InviteKind.valueOf(dto.kind) }.getOrDefault(InviteKind.FRIEND),
                        groupId = dto.groupId,
                        friendRowId = dto.friendRowId,
                        status = runCatching { InviteStatus.valueOf(dto.status) }.getOrDefault(InviteStatus.PENDING),
                        createdAtEpochMs = dto.createdAtEpochMs,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }

        /**
         * Creates a group with the creator as OWNER.
         *
         * @param creatorUserId Current user id.
         * @param name Group name.
         * @param currencyCode Default currency.
         * @param groupType Friends / Home / Other.
         * @param memberFriendUserIds Optional friend user ids to add as members.
         * @return Created [Group].
         */
        suspend fun createGroup(
            creatorUserId: String,
            name: String,
            currencyCode: String,
            groupType: com.splitease.app.domain.model.GroupType =
                com.splitease.app.domain.model.GroupType.OTHER,
            memberFriendUserIds: List<String> = emptyList(),
        ): Result<Group> =
            runCatching {
                require(name.isNotBlank()) { "Group name is required." }
                ensureLocalUserExists(creatorUserId)
                val now = System.currentTimeMillis()
                val groupId = UUID.randomUUID().toString()
                val group =
                    Group(
                        id = groupId,
                        name = name.trim(),
                        defaultCurrencyCode = currencyCode.trim().ifBlank { "INR" }.uppercase(),
                        groupType = groupType,
                        createdByUserId = creatorUserId,
                        remoteId = null,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        syncStatus = SyncStatus.PENDING,
                    )
                groupRepository.upsertGroup(group)

                val ownerMember =
                    GroupMember(
                        id = UUID.randomUUID().toString(),
                        groupId = groupId,
                        userId = creatorUserId,
                        role = MemberRole.OWNER,
                        joinedAtEpochMs = now,
                        syncStatus = SyncStatus.PENDING,
                    )
                groupRepository.upsertMember(ownerMember)

                val extraMembers =
                    memberFriendUserIds.distinct().filter { it != creatorUserId }.map { friendId ->
                        ensureLocalUserExists(friendId)
                        GroupMember(
                            id = UUID.randomUUID().toString(),
                            groupId = groupId,
                            userId = friendId,
                            role = MemberRole.MEMBER,
                            joinedAtEpochMs = now,
                            syncStatus = SyncStatus.PENDING,
                        ).also { groupRepository.upsertMember(it) }
                    }

                runCatching {
                    remote.upsertGroup(
                        GroupDto(
                            id = group.id,
                            name = group.name,
                            defaultCurrencyCode = group.defaultCurrencyCode,
                            createdByUserId = group.createdByUserId,
                            updatedAtEpochMs = now,
                        ),
                    )
                    remote.upsertGroupMember(
                        GroupMemberDto(
                            id = ownerMember.id,
                            groupId = ownerMember.groupId,
                            userId = ownerMember.userId,
                            role = ownerMember.role.name,
                            joinedAtEpochMs = ownerMember.joinedAtEpochMs,
                        ),
                    )
                    extraMembers.forEach { member ->
                        remote.upsertGroupMember(
                            GroupMemberDto(
                                id = member.id,
                                groupId = member.groupId,
                                userId = member.userId,
                                role = member.role.name,
                                joinedAtEpochMs = member.joinedAtEpochMs,
                            ),
                        )
                    }
                    val synced = group.copy(remoteId = group.id, syncStatus = SyncStatus.SYNCED)
                    groupRepository.upsertGroup(synced)
                    groupRepository.upsertMember(ownerMember.copy(syncStatus = SyncStatus.SYNCED))
                    extraMembers.forEach {
                        groupRepository.upsertMember(it.copy(syncStatus = SyncStatus.SYNCED))
                    }
                    synced
                }.getOrDefault(group)
            }

        /**
         * Updates group name/currency locally and syncs when possible.
         *
         * @param group Updated group.
         */
        suspend fun updateGroup(group: Group): Result<Unit> =
            runCatching {
                val now = System.currentTimeMillis()
                val pending = group.copy(updatedAtEpochMs = now, syncStatus = SyncStatus.PENDING)
                groupRepository.upsertGroup(pending)
                runCatching {
                    remote.upsertGroup(
                        GroupDto(
                            id = pending.id,
                            name = pending.name,
                            defaultCurrencyCode = pending.defaultCurrencyCode,
                            createdByUserId = pending.createdByUserId,
                            updatedAtEpochMs = now,
                        ),
                    )
                    groupRepository.upsertGroup(
                        pending.copy(remoteId = pending.id, syncStatus = SyncStatus.SYNCED),
                    )
                }
            }

        /**
         * Adds a friend as a group member (must be a real auth user id, not an invite placeholder).
         *
         * @param groupId Group id.
         * @param userId Member user id.
         */
        suspend fun addMemberToGroup(groupId: String, userId: String): Result<Unit> =
            runCatching {
                val now = System.currentTimeMillis()
                val member =
                    GroupMember(
                        id = UUID.randomUUID().toString(),
                        groupId = groupId,
                        userId = userId,
                        role = MemberRole.MEMBER,
                        joinedAtEpochMs = now,
                        syncStatus = SyncStatus.PENDING,
                    )
                groupRepository.upsertMember(member)
                runCatching {
                    remote.upsertGroupMember(
                        GroupMemberDto(
                            id = member.id,
                            groupId = member.groupId,
                            userId = member.userId,
                            role = member.role.name,
                            joinedAtEpochMs = member.joinedAtEpochMs,
                        ),
                    )
                    groupRepository.upsertMember(member.copy(syncStatus = SyncStatus.SYNCED))
                }
            }

        /**
         * Removes the current user from a group. If they are the last member, deletes the group.
         *
         * @param groupId Group id.
         * @param userId User leaving.
         */
        suspend fun leaveGroup(groupId: String, userId: String): Result<Unit> =
            runCatching {
                val member =
                    groupRepository.getMember(groupId, userId)
                        ?: error("You are not a member of this group.")
                val members = groupRepository.observeMembers(groupId).first()
                if (members.size <= 1) {
                    deleteGroup(groupId, userId).getOrThrow()
                    return@runCatching
                }
                groupRepository.deleteMemberById(member.id)
                runCatching { remote.deleteGroupMember(member.id) }
            }

        /**
         * Deletes a group locally and from the cloud when possible. Owner-only.
         *
         * @param groupId Group id.
         * @param requesterId User requesting deletion.
         */
        suspend fun deleteGroup(groupId: String, requesterId: String): Result<Unit> =
            runCatching {
                val group =
                    groupRepository.getGroupById(groupId)
                        ?: error("Group not found.")
                require(group.createdByUserId == requesterId) {
                    "Only the group owner can delete this group."
                }
                groupRepository.deleteGroupById(groupId)
                runCatching { remote.deleteGroup(groupId) }
            }

        /**
         * Pulls remote groups/memberships for [userId] into Room.
         * No-ops when cloud tables are missing or the device is offline.
         *
         * @param userId Current user id.
         */
        suspend fun refreshGroups(userId: String) {
            val memberships =
                runCatching { remote.fetchMembershipsForUser(userId) }.getOrElse { return }
            val created =
                runCatching { remote.fetchGroupsCreatedBy(userId) }.getOrDefault(emptyList())
            val groupIds = (memberships.map { it.groupId } + created.map { it.id }).distinct()

            groupIds.forEach { groupId ->
                val dto = runCatching { remote.fetchGroup(groupId) }.getOrNull() ?: return@forEach
                val existing = groupRepository.getGroupById(dto.id)
                groupRepository.upsertGroup(
                    Group(
                        id = dto.id,
                        name = dto.name,
                        defaultCurrencyCode = dto.defaultCurrencyCode,
                        groupType = existing?.groupType ?: com.splitease.app.domain.model.GroupType.OTHER,
                        createdByUserId = dto.createdByUserId,
                        remoteId = dto.id,
                        createdAtEpochMs = existing?.createdAtEpochMs ?: dto.updatedAtEpochMs,
                        updatedAtEpochMs = dto.updatedAtEpochMs,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
                runCatching { remote.fetchGroupMembers(groupId) }.getOrDefault(emptyList()).forEach { memberDto ->
                    ensureLocalUserExists(memberDto.userId)
                    groupRepository.upsertMember(
                        GroupMember(
                            id = memberDto.id,
                            groupId = memberDto.groupId,
                            userId = memberDto.userId,
                            role = runCatching { MemberRole.valueOf(memberDto.role) }.getOrDefault(MemberRole.MEMBER),
                            joinedAtEpochMs = memberDto.joinedAtEpochMs,
                            syncStatus = SyncStatus.SYNCED,
                        ),
                    )
                }
            }
        }

        /**
         * Room FK on [GroupMember] requires a [User] row. Session restore / DB wipe can leave
         * auth signed-in without a local user — insert a minimal stub when missing.
         */
        private suspend fun ensureLocalUserExists(userId: String) {
            if (userRepository.getUserById(userId) != null) return
            val now = System.currentTimeMillis()
            userRepository.upsert(
                User(
                    id = userId,
                    email = "",
                    displayName = "You",
                    photoUrl = null,
                    remoteId = userId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            )
        }

        private suspend fun linkExistingFriend(
            ownerUserId: String,
            friendUserId: String,
            email: String,
            displayName: String,
        ): AddPersonOutcome {
            val now = System.currentTimeMillis()
            userRepository.upsert(
                User(
                    id = friendUserId,
                    email = email,
                    displayName = displayName,
                    photoUrl = null,
                    remoteId = friendUserId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.SYNCED,
                ),
            )

            val existing = friendRepository.getByOwnerAndEmail(ownerUserId, email)
            val friend =
                existing?.copy(
                    friendUserId = friendUserId,
                    displayNameSnapshot = displayName,
                    emailSnapshot = email,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.PENDING,
                ) ?: Friend(
                    id = UUID.randomUUID().toString(),
                    ownerUserId = ownerUserId,
                    friendUserId = friendUserId,
                    emailSnapshot = email,
                    displayNameSnapshot = displayName,
                    remoteId = null,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.PENDING,
                )
            friendRepository.upsert(friend)

            val synced =
                runCatching {
                    remote.upsertFriend(
                        FriendDto(
                            id = friend.id,
                            ownerUserId = friend.ownerUserId,
                            friendUserId = friend.friendUserId,
                            emailSnapshot = friend.emailSnapshot,
                            displayNameSnapshot = friend.displayNameSnapshot,
                            updatedAtEpochMs = now,
                        ),
                    )
                    friend.copy(remoteId = friend.id, syncStatus = SyncStatus.SYNCED, updatedAtEpochMs = now)
                }.getOrDefault(friend)

            if (synced.syncStatus == SyncStatus.SYNCED) {
                friendRepository.upsert(synced)
            }
            return AddPersonOutcome(friend = synced, inviteShareText = null, isInvitePending = false)
        }

        private suspend fun createPendingFriendInvite(
            ownerUserId: String,
            email: String,
            groupId: String?,
            groupName: String? = null,
        ): AddPersonOutcome {
            val now = System.currentTimeMillis()
            val placeholderId = UUID.randomUUID().toString()
            val localPart = email.substringBefore("@").ifBlank { "Friend" }
            val displayName = "$localPart (invited)"

            userRepository.upsert(
                User(
                    id = placeholderId,
                    email = email,
                    displayName = displayName,
                    photoUrl = null,
                    remoteId = null,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            )

            val friend =
                Friend(
                    id = UUID.randomUUID().toString(),
                    ownerUserId = ownerUserId,
                    friendUserId = placeholderId,
                    emailSnapshot = email,
                    displayNameSnapshot = displayName,
                    remoteId = null,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.PENDING,
                )
            friendRepository.upsert(friend)

            runCatching {
                remote.upsertFriend(
                    FriendDto(
                        id = friend.id,
                        ownerUserId = friend.ownerUserId,
                        friendUserId = friend.friendUserId,
                        emailSnapshot = friend.emailSnapshot,
                        displayNameSnapshot = friend.displayNameSnapshot,
                        updatedAtEpochMs = now,
                    ),
                )
                friendRepository.upsert(
                    friend.copy(remoteId = friend.id, syncStatus = SyncStatus.SYNCED),
                )
            }

            val token = UUID.randomUUID().toString().replace("-", "")
            val kind = if (groupId != null) InviteKind.GROUP else InviteKind.FRIEND
            val invite =
                Invite(
                    id = UUID.randomUUID().toString(),
                    token = token,
                    inviterUserId = ownerUserId,
                    email = email,
                    kind = kind,
                    groupId = groupId,
                    friendRowId = friend.id,
                    status = InviteStatus.PENDING,
                    createdAtEpochMs = now,
                    syncStatus = SyncStatus.PENDING,
                )
            inviteRepository.upsert(invite)

            if (groupId != null && groupRepository.getMember(groupId, placeholderId) == null) {
                groupRepository.upsertMember(
                    GroupMember(
                        id = UUID.randomUUID().toString(),
                        groupId = groupId,
                        userId = placeholderId,
                        role = MemberRole.MEMBER,
                        joinedAtEpochMs = now,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                    ),
                )
            }

            runCatching {
                remote.upsertInvite(
                    InviteDto(
                        id = invite.id,
                        token = invite.token,
                        inviterUserId = invite.inviterUserId,
                        email = invite.email,
                        kind = invite.kind.name,
                        groupId = invite.groupId,
                        friendRowId = invite.friendRowId,
                        status = invite.status.name,
                        createdAtEpochMs = invite.createdAtEpochMs,
                    ),
                )
                inviteRepository.upsert(invite.copy(syncStatus = SyncStatus.SYNCED))
            }

            val inviterName = userRepository.getUserById(ownerUserId)?.displayName ?: "A friend"
            val shareText =
                if (groupId != null) {
                    InviteLinks.groupShareText(inviterName, groupName ?: "a group", token)
                } else {
                    InviteLinks.friendShareText(inviterName, token)
                }

            return AddPersonOutcome(
                friend = friend,
                inviteShareText = shareText,
                isInvitePending = true,
            )
        }
    }
