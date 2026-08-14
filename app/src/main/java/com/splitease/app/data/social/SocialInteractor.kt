package com.splitease.app.data.social

import android.content.Context
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.data.media.LocalMediaCleanup
import com.splitease.app.data.media.MediaStorageCleanup
import com.splitease.app.data.pinboard.PinBoardInteractor
import com.splitease.app.data.remote.GroupCoverStorage
import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.GroupMemberDto
import com.splitease.app.data.remote.dto.ProfileDto
import com.splitease.app.data.remote.mapper.isRemoteMediaUrl
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
import com.splitease.app.domain.repository.MailRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppCurrencies
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
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
        @ApplicationContext private val appContext: Context,
        private val friendRepository: FriendRepository,
        private val groupRepository: GroupRepository,
        private val inviteRepository: InviteRepository,
        private val userRepository: UserRepository,
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val remote: SocialRemoteDataSource,
        private val groupCoverStorage: GroupCoverStorage,
        private val mediaStorageCleanup: MediaStorageCleanup,
        private val pinBoardInteractor: PinBoardInteractor,
        private val expenseInteractor: com.splitease.app.data.expense.ExpenseInteractor,
        private val mailRepository: MailRepository,
        private val paymentRemote: PaymentRemoteDataSource,
    ) {
        /**
         * Removes a friend, deletes non-group expenses/payments between the two users,
         * and cancels any pending invite.
         *
         * @param ownerUserId Current user id.
         * @param friendUserId Friend's user id (or pending placeholder).
         */
        suspend fun removeFriend(
            ownerUserId: String,
            friendUserId: String,
        ): Result<Unit> =
            runCatching {
                val friend =
                    friendRepository
                        .getByOwnerAndFriendUserId(ownerUserId, friendUserId)
                        ?: error("Friend not found.")

                inviteRepository.getByFriendRowId(friend.id)?.let { invite ->
                    if (invite.status == InviteStatus.PENDING) {
                        cancelInviteAndPush(invite)
                    }
                }

                expenseRepository.observeBetweenUsers(ownerUserId, friendUserId).first().forEach { expense ->
                    runCatching {
                        expenseInteractor.deleteExpense(expense.id, actorUserId = ownerUserId).getOrThrow()
                    }.onFailure {
                        // Fall back to local+media cleanup if remote delete fails mid-remove.
                        expenseInteractor.purgeExpenseMedia(expense.id)
                        expenseRepository.deleteExpenseById(expense.id)
                    }
                }
                paymentRepository
                    .observeBetweenUsers(ownerUserId, friendUserId)
                    .first()
                    .filter { it.groupId == null }
                    .forEach { payment ->
                        runCatching { paymentRemote.delete(payment.id) }
                        paymentRepository.deleteById(payment.id)
                    }

                friendRepository.deleteById(friend.id)
                runCatching { remote.deleteFriend(friend.remoteId ?: friend.id) }
            }

        /**
         * Updates a friend's display name and/or contact. For pending invites, regenerates
         * the invite when the contact changes.
         *
         * @param ownerUserId Current user id.
         * @param friendUserId Friend's user id.
         * @param displayName New display name.
         * @param contact New email or phone.
         * @return Outcome with optional share text when a new invite is pending.
         */
        suspend fun updateFriendContact(
            ownerUserId: String,
            friendUserId: String,
            displayName: String,
            contact: String,
        ): Result<AddPersonOutcome> =
            runCatching {
                val friend =
                    friendRepository
                        .getByOwnerAndFriendUserId(ownerUserId, friendUserId)
                        ?: error("Friend not found.")
                val normalized = contact.trim()
                require(normalized.isNotBlank()) { "Email or phone is required." }
                val name = displayName.trim().ifBlank { friend.displayNameSnapshot }
                val pending =
                    friend.displayNameSnapshot.contains("(invited)", ignoreCase = true) ||
                        inviteRepository.getByFriendRowId(friend.id)?.status == InviteStatus.PENDING
                val now = System.currentTimeMillis()

                if (!pending) {
                    val updated =
                        friend.copy(
                            displayNameSnapshot = name.removeSuffix(" (invited)").trim().ifBlank { name },
                            emailSnapshot = normalized,
                            updatedAtEpochMs = now,
                            syncStatus = SyncStatus.PENDING,
                        )
                    friendRepository.upsert(updated)
                    runCatching {
                        remote.upsertFriend(updated.toDto(updatedAtEpochMs = now))
                        friendRepository.upsert(
                            updated.copy(remoteId = updated.remoteId ?: updated.id, syncStatus = SyncStatus.SYNCED),
                        )
                    }
                    return@runCatching AddPersonOutcome(
                        friend = updated,
                        inviteShareText = null,
                        isInvitePending = false,
                    )
                }

                val contactChanged = !normalized.equals(friend.emailSnapshot, ignoreCase = true)
                if (!contactChanged) {
                    val baseName = name.removeSuffix(" (invited)").trim().ifBlank { name }
                    val labeled =
                        if (baseName.contains("(invited)", ignoreCase = true)) {
                            baseName
                        } else {
                            "$baseName (invited)"
                        }
                    val updated =
                        friend.copy(
                            displayNameSnapshot = labeled,
                            updatedAtEpochMs = now,
                            syncStatus = SyncStatus.PENDING,
                        )
                    friendRepository.upsert(updated)
                    val existingInvite = inviteRepository.getByFriendRowId(friend.id)
                    val share =
                        if (existingInvite?.status == InviteStatus.PENDING) {
                            InviteLinks.friendShareText(
                                inviterName =
                                    userRepository.getUserById(ownerUserId)?.displayName ?: "A friend",
                                token = existingInvite.token,
                            )
                        } else {
                            null
                        }
                    return@runCatching AddPersonOutcome(
                        friend = updated,
                        inviteShareText = share,
                        isInvitePending = share != null,
                    )
                }

                inviteRepository.getByFriendRowId(friend.id)?.let { invite ->
                    if (invite.status == InviteStatus.PENDING) {
                        cancelInviteAndPush(invite)
                    }
                }
                friendRepository.deleteById(friend.id)
                runCatching { remote.deleteFriend(friend.remoteId ?: friend.id) }

                addFriendByContact(
                    ownerUserId = ownerUserId,
                    contact = normalized,
                    displayName = name.removeSuffix(" (invited)").trim().ifBlank { null },
                    groupId = null,
                ).getOrThrow()
            }

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
                // Friendship rows FK to users(ownerUserId). Rebuild the local owner profile
                // before writes so Review/Add friends never fails with raw FK errors.
                ensureLocalUserExists(ownerUserId)
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
                    // Do not auto-resend email here — Review retries and re-adds would
                    // duplicate mail. Explicit resend uses deliverPendingInvite().
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
                cancelInviteAndPush(invite)
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
                // Reuse a local pending invite placeholder instead of creating a duplicate.
                friendRepository.getByOwnerAndEmail(ownerUserId, normalized)?.let { existing ->
                    val pendingInvite = inviteRepository.getByFriendRowId(existing.id)
                    val isPlaceholder =
                        pendingInvite?.status == InviteStatus.PENDING ||
                            existing.displayNameSnapshot.contains("(invited)", ignoreCase = true)
                    if (isPlaceholder) {
                        // Same as addExistingFriendToGroup: if they already registered,
                        // promote the placeholder and add them as a real member.
                        val profile =
                            runCatching { remote.findProfileByEmail(normalized) }.getOrNull()
                        if (profile != null && profile.id != ownerUserId) {
                            if (pendingInvite != null) {
                                promotePendingInviteIfJoined(ownerUserId, pendingInvite)
                            }
                            addMemberToGroup(
                                groupId = groupId,
                                userId = profile.id,
                                actingUserId = ownerUserId,
                            ).getOrThrow()
                            val linked =
                                friendRepository.getByFriendUserId(profile.id)
                                    ?: existing.copy(friendUserId = profile.id)
                            return AddPersonOutcome(
                                friend = linked,
                                inviteShareText = null,
                                isInvitePending = false,
                            )
                        }
                        return ensureGroupInviteForPendingFriend(
                            ownerUserId = ownerUserId,
                            friend = existing,
                            groupId = groupId,
                        )
                    }
                    addMemberToGroup(
                        groupId = groupId,
                        userId = existing.friendUserId,
                        actingUserId = ownerUserId,
                    ).getOrThrow()
                    return AddPersonOutcome(
                        friend = existing,
                        inviteShareText = null,
                        isInvitePending = false,
                    )
                }

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
                    addMemberToGroup(groupId, profile.id, actingUserId = ownerUserId).getOrThrow()
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
         *
         * Does **not** hydrate Room — [com.splitease.app.data.sync.SyncInteractor.syncForUser]
         * always pulls friends/groups/expenses after this returns so we avoid a double fetch.
         *
         * @param _userId Newly authenticated user id (reserved for future scoped claims).
         * @param inviteToken Optional deep-link token to accept first (join-as-new).
         * @return True when there was no token, or the token invite is no longer pending
         *   (accepted / invalid). False when the token invite is still pending after claim.
         */
        suspend fun acceptPendingInvitesForCurrentUser(
            _userId: String,
            inviteToken: String? = null,
        ): Boolean {
            var acceptedByToken = false
            if (!inviteToken.isNullOrBlank()) {
                // Do not swallow — callers must know when the RPC fails so the token is kept.
                val accepted = remote.acceptInviteByToken(inviteToken)
                acceptedByToken = accepted > 0
            }
            runCatching { remote.acceptPendingInvites() }
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
         * Re-delivers a pending invite: emails the join link when the contact is an email,
         * otherwise returns share text for the system share sheet.
         *
         * @param friendRowId Local friendship row id linked to the invite.
         * @return Outcome with email-sent or share-text delivery, or null when no pending invite.
         */
        suspend fun deliverPendingInvite(friendRowId: String): AddPersonOutcome? {
            val invite = inviteRepository.getByFriendRowId(friendRowId) ?: return null
            if (invite.status != InviteStatus.PENDING) return null
            val friend = friendRepository.getById(friendRowId) ?: return null
            val inviterName =
                userRepository.getUserById(invite.inviterUserId)?.displayName ?: "A friend"
            val groupName =
                invite.groupId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { groupRepository.getGroupById(it)?.name ?: "a group" }
            val shareText =
                if (groupName != null) {
                    InviteLinks.groupShareText(inviterName, groupName, invite.token)
                } else {
                    InviteLinks.friendShareText(inviterName, invite.token)
                }
            val emailSent =
                trySendInviteEmail(
                    toEmail = invite.email,
                    inviterName = inviterName,
                    groupName = groupName,
                    token = invite.token,
                )
            return AddPersonOutcome(
                friend = friend,
                inviteShareText = if (emailSent) null else shareText,
                isInvitePending = true,
                inviteEmailSent = emailSent,
            )
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
            dto.groupPhotoUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { photoUrl ->
                    withContext(Dispatchers.IO) {
                        AvatarImageIO.cacheRemoteImage(appContext, photoUrl)
                    }
                }
            // Generic group share links store a non-user placeholder so email-based
            // accept cannot burn the token; never prefill that into signup.
            val previewEmail =
                dto.email.trim().takeUnless {
                    it.equals(GROUP_SHARE_LINK_EMAIL, ignoreCase = true) ||
                        it.endsWith("@splitease.invalid", ignoreCase = true)
                }.orEmpty()
            return InvitePreview(
                token = dto.token,
                kind = runCatching { InviteKind.valueOf(dto.kind) }.getOrDefault(InviteKind.FRIEND),
                email = previewEmail,
                inviterName = dto.inviterName.ifBlank { "A friend" },
                groupId = dto.groupId,
                groupName = dto.groupName,
                groupPhotoUrl = dto.groupPhotoUrl?.trim()?.takeIf { it.isNotEmpty() },
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
                    remapPlaceholderAcrossDevices(previous.friendUserId, dto.friendUserId)
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
                val remoteInvite = dto.toDomain()
                val local = inviteRepository.getByToken(remoteInvite.token)
                // Do not resurrect a locally cancelled invite that still needs flush.
                if (
                    local != null &&
                    local.status == InviteStatus.CANCELLED &&
                    local.syncStatus == SyncStatus.PENDING
                ) {
                    return@forEach
                }
                inviteRepository.upsert(remoteInvite)
            }
            reconcileJoinedInvitees(inviterUserId)
        }

        /**
         * Inviter-side heal when someone joined but the local/cloud friendship is still
         * stuck as "(invited)" / PENDING (accept RPC missed or never ran).
         *
         * For each PENDING person invite whose email now has a profile, remaps the
         * friendship onto that user (Room + remote splits) and marks the invite ACCEPTED.
         * Also strips a stale "(invited)" label when the invite is already ACCEPTED and
         * re-pushes expenses so remote splits match any prior local-only remap.
         */
        suspend fun reconcileJoinedInvitees(ownerUserId: String) {
            val sent = remote.fetchInvitesSentBy(ownerUserId).map { it.toDomain() }
            sent.forEach { invite -> inviteRepository.upsert(invite) }

            for (invite in sent) {
                val friendRowId = invite.friendRowId ?: continue
                when (invite.status) {
                    InviteStatus.ACCEPTED ->
                        healAcceptedInvite(ownerUserId, friendRowId)
                    InviteStatus.PENDING ->
                        promotePendingInviteIfJoined(ownerUserId, invite)
                    else -> Unit
                }
            }
        }

        private suspend fun promotePendingInviteIfJoined(
            ownerUserId: String,
            invite: Invite,
        ) {
            val friendRowId = invite.friendRowId ?: return
            val contact = invite.email.trim()
            if (!contact.contains("@")) return
            val profile =
                runCatching { remote.findProfileByEmail(contact) }.getOrNull() ?: return
            if (profile.id == ownerUserId) return

            val friend = friendRepository.getById(friendRowId) ?: return
            val now = System.currentTimeMillis()
            val cleanedName =
                profile.displayName
                    .trim()
                    .removeSuffix(" (invited)")
                    .trim()
                    .ifBlank {
                        friend.displayNameSnapshot
                            .removeSuffix(" (invited)")
                            .trim()
                    }.ifBlank { contact.substringBefore("@") }

            // Remap Room + remote BEFORE updating friends / burning the invite, so
            // accept_pending_invites does not lose the placeholder id.
            val placeholderId = friend.friendUserId
            if (placeholderId != profile.id) {
                remapPlaceholderAcrossDevices(placeholderId, profile.id)
            }

            val existingForProfile =
                friendRepository
                    .observeFriends(ownerUserId)
                    .first()
                    .firstOrNull { it.friendUserId == profile.id && it.id != friend.id }

            if (existingForProfile != null) {
                runCatching { remote.deleteFriend(friend.id) }
                friendRepository.deleteById(friend.id)
            } else {
                ensureLocalUserExists(
                    userId = profile.id,
                    email = profile.email.ifBlank { contact },
                    displayName = cleanedName,
                )
                val updated =
                    friend.copy(
                        friendUserId = profile.id,
                        emailSnapshot = profile.email.ifBlank { friend.emailSnapshot },
                        displayNameSnapshot = cleanedName,
                        updatedAtEpochMs = now,
                        syncStatus = SyncStatus.SYNCED,
                        remoteId = friend.remoteId ?: friend.id,
                    )
                runCatching { remote.upsertFriend(updated.toDto(updatedAtEpochMs = now)) }
                friendRepository.upsert(updated)
            }

            if (invite.kind == InviteKind.GROUP && !invite.groupId.isNullOrBlank()) {
                val groupId = invite.groupId
                val existingMember = groupRepository.getMember(groupId, profile.id)
                if (existingMember == null) {
                    // PENDING until cloud upsert succeeds so flushPending can retry.
                    val member =
                        GroupMember(
                            id = UUID.randomUUID().toString(),
                            groupId = groupId,
                            userId = profile.id,
                            role = MemberRole.MEMBER,
                            joinedAtEpochMs = now,
                            syncStatus = SyncStatus.PENDING,
                        )
                    groupRepository.upsertMember(member)
                    runCatching {
                        ensureGroupSyncedToCloud(groupId, ownerUserId)
                        remote.upsertGroupMember(member.toDto())
                        groupRepository.upsertMember(member.copy(syncStatus = SyncStatus.SYNCED))
                    }
                } else if (
                    existingMember.syncStatus != SyncStatus.SYNCED &&
                    existingMember.syncStatus != SyncStatus.LOCAL_ONLY
                ) {
                    runCatching {
                        ensureGroupSyncedToCloud(groupId, ownerUserId)
                        remote.upsertGroupMember(existingMember.toDto())
                        groupRepository.upsertMember(
                            existingMember.copy(syncStatus = SyncStatus.SYNCED),
                        )
                    }
                }
            }

            ensureReciprocalWithInviter(
                inviteeUserId = profile.id,
                inviterUserId = ownerUserId,
            )

            val accepted =
                invite.copy(
                    status = InviteStatus.ACCEPTED,
                    email = profile.email.ifBlank { invite.email },
                    syncStatus = SyncStatus.SYNCED,
                )
            runCatching { remote.upsertInvite(accepted.toDto()) }
            inviteRepository.upsert(accepted)
        }

        private suspend fun healAcceptedInvite(
            ownerUserId: String,
            friendRowId: String,
        ) {
            stripStaleInvitedLabel(friendRowId)
            val friend = friendRepository.getById(friendRowId) ?: return
            // Prior builds remapped Room only; re-push so remote splits match.
            runCatching { expenseInteractor.republishExpensesInvolving(friend.friendUserId) }
            ensureReciprocalWithInviter(
                inviteeUserId = friend.friendUserId,
                inviterUserId = ownerUserId,
            )
        }

        private suspend fun stripStaleInvitedLabel(friendRowId: String) {
            val friend = friendRepository.getById(friendRowId) ?: return
            if (!friend.displayNameSnapshot.contains("(invited)", ignoreCase = true)) return
            val cleaned =
                friend.displayNameSnapshot
                    .replace(Regex("\\s*\\(invited\\)\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { friend.emailSnapshot.substringBefore("@").ifBlank { "Friend" } }
            val now = System.currentTimeMillis()
            val updated =
                friend.copy(
                    displayNameSnapshot = cleaned,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.SYNCED,
                )
            runCatching { remote.upsertFriend(updated.toDto(updatedAtEpochMs = now)) }
            friendRepository.upsert(updated)
        }

        /**
         * Remaps placeholder → real user in Room and on Supabase, then re-pushes expenses.
         */
        private suspend fun remapPlaceholderAcrossDevices(
            fromUserId: String,
            toUserId: String,
        ) {
            if (fromUserId.isBlank() || toUserId.isBlank() || fromUserId == toUserId) return
            expenseRepository.remapUserId(fromUserId, toUserId)
            groupRepository.remapMemberUserId(fromUserId, toUserId)
            runCatching { remote.remapPlaceholderUser(fromUserId, toUserId) }
            runCatching { expenseInteractor.republishExpensesInvolving(toUserId) }
        }

        /**
         * Creates the invitee→inviter friendship edge so both Friends lists stay in sync.
         */
        private suspend fun ensureReciprocalWithInviter(
            inviteeUserId: String,
            inviterUserId: String,
        ) {
            if (inviteeUserId.isBlank() || inviterUserId.isBlank() || inviteeUserId == inviterUserId) {
                return
            }
            val inviter = userRepository.getUserById(inviterUserId)
            val email = inviter?.email.orEmpty()
            val name =
                inviter?.displayName?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@").ifBlank { "Friend" }
            runCatching {
                remote.ensureReciprocalFriend(
                    ownerUserId = inviteeUserId,
                    friendUserId = inviterUserId,
                    email = email,
                    displayName = name,
                )
            }
        }

        /**
         * After expenses/groups hydrate, create missing friend rows for counterparties
         * so the invitee can open Friend detail even if reciprocal RPC was unavailable.
         *
         * @param userId Signed-in user id.
         */
        suspend fun ensureFriendsFromSharedActivity(userId: String) {
            val known =
                friendRepository
                    .observeFriends(userId)
                    .first()
                    .map { it.friendUserId }
                    .toMutableSet()
            val counterparties = linkedSetOf<String>()

            val expenses = expenseRepository.observeInvolvingUser(userId).first()
            val splits = expenseRepository.getSplitsForExpenses(expenses.map { it.id })
            expenses.forEach { expense ->
                (splits[expense.id].orEmpty().map { it.userId } + expense.paidByUserId)
                    .filter { it.isNotBlank() && it != userId && it !in known }
                    .forEach { counterparties += it }
            }

            groupRepository.observeGroupsForUser(userId).first().forEach { group ->
                groupRepository.observeMembers(group.id).first().forEach { member ->
                    val otherId = member.userId
                    if (otherId.isNotBlank() && otherId != userId && otherId !in known) {
                        counterparties += otherId
                    }
                }
            }

            counterparties.forEach { otherId ->
                val profile =
                    runCatching { remote.fetchProfileById(otherId) }.getOrNull() ?: return@forEach
                runCatching {
                    linkExistingFriend(
                        ownerUserId = userId,
                        friendUserId = profile.id,
                        email = profile.email,
                        displayName = profile.displayName,
                        ensureReciprocal = false,
                    )
                }
                known += otherId
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
            photoUri: String? = null,
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
                val photoUrl =
                    photoUri
                        ?.takeIf { it.isNotBlank() }
                        ?.let { persistGroupPhoto(groupId, it) }
                val group =
                    Group(
                        id = groupId,
                        name = name.trim(),
                        defaultCurrencyCode = AppCurrencies.normalizeOrDefault(currencyCode),
                        groupType = groupType,
                        photoUrl = photoUrl,
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
                        // RLS: created_by_user_id must equal auth.uid().
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

                val local = groupRepository.getGroupById(groupId) ?: group
                if (cloudError == null && !local.photoUrl.isNullOrBlank()) {
                    runCatching { ensurePhotoUploaded(local) }
                        .onSuccess { uploaded ->
                            if (uploaded.photoUrl != local.photoUrl) {
                                groupRepository.upsertGroup(
                                    uploaded.copy(remoteId = uploaded.id, syncStatus = SyncStatus.SYNCED),
                                )
                            }
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
         * Adds an existing friend list entry into a group from Find people.
         *
         * Registered friends are written to Supabase `group_members`. Pending invite
         * friends get a GROUP invite (so accept_pending_invites can join them) instead of
         * a doomed cloud membership with a placeholder user id.
         *
         * @param ownerUserId Current signed-in user.
         * @param groupId Target group.
         * @param friendUserId Friend's user id (or invite placeholder).
         * @return Outcome with optional share text when an invite is still pending.
         */
        suspend fun addExistingFriendToGroup(
            ownerUserId: String,
            groupId: String,
            friendUserId: String,
        ): Result<AddPersonOutcome> =
            runCatching {
                val friend =
                    friendRepository
                        .getByOwnerAndFriendUserId(ownerUserId, friendUserId)
                        ?: error("Friend not found.")
                val pendingInvite = inviteRepository.getByFriendRowId(friend.id)
                val isPending =
                    pendingInvite?.status == InviteStatus.PENDING ||
                        friend.displayNameSnapshot.contains("(invited)", ignoreCase = true)

                if (isPending) {
                    val email = friend.emailSnapshot.trim()
                    if (email.contains("@")) {
                        val profile = runCatching { remote.findProfileByEmail(email) }.getOrNull()
                        if (profile != null && profile.id != ownerUserId) {
                            if (pendingInvite != null) {
                                promotePendingInviteIfJoined(ownerUserId, pendingInvite)
                            }
                            addMemberToGroup(
                                groupId = groupId,
                                userId = profile.id,
                                actingUserId = ownerUserId,
                            ).getOrThrow()
                            val linked =
                                friendRepository.getByFriendUserId(profile.id)
                                    ?: friend.copy(friendUserId = profile.id)
                            return@runCatching AddPersonOutcome(
                                friend = linked,
                                inviteShareText = null,
                                isInvitePending = false,
                            )
                        }
                    }
                    return@runCatching ensureGroupInviteForPendingFriend(
                        ownerUserId = ownerUserId,
                        friend = friend,
                        groupId = groupId,
                    )
                }

                addMemberToGroup(
                    groupId = groupId,
                    userId = friendUserId,
                    actingUserId = ownerUserId,
                ).getOrThrow()
                AddPersonOutcome(
                    friend = friend,
                    inviteShareText = null,
                    isInvitePending = false,
                )
            }

        /**
         * Adds a friend as a group member (must be a real auth user id, not an invite placeholder).
         *
         * Ensures the group exists on Supabase first, then upserts membership. Cloud failures
         * are returned to the caller (local PENDING is kept for retry).
         *
         * @param groupId Group id.
         * @param userId Member user id.
         * @param actingUserId Signed-in user performing the add (for group cloud sync / RLS).
         */
        suspend fun addMemberToGroup(
            groupId: String,
            userId: String,
            actingUserId: String? = null,
        ): Result<Unit> =
            runCatching {
                val group =
                    groupRepository.getGroupById(groupId)
                        ?: error("Group not found.")
                val actor = actingUserId?.takeIf { it.isNotBlank() } ?: group.createdByUserId

                val friend = friendRepository.getByFriendUserId(userId)
                val pendingInvite = friend?.let { inviteRepository.getByFriendRowId(it.id) }
                val isPlaceholder =
                    pendingInvite?.status == InviteStatus.PENDING ||
                        friend?.displayNameSnapshot?.contains("(invited)", ignoreCase = true) == true
                if (isPlaceholder) {
                    error(
                        "This person hasn't joined SplitEase yet. Send them a group invite instead.",
                    )
                }

                ensureLocalUserExists(
                    userId = userId,
                    email = friend?.emailSnapshot.orEmpty(),
                    displayName =
                        friend?.displayNameSnapshot?.takeIf { it.isNotBlank() } ?: "Member",
                )
                ensureGroupSyncedToCloud(groupId, actor)

                val existing = groupRepository.getMember(groupId, userId)
                if (existing != null) {
                    if (existing.syncStatus != SyncStatus.SYNCED) {
                        remote.upsertGroupMember(existing.toDto())
                        groupRepository.upsertMember(existing.copy(syncStatus = SyncStatus.SYNCED))
                    }
                    return@runCatching
                }

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
                remote.upsertGroupMember(member.toDto())
                groupRepository.upsertMember(member.copy(syncStatus = SyncStatus.SYNCED))
            }

        /**
         * Upgrades / creates a GROUP invite for a pending friend so accept_pending_invites
         * can add them when they sign up. Keeps a LOCAL_ONLY placeholder membership for UI.
         */
        private suspend fun ensureGroupInviteForPendingFriend(
            ownerUserId: String,
            friend: Friend,
            groupId: String,
        ): AddPersonOutcome {
            val group =
                groupRepository.getGroupById(groupId)
                    ?: error("Group not found.")
            val email = friend.emailSnapshot.trim()
            require(email.contains("@")) {
                "This invite needs an email address before they can join the group."
            }

            val existingInvite = inviteRepository.getByFriendRowId(friend.id)
            var shouldSendEmail = true
            val invite =
                when (existingInvite?.status) {
                    InviteStatus.PENDING -> {
                        if (
                            existingInvite.kind == InviteKind.GROUP &&
                            existingInvite.groupId == groupId
                        ) {
                            if (existingInvite.syncStatus != SyncStatus.SYNCED) {
                                pushInviteToCloud(existingInvite)
                            } else {
                                // Already delivered for this group — avoid duplicate emails.
                                shouldSendEmail = false
                            }
                            existingInvite
                        } else {
                            val upgraded =
                                existingInvite.copy(
                                    kind = InviteKind.GROUP,
                                    groupId = groupId,
                                    syncStatus = SyncStatus.PENDING,
                                )
                            inviteRepository.upsert(upgraded)
                            pushInviteToCloud(upgraded)
                            upgraded
                        }
                    }
                    else -> {
                        val created =
                            buildPendingInvite(
                                inviterUserId = ownerUserId,
                                email = email,
                                kind = InviteKind.GROUP,
                                groupId = groupId,
                                friendRowId = friend.id,
                            )
                        inviteRepository.upsert(created)
                        pushInviteToCloud(created)
                        created
                    }
                }

            if (groupRepository.getMember(groupId, friend.friendUserId) == null) {
                groupRepository.upsertMember(
                    GroupMember(
                        id = UUID.randomUUID().toString(),
                        groupId = groupId,
                        userId = friend.friendUserId,
                        role = MemberRole.MEMBER,
                        joinedAtEpochMs = System.currentTimeMillis(),
                        syncStatus = SyncStatus.LOCAL_ONLY,
                    ),
                )
            }

            val inviterName =
                userRepository.getUserById(ownerUserId)?.displayName ?: "A friend"
            val shareText = InviteLinks.groupShareText(inviterName, group.name, invite.token)
            val emailSent =
                if (shouldSendEmail) {
                    trySendInviteEmail(
                        toEmail = email,
                        inviterName = inviterName,
                        groupName = group.name,
                        token = invite.token,
                    )
                } else {
                    false
                }
            return AddPersonOutcome(
                friend = friend,
                // Already-synced reuse must not look like a failed email (share sheet).
                inviteShareText = if (emailSent || !shouldSendEmail) null else shareText,
                isInvitePending = true,
                inviteEmailSent = emailSent,
            )
        }

        /**
         * Removes the current user from a group. If they are the last member, dissolves the group
         * (even when they are not the original creator).
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
                    // Last remaining member may not be createdByUserId after the owner left.
                    purgeAndDeleteGroup(groupId)
                    return@runCatching
                }
                groupRepository.deleteMemberById(member.id)
                runCatching { remote.deleteGroupMember(member.id) }
            }

        /**
         * Removes another member from a group. Does not delete the friendship.
         *
         * @param groupId Group id.
         * @param requesterId Member performing the removal.
         * @param targetUserId Member to remove (must not be [requesterId]).
         */
        suspend fun removeGroupMember(
            groupId: String,
            requesterId: String,
            targetUserId: String,
        ): Result<Unit> =
            runCatching {
                require(requesterId != targetUserId) {
                    "Use leave group to remove yourself."
                }
                groupRepository.getMember(groupId, requesterId)
                    ?: error("You are not a member of this group.")
                val target =
                    groupRepository.getMember(groupId, targetUserId)
                        ?: error("That person is not in this group.")
                val members = groupRepository.observeMembers(groupId).first()
                if (members.size <= 1) {
                    error("Cannot remove the last member of a group.")
                }
                groupRepository.deleteMemberById(target.id)
                runCatching { remote.deleteGroupMember(target.id) }
                detachPendingGroupInvite(groupId, targetUserId)
            }

        /**
         * Downgrades a per-person GROUP invite to FRIEND so removal from a group does not
         * leave a claimable group link or allow silent re-add via Find people.
         */
        private suspend fun detachPendingGroupInvite(
            groupId: String,
            targetUserId: String,
        ) {
            val friend = friendRepository.getByFriendUserId(targetUserId) ?: return
            val invite = inviteRepository.getByFriendRowId(friend.id) ?: return
            if (
                invite.status != InviteStatus.PENDING ||
                invite.kind != InviteKind.GROUP ||
                invite.groupId != groupId
            ) {
                return
            }
            val downgraded =
                invite.copy(
                    kind = InviteKind.FRIEND,
                    groupId = null,
                    syncStatus = SyncStatus.PENDING,
                )
            inviteRepository.upsert(downgraded)
            runCatching {
                remote.upsertInvite(downgraded.toDto())
                inviteRepository.upsert(downgraded.copy(syncStatus = SyncStatus.SYNCED))
            }
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
                purgeAndDeleteGroup(groupId)
            }

        /**
         * Removes the group from cloud (best-effort), then purges media and local rows.
         * Cloud delete runs first so a failed remote delete does not leave other members
         * with a live group whose Storage objects were already wiped.
         */
        private suspend fun purgeAndDeleteGroup(groupId: String) {
            val group =
                groupRepository.getGroupById(groupId)
                    ?: error("Group not found.")
            runCatching { remote.deleteGroup(groupId) }
            val expenses = expenseRepository.observeExpenses(groupId).first()
            expenses.forEach { expense ->
                expenseInteractor.purgeExpenseMedia(expense.id)
            }
            val pinBoardContent =
                runCatching { pinBoardInteractor.load(groupId).content }.getOrNull()
            mediaStorageCleanup.purgeGroupMedia(group, pinBoardContent)
            groupRepository.deleteGroupById(groupId)
        }

        /** Cancels an invite locally and pushes CANCELLED so the token cannot be claimed. */
        private suspend fun cancelInviteAndPush(invite: Invite) {
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

        /**
         * Fast Home paint: pulls group rows + the current user's memberships only.
         *
         * Skips per-group member lists and profile hydration (those stay in [refreshGroups]
         * / full sync). Enough for [GroupRepository.observeGroupsForUser] to show the list.
         *
         * @param userId Current user id.
         */
        suspend fun refreshGroupList(userId: String) {
            val memberships =
                runCatching { remote.fetchMembershipsForUser(userId) }.getOrElse { return }
            val created =
                runCatching { remote.fetchGroupsCreatedBy(userId) }.getOrDefault(emptyList())
            val createdById = created.associateBy { it.id }
            val groupIds = (memberships.map { it.groupId } + created.map { it.id }).distinct()
            if (groupIds.isEmpty()) {
                // Still persist memberships if any were returned without group ids (defensive).
                return
            }

            // Current user must exist for membership FK (already true after login persist).
            ensureLocalUserExists(userId)

            coroutineScope {
                groupIds
                    .map { groupId ->
                        async {
                            val dto =
                                createdById[groupId]
                                    ?: runCatching { remote.fetchGroup(groupId) }.getOrNull()
                                    ?: return@async
                            val existing = groupRepository.getGroupById(dto.id)
                            groupRepository.upsertGroup(
                                Group(
                                    id = dto.id,
                                    name = dto.name,
                                    defaultCurrencyCode = dto.defaultCurrencyCode,
                                    groupType =
                                        existing?.groupType
                                            ?: com.splitease.app.domain.model.GroupType.OTHER,
                                    photoUrl = resolvePhotoUrlForRefresh(existing?.photoUrl, dto.photoUrl),
                                    coverUrl = resolveCoverUrlForRefresh(existing?.coverUrl, dto.coverUrl),
                                    createdByUserId = dto.createdByUserId,
                                    remoteId = dto.id,
                                    createdAtEpochMs = existing?.createdAtEpochMs ?: dto.updatedAtEpochMs,
                                    updatedAtEpochMs = dto.updatedAtEpochMs,
                                    syncStatus = SyncStatus.SYNCED,
                                ),
                            )
                        }
                    }.awaitAll()
            }

            memberships.forEach { memberDto ->
                groupRepository.upsertMember(
                    GroupMember(
                        id = memberDto.id,
                        groupId = memberDto.groupId,
                        userId = memberDto.userId,
                        role =
                            runCatching { MemberRole.valueOf(memberDto.role) }
                                .getOrDefault(MemberRole.MEMBER),
                        joinedAtEpochMs = memberDto.joinedAtEpochMs,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
            // Owned groups must appear in observeGroupsForUser even if the memberships
            // query lagged — ensure a local membership row for the creator.
            val memberGroupIds = memberships.map { it.groupId }.toHashSet()
            created.forEach { dto ->
                if (dto.id in memberGroupIds) return@forEach
                if (groupRepository.getMember(dto.id, userId) != null) return@forEach
                groupRepository.upsertMember(
                    GroupMember(
                        id = UUID.randomUUID().toString(),
                        groupId = dto.id,
                        userId = userId,
                        role = MemberRole.OWNER,
                        joinedAtEpochMs = dto.updatedAtEpochMs,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }

        /**
         * Pulls remote groups/memberships for [userId] into Room.
         * No-ops when cloud tables are missing or the device is offline.
         *
         * Uses created-by rows when already fetched (skips a per-group GET) and
         * pulls each group in parallel to avoid sequential N+1 latency.
         *
         * @param userId Current user id.
         */
        suspend fun refreshGroups(userId: String) {
            val memberships =
                runCatching { remote.fetchMembershipsForUser(userId) }.getOrElse { return }
            val created =
                runCatching { remote.fetchGroupsCreatedBy(userId) }.getOrDefault(emptyList())
            val createdById = created.associateBy { it.id }
            val groupIds = (memberships.map { it.groupId } + created.map { it.id }).distinct()
            if (groupIds.isEmpty()) return

            coroutineScope {
                groupIds
                    .map { groupId ->
                        async {
                            val dto =
                                createdById[groupId]
                                    ?: runCatching { remote.fetchGroup(groupId) }.getOrNull()
                                    ?: return@async
                            val existing = groupRepository.getGroupById(dto.id)
                            groupRepository.upsertGroup(
                                Group(
                                    id = dto.id,
                                    name = dto.name,
                                    defaultCurrencyCode = dto.defaultCurrencyCode,
                                    groupType =
                                        existing?.groupType
                                            ?: com.splitease.app.domain.model.GroupType.OTHER,
                                    photoUrl = resolvePhotoUrlForRefresh(existing?.photoUrl, dto.photoUrl),
                                    coverUrl = resolveCoverUrlForRefresh(existing?.coverUrl, dto.coverUrl),
                                    createdByUserId = dto.createdByUserId,
                                    remoteId = dto.id,
                                    createdAtEpochMs = existing?.createdAtEpochMs ?: dto.updatedAtEpochMs,
                                    updatedAtEpochMs = dto.updatedAtEpochMs,
                                    syncStatus = SyncStatus.SYNCED,
                                ),
                            )
                            val memberDtos =
                                runCatching { remote.fetchGroupMembers(groupId) }
                                    .getOrDefault(emptyList())
                            upsertMembersAndProfiles(groupId, memberDtos)
                        }
                    }.awaitAll()
            }
        }

        /**
         * Re-fetches memberships and `profiles` (including photo URLs) for [groupId].
         *
         * Used when a group detail is opened so co-member avatar updates show without
         * waiting for a full app sync.
         */
        suspend fun refreshGroupMemberProfiles(groupId: String) {
            val memberDtos =
                runCatching { remote.fetchGroupMembers(groupId) }.getOrDefault(emptyList())
            upsertMembersAndProfiles(groupId, memberDtos)
        }

        private suspend fun upsertMembersAndProfiles(
            groupId: String,
            memberDtos: List<GroupMemberDto>,
        ) {
            if (memberDtos.isEmpty()) return
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
                        role =
                            runCatching { MemberRole.valueOf(memberDto.role) }
                                .getOrDefault(MemberRole.MEMBER),
                        joinedAtEpochMs = memberDto.joinedAtEpochMs,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
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
                    existing != null &&
                        existing.email.isNotBlank() &&
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
                        photoUrl = resolveUsablePhotoUrl(existing?.photoUrl, resolved.photoUrl),
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
            ensureReciprocal: Boolean = true,
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

            val existingByEmail = friendRepository.getByOwnerAndEmail(ownerUserId, email)
            val existingByUser =
                friendRepository
                    .observeFriends(ownerUserId)
                    .first()
                    .firstOrNull { it.friendUserId == friendUserId }
            val existing = existingByEmail ?: existingByUser
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
            if (ensureReciprocal) {
                ensureReciprocalWithInviter(
                    inviteeUserId = friendUserId,
                    inviterUserId = ownerUserId,
                )
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
            val emailSent =
                trySendInviteEmail(
                    toEmail = email,
                    inviterName = inviterName,
                    groupName = if (groupId != null) groupName ?: "a group" else null,
                    token = invite.token,
                )

            return AddPersonOutcome(
                friend = friend,
                inviteShareText = if (emailSent) null else shareText,
                isInvitePending = true,
                inviteEmailSent = emailSent,
            )
        }

        /**
         * Best-effort invite email via the mail service. Phone contacts and placeholder
         * share-link emails are skipped so the caller can fall back to the share sheet.
         *
         * @param toEmail Candidate recipient.
         * @param inviterName Display name of the sender.
         * @param groupName Group name for group invites; null for friend invites.
         * @param token Invite token.
         * @return True when the mail service accepted the send.
         */
        private suspend fun trySendInviteEmail(
            toEmail: String,
            inviterName: String,
            groupName: String?,
            token: String,
        ): Boolean {
            val normalized = toEmail.trim()
            if (!normalized.contains("@")) return false
            if (normalized.equals(GROUP_SHARE_LINK_EMAIL, ignoreCase = true)) return false
            if (normalized.endsWith("@splitease.invalid", ignoreCase = true)) return false
            return mailRepository
                .sendInviteEmail(
                    toEmail = normalized,
                    inviterName = inviterName,
                    groupName = groupName,
                    token = token,
                ).isSuccess
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
            invite.groupId?.let { ensureGroupSyncedToCloud(it, invite.inviterUserId) }
            remote.upsertInvite(invite.toDto())
            inviteRepository.upsert(invite.copy(syncStatus = SyncStatus.SYNCED))
        }

        /**
         * Ensures [groupId] exists in Supabase before writing a GROUP invite (FK-safe).
         *
         * Local `createdByUserId` can be stale after re-signup on the same device; RLS requires
         * `created_by_user_id = auth.uid()`, so unsynced local groups are re-attributed to
         * [currentUserId] before insert.
         *
         * @param groupId Local / remote group id.
         * @param currentUserId Signed-in user performing the invite sync.
         */
        private suspend fun ensureGroupSyncedToCloud(
            groupId: String,
            currentUserId: String,
        ) {
            val group = groupRepository.getGroupById(groupId) ?: return
            val now = System.currentTimeMillis()
            val remoteExisting = runCatching { remote.fetchGroup(groupId) }.getOrNull()
            if (remoteExisting != null) {
                groupRepository.upsertGroup(
                    group.copy(
                        remoteId = group.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAtEpochMs = now,
                    ),
                )
            } else {
                val toUpload =
                    if (group.createdByUserId == currentUserId) {
                        group
                    } else {
                        group
                            .copy(
                                createdByUserId = currentUserId,
                                syncStatus = SyncStatus.PENDING,
                                updatedAtEpochMs = now,
                            ).also { groupRepository.upsertGroup(it) }
                    }
                remote.upsertGroup(toUpload.toDto(updatedAtEpochMs = now))
                groupRepository.upsertGroup(
                    toUpload.copy(
                        remoteId = toUpload.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAtEpochMs = now,
                    ),
                )
            }

            var membership = groupRepository.getMember(groupId, currentUserId)
            if (membership == null) {
                membership =
                    GroupMember(
                        id = UUID.randomUUID().toString(),
                        groupId = groupId,
                        userId = currentUserId,
                        role = MemberRole.OWNER,
                        joinedAtEpochMs = now,
                        syncStatus = SyncStatus.PENDING,
                    )
                groupRepository.upsertMember(membership)
            }
            if (membership.syncStatus != SyncStatus.SYNCED) {
                remote.upsertGroupMember(membership.toDto())
                groupRepository.upsertMember(membership.copy(syncStatus = SyncStatus.SYNCED))
            }
        }

        /**
         * Copies [photoUri] into app-private storage, uploads to Supabase Storage, and stores
         * the public URL on the group so all members see the same list avatar.
         */
        suspend fun updateGroupPhoto(
            groupId: String,
            photoUri: String,
        ): Result<Unit> =
            runCatching {
                val group =
                    groupRepository.getGroupById(groupId)
                        ?: error("Group not found.")
                val localPath = persistGroupPhoto(groupId, photoUri)
                val remoteUrl =
                    runCatching { groupCoverStorage.uploadPhoto(groupId, localPath) }
                        .getOrElse { err ->
                            // Keep the local photo for this device; retry upload on next sync.
                            updateGroup(group.copy(photoUrl = localPath)).getOrThrow()
                            throw err
                        }
                runCatching {
                    AvatarImageIO.seedRemoteImageCache(appContext, remoteUrl, File(localPath))
                }
                val now = System.currentTimeMillis()
                val updated = group.copy(photoUrl = remoteUrl, updatedAtEpochMs = now)
                groupRepository.upsertGroup(updated.copy(syncStatus = SyncStatus.PENDING))
                runCatching {
                    remote.patchGroupPhotoUrl(groupId, remoteUrl, now)
                    remote.upsertGroup(updated.toDto(updatedAtEpochMs = now))
                    groupRepository.upsertGroup(
                        updated.copy(remoteId = updated.id, syncStatus = SyncStatus.SYNCED),
                    )
                }.getOrElse {
                    groupRepository.upsertGroup(updated.copy(syncStatus = SyncStatus.PENDING))
                }
            }

        /**
         * Copies a cropped cover [coverUri] into app-private storage, uploads to Supabase
         * Storage, and stores the public URL on the group so all members see the same banner.
         */
        suspend fun updateGroupCover(
            groupId: String,
            coverUri: String,
        ): Result<Unit> =
            runCatching {
                val group =
                    groupRepository.getGroupById(groupId)
                        ?: error("Group not found.")
                val localPath = persistGroupCover(groupId, coverUri)
                val remoteUrl =
                    runCatching { groupCoverStorage.uploadCover(groupId, localPath) }
                        .getOrElse { err ->
                            // Keep the local cover for this device; retry upload on next sync.
                            updateGroup(group.copy(coverUrl = localPath)).getOrThrow()
                            throw err
                        }
                // Seed disk cache so the banner paints immediately without a network round-trip.
                runCatching {
                    AvatarImageIO.seedRemoteImageCache(appContext, remoteUrl, File(localPath))
                }
                val now = System.currentTimeMillis()
                val updated = group.copy(coverUrl = remoteUrl, updatedAtEpochMs = now)
                groupRepository.upsertGroup(updated.copy(syncStatus = SyncStatus.PENDING))
                runCatching {
                    remote.patchGroupCoverUrl(groupId, remoteUrl, now)
                    remote.upsertGroup(updated.toDto(updatedAtEpochMs = now))
                    groupRepository.upsertGroup(
                        updated.copy(remoteId = updated.id, syncStatus = SyncStatus.SYNCED),
                    )
                }.getOrElse {
                    // Cover bytes are in Storage; Room keeps the public URL for display/retry.
                    groupRepository.upsertGroup(updated.copy(syncStatus = SyncStatus.PENDING))
                }
            }

        /**
         * Clears [Group.coverUrl], deletes Storage + local cover files, and patches PostgREST.
         */
        suspend fun removeGroupCover(groupId: String): Result<Unit> =
            runCatching {
                val group =
                    groupRepository.getGroupById(groupId)
                        ?: error("Group not found.")
                mediaStorageCleanup.evictRemoteMediaCache(group.coverUrl)
                deleteGroupCoverFiles(groupId)
                runCatching { groupCoverStorage.deleteCover(groupId) }
                val now = System.currentTimeMillis()
                val updated = group.copy(coverUrl = null, updatedAtEpochMs = now)
                groupRepository.upsertGroup(updated.copy(syncStatus = SyncStatus.PENDING))
                runCatching {
                    remote.patchGroupCoverUrl(groupId, coverUrl = null, updatedAtEpochMs = now)
                    groupRepository.upsertGroup(
                        updated.copy(remoteId = updated.id, syncStatus = SyncStatus.SYNCED),
                    )
                }
            }

        /**
         * Picks the cover URL after a cloud pull.
         * Prefers remote https; keeps a pending local-only cover when cloud has none.
         */
        private fun resolveCoverUrlForRefresh(
            existingCoverUrl: String?,
            remoteCoverUrl: String?,
        ): String? = resolveMediaUrlForRefresh(existingCoverUrl, remoteCoverUrl)

        /**
         * Picks the list photo URL after a cloud pull.
         * Prefers remote https; keeps a pending local-only photo when cloud has none.
         */
        private fun resolvePhotoUrlForRefresh(
            existingPhotoUrl: String?,
            remotePhotoUrl: String?,
        ): String? = resolveMediaUrlForRefresh(existingPhotoUrl, remotePhotoUrl)

        private fun resolveMediaUrlForRefresh(
            existingUrl: String?,
            remoteUrl: String?,
        ): String? {
            val remote = remoteUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (remote != null) {
                runCatching { AvatarImageIO.cacheRemoteImage(appContext, remote) }
                return remote
            }
            val existing = existingUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // Pending local file not yet uploaded — keep it. Drop stale remote URL if cloud cleared.
            return if (existing.isRemoteMediaUrl()) null else existing
        }

        /**
         * Profile photos may be a public URL or a device-local path. Ignore another
         * device's local path so a refresh does not wipe a photo that exists here.
         */
        private fun resolveUsablePhotoUrl(
            existing: String?,
            remote: String?,
        ): String? {
            val remoteTrimmed = remote?.trim()?.takeIf { it.isNotEmpty() }
            if (remoteTrimmed != null) {
                if (remoteTrimmed.isRemoteMediaUrl()) {
                    runCatching { AvatarImageIO.cacheRemoteImage(appContext, remoteTrimmed) }
                    return remoteTrimmed
                }
                if (isExistingLocalPhoto(remoteTrimmed)) return remoteTrimmed
            }
            val existingTrimmed = existing?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return existingTrimmed.takeIf { url ->
                url.isRemoteMediaUrl() || isExistingLocalPhoto(url)
            }
        }

        private fun isExistingLocalPhoto(path: String): Boolean {
            if (path.startsWith("content:", ignoreCase = true)) return true
            val filePath =
                if (path.startsWith("file:", ignoreCase = true)) {
                    android.net.Uri.parse(path).path
                } else {
                    path
                }
            return !filePath.isNullOrBlank() && File(filePath).isFile
        }

        /**
         * If [group] still has a local-only cover path, uploads it and returns the group with
         * the public URL. Used by sync flush so PENDING groups do not wipe cloud covers.
         */
        suspend fun ensureCoverUploaded(group: Group): Group {
            val cover = group.coverUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return group
            if (cover.isRemoteMediaUrl()) return group
            val local = File(cover)
            if (!local.exists()) return group.copy(coverUrl = null)
            val remoteUrl = groupCoverStorage.uploadCover(group.id, local.absolutePath)
            AvatarImageIO.seedRemoteImageCache(appContext, remoteUrl, local)
            val now = System.currentTimeMillis()
            runCatching { remote.patchGroupCoverUrl(group.id, remoteUrl, now) }
            return group.copy(coverUrl = remoteUrl, updatedAtEpochMs = now)
        }

        /**
         * If [group] still has a local-only list photo path, uploads it and returns the group
         * with the public URL. Used by sync flush / create so photos reach other devices.
         */
        suspend fun ensurePhotoUploaded(group: Group): Group {
            val photo = group.photoUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return group
            if (photo.isRemoteMediaUrl()) return group
            val local = File(photo)
            if (!local.exists()) return group.copy(photoUrl = null)
            val remoteUrl = groupCoverStorage.uploadPhoto(group.id, local.absolutePath)
            AvatarImageIO.seedRemoteImageCache(appContext, remoteUrl, local)
            val now = System.currentTimeMillis()
            runCatching { remote.patchGroupPhotoUrl(group.id, remoteUrl, now) }
            return group.copy(photoUrl = remoteUrl, updatedAtEpochMs = now)
        }

        private fun persistGroupPhoto(
            groupId: String,
            photoUri: String,
        ): String {
            val dir = File(appContext.filesDir, "group_photos").apply { mkdirs() }
            val dest = File(dir, "${groupId}_${System.currentTimeMillis()}.jpg")
            val path =
                AvatarImageIO.copyScaledJpeg(
                    context = appContext,
                    photoUri = photoUri,
                    destFile = dest,
                )
            pruneGroupMediaFiles(dir, groupId, keep = 2)
            return path
        }

        private fun persistGroupCover(
            groupId: String,
            coverUri: String,
        ): String {
            val dir = File(appContext.filesDir, "group_covers").apply { mkdirs() }
            val dest = File(dir, "${groupId}_${System.currentTimeMillis()}.jpg")
            val path =
                AvatarImageIO.copyScaledJpeg(
                    context = appContext,
                    photoUri = coverUri,
                    destFile = dest,
                    maxSidePx = AvatarImageIO.COVER_STORED_MAX_SIDE_PX,
                    quality = 82,
                )
            pruneGroupMediaFiles(dir, groupId, keep = 2)
            return path
        }

        private fun deleteGroupCoverFiles(groupId: String) {
            LocalMediaCleanup.deleteGroupCoverFiles(appContext, groupId)
        }

        private fun pruneGroupMediaFiles(
            dir: File,
            groupId: String,
            keep: Int,
        ) {
            when (dir.name) {
                "group_covers" ->
                    LocalMediaCleanup.pruneGroupCoverFiles(appContext, groupId, keepNewest = keep)
                "group_photos" ->
                    LocalMediaCleanup.pruneGroupPhotoFiles(appContext, groupId, keepNewest = keep)
                else -> {
                    if (!dir.isDirectory) return
                    dir
                        .listFiles()
                        ?.filter { file ->
                            file.isFile &&
                                (
                                    file.name.equals("$groupId.jpg", ignoreCase = true) ||
                                        (
                                            file.name.startsWith("${groupId}_") &&
                                                file.name.endsWith(".jpg", ignoreCase = true)
                                        )
                                )
                        }?.sortedByDescending { it.lastModified() }
                        ?.drop(keep.coerceAtLeast(0))
                        ?.forEach { it.delete() }
                }
            }
        }
    }