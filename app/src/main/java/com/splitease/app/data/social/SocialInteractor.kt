package com.splitease.app.data.social

import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.ProfileDto
import com.splitease.app.data.remote.mapper.toDomain
import com.splitease.app.data.remote.mapper.toDto
import com.splitease.app.domain.model.AddPersonOutcome
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.GroupShareLink
import com.splitease.app.domain.model.Invite
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.InvitePreview
import com.splitease.app.domain.model.InvitePreviewMember
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
            addFriendByContact(ownerUserId, contact = email, displayName = null, groupId = null)

        /**
         * Adds or invites a person by email or phone contact string.
         *
         * @param ownerUserId Current user id.
         * @param contact Email or phone number.
         * @param displayName Optional preferred name (used for pending invites).
         * @param groupId When set, also invite into that group.
         * @return [Result] with [AddPersonOutcome].
         */
        suspend fun addFriendByContact(
            ownerUserId: String,
            contact: String,
            displayName: String? = null,
            groupId: String? = null,
        ): Result<AddPersonOutcome> =
            runCatching {
                val normalized = contact.trim()
                require(normalized.isNotBlank()) { "Email or phone is required." }
                val looksLikeEmail = normalized.contains("@")
                require(looksLikeEmail || normalized.any { it.isDigit() }) {
                    "Enter a valid email or phone number."
                }
                val selfEmail = userRepository.getUserById(ownerUserId)?.email
                require(!normalized.equals(selfEmail, ignoreCase = true)) {
                    "You can't add yourself."
                }

                if (groupId != null) {
                    return@runCatching inviteToGroupByContact(
                        ownerUserId = ownerUserId,
                        groupId = groupId,
                        contact = normalized,
                        displayName = displayName,
                    )
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

                if (looksLikeEmail) {
                    val profile = remote.findProfileByEmail(normalized)
                    if (profile != null) {
                        require(profile.id != ownerUserId) { "You can't add yourself." }
                        return@runCatching linkExistingFriend(
                            ownerUserId,
                            profile.id,
                            profile.email,
                            displayName?.takeIf { it.isNotBlank() } ?: profile.displayName,
                        )
                    }
                }

                createPendingFriendInvite(
                    ownerUserId = ownerUserId,
                    email = normalized,
                    groupId = null,
                    displayNameOverride = displayName,
                )
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
                inviteToGroupByContact(ownerUserId, groupId, email, displayName = null)
            }

        /**
         * Creates a generic, shareable group invite link token.
         *
         * Reuses an existing pending group share-link invite when one exists.
         * The invite is accepted by `accept_invite_by_token`, so any signed-in recipient
         * opening the link can be auto-joined to the group.
         *
         * @param ownerUserId Current user (group owner/inviter).
         * @param groupId Target group.
         * @return Plain-text share body containing the deep link.
         */
        suspend fun createGroupShareLink(
            ownerUserId: String,
            groupId: String,
        ): Result<String> =
            getOrCreateGroupShareLink(ownerUserId, groupId).map { it.shareText }

        /**
         * Returns an existing pending group share link, or creates one.
         *
         * @param ownerUserId Current user (group owner/inviter).
         * @param groupId Target group.
         * @return [GroupShareLink] with URL and share text.
         */
        suspend fun getOrCreateGroupShareLink(
            ownerUserId: String,
            groupId: String,
        ): Result<GroupShareLink> =
            runCatching {
                val existing = inviteRepository.getGroupShareInvites(groupId).firstOrNull()
                if (existing != null) {
                    if (existing.syncStatus != SyncStatus.SYNCED) {
                        pushInviteToCloud(existing)
                    }
                    return@runCatching toGroupShareLink(ownerUserId, groupId, existing.token)
                }
                createFreshGroupShareLink(ownerUserId, groupId)
            }

        /**
         * Invalidates prior generic group share links and creates a new token.
         *
         * @param ownerUserId Current user (group owner/inviter).
         * @param groupId Target group.
         * @return New [GroupShareLink].
         */
        suspend fun regenerateGroupShareLink(
            ownerUserId: String,
            groupId: String,
        ): Result<GroupShareLink> =
            runCatching {
                cancelPendingGroupShareLinks(groupId)
                createFreshGroupShareLink(ownerUserId, groupId)
            }

        private suspend fun createFreshGroupShareLink(
            ownerUserId: String,
            groupId: String,
        ): GroupShareLink {
            val group =
                groupRepository.getGroupById(groupId)
                    ?: throw IllegalStateException("Group not found.")
            // Must NOT use the inviter's real email: accept_pending_invites matches by
            // email and would auto-accept (burn) the share link on the inviter's next sync.
            // Token-only claim via accept_invite_by_token; placeholder never matches a user.
            val invite =
                buildPendingInvite(
                    inviterUserId = ownerUserId,
                    email = GROUP_SHARE_LINK_EMAIL,
                    kind = InviteKind.GROUP,
                    groupId = groupId,
                    friendRowId = null,
                )
            inviteRepository.upsert(invite)
            pushInviteToCloud(invite)
            return toGroupShareLink(ownerUserId, groupId, invite.token, group.name)
        }

        private suspend fun cancelPendingGroupShareLinks(groupId: String) {
            val pending = inviteRepository.getGroupShareInvites(groupId)
            for (invite in pending) {
                val cancelled =
                    invite.copy(
                        status = InviteStatus.CANCELLED,
                        syncStatus = SyncStatus.PENDING,
                    )
                inviteRepository.upsert(cancelled)
                runCatching {
                    remote.upsertInvite(cancelled.toDto())
                    inviteRepository.upsert(cancelled.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }

        private suspend fun toGroupShareLink(
            ownerUserId: String,
            groupId: String,
            token: String,
            knownGroupName: String? = null,
        ): GroupShareLink {
            val groupName =
                knownGroupName
                    ?: groupRepository.getGroupById(groupId)?.name
                    ?: "a group"
            val inviterName = userRepository.getUserById(ownerUserId)?.displayName ?: "A friend"
            return GroupShareLink(
                groupName = groupName,
                url = InviteLinks.clipboardLink(token),
                shareText = InviteLinks.groupShareText(inviterName, groupName, token),
            )
        }

        private suspend fun inviteToGroupByContact(
            ownerUserId: String,
            groupId: String,
            contact: String,
            displayName: String?,
        ): AddPersonOutcome {
            val normalized = contact.trim()
            val group =
                groupRepository.getGroupById(groupId)
                    ?: throw IllegalStateException("Group not found.")
            require(
                !normalized.equals(
                    userRepository.getUserById(ownerUserId)?.email,
                    ignoreCase = true,
                ),
            ) {
                "You can't invite yourself."
            }

            if (normalized.contains("@")) {
                val profile = remote.findProfileByEmail(normalized)
                if (profile != null) {
                    require(profile.id != ownerUserId) { "You can't invite yourself." }
                    val friendOutcome =
                        linkExistingFriend(
                            ownerUserId,
                            profile.id,
                            profile.email,
                            displayName?.takeIf { it.isNotBlank() } ?: profile.displayName,
                        )
                    addMemberToGroup(groupId, profile.id).getOrThrow()
                    return friendOutcome
                }
            }

            return createPendingFriendInvite(
                ownerUserId = ownerUserId,
                email = normalized,
                groupId = groupId,
                groupName = group.name,
                displayNameOverride = displayName,
            )
        }

        /**
         * After sign-in/sign-up, claim any pending invites for this account's email.
         * Then refresh friends/groups into Room.
         *
         * @param userId Newly authenticated user id.
         * @param inviteToken Optional deep-link token to accept first (join-as-new).
         * @return True when there was no token, or the token invite is no longer pending
         *   (accepted / invalid). False when the token invite is still pending after claim.
         */
        suspend fun acceptPendingInvitesForCurrentUser(
            userId: String,
            inviteToken: String? = null,
        ): Boolean {
            var acceptedByToken = false
            if (!inviteToken.isNullOrBlank()) {
                // Do not swallow — callers must know when the RPC fails so the token is kept.
                val accepted = remote.acceptInviteByToken(inviteToken)
                acceptedByToken = accepted > 0
            }
            runCatching { remote.acceptPendingInvites() }
            refreshFriends(userId)
            refreshGroups(userId)
            refreshSentInvites(userId)
            runCatching { expenseInteractor.refreshExpensesForUser(userId) }
            if (inviteToken.isNullOrBlank()) return true
            if (acceptedByToken) return true
            // RPC returned 0: invite missing/already used, or accept no-oped.
            return loadInvitePreview(inviteToken) == null
        }

        /**
         * Rebuilds share body text for an existing pending invite.
         *
         * @param friendRowId Local friendship row id linked to the invite.
         * @return Share text, or null when no pending invite exists.
         */
        suspend fun pendingInviteShareText(friendRowId: String): String? {
            val invite = inviteRepository.getByFriendRowId(friendRowId) ?: return null
            if (invite.status != InviteStatus.PENDING) return null
            val inviterName =
                userRepository.getUserById(invite.inviterUserId)?.displayName ?: "A friend"
            return if (!invite.groupId.isNullOrBlank()) {
                val groupName =
                    groupRepository.getGroupById(invite.groupId)?.name ?: "a group"
                InviteLinks.groupShareText(inviterName, groupName, invite.token)
            } else {
                InviteLinks.friendShareText(inviterName, invite.token)
            }
        }

        /**
         * Invite URL suitable for clipboard copy (reuses the existing pending token).
         *
         * @param friendRowId Local friendship row id linked to the invite.
         * @return https invite URL from [InviteLinks], or null when no pending invite exists.
         */
        suspend fun pendingInviteClipboardLink(friendRowId: String): String? {
            val invite = inviteRepository.getByFriendRowId(friendRowId) ?: return null
            if (invite.status != InviteStatus.PENDING) return null
            return InviteLinks.clipboardLink(invite.token)
        }

        /**
         * Loads invite landing preview for a deep-link token (anonymous-safe).
         *
         * @param token Opaque invite token.
         * @return Domain preview, or null when invalid / already used.
         */
        suspend fun loadInvitePreview(token: String): InvitePreview? {
            val dto = remote.fetchInvitePreview(token) ?: return null
            return InvitePreview(
                token = dto.token,
                kind = runCatching { InviteKind.valueOf(dto.kind) }.getOrDefault(InviteKind.FRIEND),
                email = dto.email,
                inviterName = dto.inviterName.ifBlank { "A friend" },
                groupId = dto.groupId,
                groupName = dto.groupName,
                members =
                    dto.members.map { member ->
                        InvitePreviewMember(
                            displayName = member.displayName,
                            alreadyJoined = member.alreadyJoined,
                        )
                    },
            )
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
                // group_members FK requires a users row for friendUserId
                ensureLocalUserExists(
                    userId = dto.friendUserId,
                    email = dto.emailSnapshot,
                    displayName = dto.displayNameSnapshot,
                )
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
                inviteRepository.upsert(dto.toDomain())
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
                val self = userRepository.getUserById(creatorUserId)
                ensureLocalUserExists(
                    userId = creatorUserId,
                    email = self?.email.orEmpty(),
                    displayName = self?.displayName?.takeIf { it.isNotBlank() } ?: "You",
                )
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
                        val friend = friendRepository.getByFriendUserId(friendId)
                        ensureLocalUserExists(
                            userId = friendId,
                            email = friend?.emailSnapshot.orEmpty(),
                            displayName =
                                friend?.displayNameSnapshot?.takeIf { it.isNotBlank() } ?: "Member",
                        )
                        GroupMember(
                            id = UUID.randomUUID().toString(),
                            groupId = groupId,
                            userId = friendId,
                            role = MemberRole.MEMBER,
                            joinedAtEpochMs = now,
                            syncStatus = SyncStatus.PENDING,
                        ).also { groupRepository.upsertMember(it) }
                    }

                // Upload group + owner first so cloud FK exists even if extra members fail
                // (e.g. invite placeholders that are not auth.users yet).
                val cloudError =
                    runCatching {
                        remote.upsertGroup(group.toDto(updatedAtEpochMs = now))
                        remote.upsertGroupMember(ownerMember.toDto())
                        groupRepository.upsertGroup(group.copy(remoteId = group.id, syncStatus = SyncStatus.SYNCED))
                        groupRepository.upsertMember(ownerMember.copy(syncStatus = SyncStatus.SYNCED))
                    }.exceptionOrNull()

                if (cloudError != null) {
                    android.util.Log.e(
                        "SplitEaseSocial",
                        "Group cloud upsert failed (kept locally as PENDING): ${cloudError.message}",
                        cloudError,
                    )
                }

                extraMembers.forEach { member ->
                    runCatching {
                        remote.upsertGroupMember(member.toDto())
                        groupRepository.upsertMember(member.copy(syncStatus = SyncStatus.SYNCED))
                    }.onFailure { err ->
                        android.util.Log.e(
                            "SplitEaseSocial",
                            "Group member cloud upsert failed for ${member.userId}: ${err.message}",
                            err,
                        )
                    }
                }

                groupRepository.getGroupById(groupId) ?: group
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
                    remote.upsertGroup(pending.toDto(updatedAtEpochMs = now))
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
                val friend = friendRepository.getByFriendUserId(userId)
                ensureLocalUserExists(
                    userId = userId,
                    email = friend?.emailSnapshot.orEmpty(),
                    displayName = friend?.displayNameSnapshot?.takeIf { it.isNotBlank() } ?: "Member",
                )
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
                    remote.upsertGroupMember(member.toDto())
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
                val memberDtos =
                    runCatching { remote.fetchGroupMembers(groupId) }.getOrDefault(emptyList())
                val profilesById =
                    runCatching { remote.fetchProfilesByIds(memberDtos.map { it.userId }) }
                        .getOrDefault(emptyList())
                        .associateBy { it.id }
                memberDtos.forEach { memberDto ->
                    ensureLocalUserExists(memberDto.userId, profilesById[memberDto.userId])
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
         * Room FK on [GroupMember] requires a [User] row. Prefer the remote [ProfileDto] when
         * available so co-members show real names (not UUID stubs). Upgrades existing stubs.
         *
         * Also resolves unique-email conflicts with invite placeholders so the real auth user
         * id can be written (otherwise group create hits SQLITE_CONSTRAINT_FOREIGNKEY).
         *
         * @param userId User id to ensure.
         * @param profile Optional profile already fetched for this user.
         * @param email Optional email for the stub when no profile.
         * @param displayName Optional display name for the stub when no profile.
         */
        private suspend fun ensureLocalUserExists(
            userId: String,
            profile: ProfileDto? = null,
            email: String = "",
            displayName: String = "Member",
        ) {
            val resolved =
                profile ?: runCatching { remote.fetchProfileById(userId) }.getOrNull()
            val existing = userRepository.getUserById(userId)
            val now = System.currentTimeMillis()
            val resolvedEmail =
                when {
                    resolved != null && resolved.email.isNotBlank() -> resolved.email.trim()
                    email.isNotBlank() -> email.trim()
                    existing != null && existing.email.isNotBlank() &&
                        !existing.email.startsWith("local+") -> existing.email
                    else -> "local+$userId@users.local"
                }
            releaseEmailAndRemapStub(ownerUserId = userId, email = resolvedEmail)
            if (resolved != null) {
                userRepository.upsert(
                    User(
                        id = userId,
                        email = resolvedEmail,
                        displayName = resolved.displayName.ifBlank { displayName.ifBlank { "Member" } },
                        photoUrl = resolved.photoUrl,
                        phoneCountryCode = resolved.phoneCountryCode,
                        phoneNumber = resolved.phoneNumber,
                        preferredCurrency = resolved.preferredCurrency,
                        remoteId = userId,
                        createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                        updatedAtEpochMs = resolved.updatedAtEpochMs.takeIf { it > 0 } ?: now,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            } else if (existing == null) {
                userRepository.upsert(
                    User(
                        id = userId,
                        email = resolvedEmail,
                        displayName = displayName.ifBlank { "Member" },
                        photoUrl = null,
                        remoteId = userId,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                    ),
                )
            } else if (existing.email.isBlank() || existing.email.startsWith("local+")) {
                userRepository.upsert(
                    existing.copy(
                        email = resolvedEmail,
                        displayName =
                            existing.displayName.takeIf { it.isNotBlank() && it != "Member" }
                                ?: displayName.ifBlank { "Member" },
                        updatedAtEpochMs = now,
                    ),
                )
            }
            require(userRepository.getUserById(userId) != null) {
                "Local user profile is missing. Sign out and sign in again."
            }
        }

        /**
         * If another local user row owns [email], free the unique index and remap FKs onto [ownerUserId].
         */
        private suspend fun releaseEmailAndRemapStub(
            ownerUserId: String,
            email: String,
        ) {
            val trimmed = email.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("local+")) return
            val conflict = userRepository.getUserByEmail(trimmed) ?: return
            if (conflict.id == ownerUserId) return
            val now = System.currentTimeMillis()
            userRepository.upsert(
                conflict.copy(
                    email = "local+${conflict.id}@users.local",
                    updatedAtEpochMs = now,
                ),
            )
            // Ensure destination user exists before remapping FK child rows.
            if (userRepository.getUserById(ownerUserId) == null) {
                userRepository.upsert(
                    User(
                        id = ownerUserId,
                        email = trimmed,
                        displayName = conflict.displayName.removeSuffix(" (invited)").ifBlank { "You" },
                        photoUrl = conflict.photoUrl,
                        phoneCountryCode = conflict.phoneCountryCode,
                        phoneNumber = conflict.phoneNumber,
                        preferredCurrency = conflict.preferredCurrency,
                        remoteId = ownerUserId,
                        createdAtEpochMs = conflict.createdAtEpochMs,
                        updatedAtEpochMs = now,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
            runCatching { groupRepository.remapMemberUserId(conflict.id, ownerUserId) }
            runCatching { expenseRepository.remapUserId(conflict.id, ownerUserId) }
            runCatching {
                friendRepository.getByFriendUserId(conflict.id)?.let { friend ->
                    friendRepository.upsert(
                        friend.copy(
                            friendUserId = ownerUserId,
                            emailSnapshot = trimmed,
                            displayNameSnapshot =
                                friend.displayNameSnapshot
                                    .removeSuffix(" (invited)")
                                    .ifBlank { friend.displayNameSnapshot },
                            updatedAtEpochMs = now,
                            syncStatus = SyncStatus.PENDING,
                        ),
                    )
                }
            }
            runCatching { userRepository.deleteById(conflict.id) }
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
                    remote.upsertFriend(friend.toDto(updatedAtEpochMs = now))
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
            displayNameOverride: String? = null,
        ): AddPersonOutcome {
            val now = System.currentTimeMillis()
            val placeholderId = UUID.randomUUID().toString()
            val localPart =
                displayNameOverride?.trim()?.takeIf { it.isNotEmpty() }
                    ?: email.substringBefore("@").ifBlank { "Friend" }
            val displayName =
                if (localPart.contains("(invited)", ignoreCase = true)) {
                    localPart
                } else {
                    "$localPart (invited)"
                }

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
                remote.upsertFriend(friend.toDto(updatedAtEpochMs = now))
                friendRepository.upsert(
                    friend.copy(remoteId = friend.id, syncStatus = SyncStatus.SYNCED),
                )
            }

            val kind = if (groupId != null) InviteKind.GROUP else InviteKind.FRIEND
            val invite =
                buildPendingInvite(
                    inviterUserId = ownerUserId,
                    email = email,
                    kind = kind,
                    groupId = groupId,
                    friendRowId = friend.id,
                    now = now,
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

            // GROUP invites FK to public.groups — push the group first or the invite
            // upsert fails and deep links open the app with nothing to claim.
            // Fail the whole invite if cloud sync fails; never share a token that isn't claimable.
            pushInviteToCloud(invite)

            val inviterName = userRepository.getUserById(ownerUserId)?.displayName ?: "A friend"
            val shareText =
                if (groupId != null) {
                    InviteLinks.groupShareText(inviterName, groupName ?: "a group", invite.token)
                } else {
                    InviteLinks.friendShareText(inviterName, invite.token)
                }

            return AddPersonOutcome(
                friend = friend,
                inviteShareText = shareText,
                isInvitePending = true,
            )
        }

        private fun newInviteToken(): String = UUID.randomUUID().toString().replace("-", "")

        companion object {
            /**
             * Placeholder email on generic group share-link invites (no [friendRowId]).
             * Must not match any real auth user so email-based accept cannot burn the link.
             */
            const val GROUP_SHARE_LINK_EMAIL = "group-share@splitease.invalid"
        }

        private fun buildPendingInvite(
            inviterUserId: String,
            email: String,
            kind: InviteKind,
            groupId: String?,
            friendRowId: String?,
            now: Long = System.currentTimeMillis(),
        ): Invite =
            Invite(
                id = UUID.randomUUID().toString(),
                token = newInviteToken(),
                inviterUserId = inviterUserId,
                email = email,
                kind = kind,
                groupId = groupId,
                friendRowId = friendRowId,
                status = InviteStatus.PENDING,
                createdAtEpochMs = now,
                syncStatus = SyncStatus.PENDING,
            )

        /**
         * Ensures group FK (when needed), upserts the invite remotely, and marks it synced.
         *
         * @param invite Local pending invite to push.
         */
        private suspend fun pushInviteToCloud(invite: Invite) {
            invite.groupId?.let { ensureGroupSyncedToCloud(it) }
            remote.upsertInvite(invite.toDto())
            inviteRepository.upsert(invite.copy(syncStatus = SyncStatus.SYNCED))
        }

        /**
         * Ensures [groupId] exists in Supabase before writing a GROUP invite (FK-safe).
         *
         * @param groupId Local / remote group id.
         */
        private suspend fun ensureGroupSyncedToCloud(groupId: String) {
            val group = groupRepository.getGroupById(groupId) ?: return
            // Always upsert before invite write — local SYNCED can be stale when a prior
            // cloud upsert failed under RLS (token must never be shared without a cloud row).
            val now = System.currentTimeMillis()
            remote.upsertGroup(group.toDto(updatedAtEpochMs = now))
            groupRepository.upsertGroup(
                group.copy(remoteId = group.id, syncStatus = SyncStatus.SYNCED, updatedAtEpochMs = now),
            )
            val owner = groupRepository.getMember(groupId, group.createdByUserId)
            if (owner != null && owner.syncStatus != SyncStatus.SYNCED) {
                remote.upsertGroupMember(owner.toDto())
                groupRepository.upsertMember(owner.copy(syncStatus = SyncStatus.SYNCED))
            }
        }
    }
