package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.FriendDto
import com.splitease.app.data.remote.dto.GroupCoverUrlPatch
import com.splitease.app.data.remote.dto.GroupDto
import com.splitease.app.data.remote.dto.GroupMemberDto
import com.splitease.app.data.remote.dto.InviteDto
import com.splitease.app.data.remote.dto.InvitePreviewDto
import com.splitease.app.data.remote.dto.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin PostgREST access for profiles, friends, groups, and invites.
 *
 * @property supabase Shared Supabase client (Auth session attached automatically).
 */
@Singleton
class SocialRemoteDataSource
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Inserts or updates the caller's profile row.
         *
         * @param profile Profile to upsert.
         */
        suspend fun upsertProfile(profile: ProfileDto) {
            supabase.from("profiles").upsert(profile)
        }

        /**
         * Looks up a profile by email (case-insensitive match via exact stored email).
         *
         * @param email Email to find.
         * @return Matching profile, or null.
         */
        suspend fun findProfileByEmail(email: String): ProfileDto? =
            supabase
                .from("profiles")
                .select(Columns.ALL) {
                    filter {
                        ilike("email", email.trim())
                    }
                }.decodeList<ProfileDto>()
                .firstOrNull()

        /**
         * Fetches a single profile by user id.
         *
         * @param userId Profile / auth user id.
         * @return Matching profile, or null.
         */
        suspend fun fetchProfileById(userId: String): ProfileDto? =
            supabase
                .from("profiles")
                .select(Columns.ALL) {
                    filter {
                        eq("id", userId)
                    }
                }.decodeList<ProfileDto>()
                .firstOrNull()

        /**
         * Fetches profiles for the given user ids.
         *
         * @param userIds Profile / auth user ids.
         * @return Matching profile rows (order not guaranteed).
         */
        suspend fun fetchProfilesByIds(userIds: List<String>): List<ProfileDto> {
            val distinct = userIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (distinct.isEmpty()) return emptyList()
            return supabase
                .from("profiles")
                .select(Columns.ALL) {
                    filter {
                        isIn("id", distinct)
                    }
                }.decodeList()
        }

        /**
         * Upserts a friendship owned by the current user.
         *
         * @param friend Friend row.
         */
        suspend fun upsertFriend(friend: FriendDto) {
            supabase.from("friends").upsert(friend)
        }

        /**
         * Deletes a friendship by id.
         *
         * @param friendId Friend row id.
         */
        suspend fun deleteFriend(friendId: String) {
            supabase.from("friends").delete {
                filter {
                    eq("id", friendId)
                }
            }
        }

        /**
         * Fetches friendships for [ownerUserId].
         *
         * @param ownerUserId Owner user id.
         * @return Remote friend rows.
         */
        suspend fun fetchFriends(ownerUserId: String): List<FriendDto> =
            supabase
                .from("friends")
                .select(Columns.ALL) {
                    filter {
                        eq("owner_user_id", ownerUserId)
                    }
                }.decodeList()

        /**
         * Upserts a group row.
         *
         * @param group Group row.
         */
        suspend fun upsertGroup(group: GroupDto) {
            supabase.from("groups").upsert(group)
        }

        /**
         * Sets or clears `groups.cover_url` without rewriting other columns via full upsert.
         */
        suspend fun patchGroupCoverUrl(
            groupId: String,
            coverUrl: String?,
            updatedAtEpochMs: Long,
        ) {
            supabase.from("groups").update(
                GroupCoverUrlPatch(
                    coverUrl = coverUrl,
                    updatedAtEpochMs = updatedAtEpochMs,
                ),
            ) {
                filter {
                    eq("id", groupId)
                }
            }
        }

        /**
         * Deletes a group by id.
         *
         * @param groupId Group id.
         */
        suspend fun deleteGroup(groupId: String) {
            supabase.from("groups").delete {
                filter {
                    eq("id", groupId)
                }
            }
        }

        /**
         * Fetches groups created by [userId] (MVP pull; memberships synced separately).
         *
         * @param userId Creator user id.
         * @return Remote group rows.
         */
        suspend fun fetchGroupsCreatedBy(userId: String): List<GroupDto> =
            supabase
                .from("groups")
                .select(Columns.ALL) {
                    filter {
                        eq("created_by_user_id", userId)
                    }
                }.decodeList()

        /**
         * Upserts a group membership.
         *
         * @param member Member row.
         */
        suspend fun upsertGroupMember(member: GroupMemberDto) {
            supabase.from("group_members").upsert(member)
        }

        /**
         * Deletes a group membership by id.
         *
         * @param memberId Membership row id.
         */
        suspend fun deleteGroupMember(memberId: String) {
            supabase.from("group_members").delete {
                filter {
                    eq("id", memberId)
                }
            }
        }

        /**
         * Fetches members for a group.
         *
         * @param groupId Group id.
         * @return Member rows.
         */
        suspend fun fetchGroupMembers(groupId: String): List<GroupMemberDto> =
            supabase
                .from("group_members")
                .select(Columns.ALL) {
                    filter {
                        eq("group_id", groupId)
                    }
                }.decodeList()

        /**
         * Fetches memberships for a user (to discover groups).
         *
         * @param userId User id.
         * @return Membership rows.
         */
        suspend fun fetchMembershipsForUser(userId: String): List<GroupMemberDto> =
            supabase
                .from("group_members")
                .select(Columns.ALL) {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList()

        /**
         * Fetches a group by id.
         *
         * @param groupId Group id.
         * @return Group row or null.
         */
        suspend fun fetchGroup(groupId: String): GroupDto? =
            supabase
                .from("groups")
                .select(Columns.ALL) {
                    filter {
                        eq("id", groupId)
                    }
                }.decodeList<GroupDto>()
                .firstOrNull()

        /**
         * Upserts an invite row.
         *
         * @param invite Invite DTO.
         */
        suspend fun upsertInvite(invite: InviteDto) {
            supabase.from("invites").upsert(invite)
        }

        /**
         * Fetches invites sent by [inviterUserId].
         *
         * @param inviterUserId Inviter user id.
         * @return Remote invite rows.
         */
        suspend fun fetchInvitesSentBy(inviterUserId: String): List<InviteDto> =
            supabase
                .from("invites")
                .select(Columns.ALL) {
                    filter {
                        eq("inviter_user_id", inviterUserId)
                    }
                }.decodeList()

        /**
         * Claims pending invites addressed to the signed-in user's email
         * (links friendships and joins groups via SECURITY DEFINER RPC).
         */
        suspend fun acceptPendingInvites() {
            runCatching {
                supabase.postgrest.rpc("accept_pending_invites")
            }
        }

        /**
         * Remaps placeholder → real user ids on remote expenses/payments/members.
         *
         * @param fromUserId Invite placeholder UUID.
         * @param toUserId Real auth user id.
         */
        suspend fun remapPlaceholderUser(
            fromUserId: String,
            toUserId: String,
        ) {
            if (fromUserId.isBlank() || toUserId.isBlank() || fromUserId == toUserId) return
            supabase.postgrest.rpc(
                function = "remap_placeholder_user",
                parameters =
                    buildJsonObject {
                        put("p_from", fromUserId)
                        put("p_to", toUserId)
                    },
            )
        }

        /**
         * Ensures a friendship row owned by [ownerUserId] pointing at [friendUserId].
         *
         * Used so adding A→B also creates B→A (SECURITY DEFINER).
         *
         * @param ownerUserId Owner of the reverse edge (usually the invitee).
         * @param friendUserId The other party (usually the inviter).
         * @param email Friend email snapshot.
         * @param displayName Friend display name snapshot.
         */
        suspend fun ensureReciprocalFriend(
            ownerUserId: String,
            friendUserId: String,
            email: String,
            displayName: String,
        ) {
            if (ownerUserId.isBlank() || friendUserId.isBlank() || ownerUserId == friendUserId) return
            supabase.postgrest.rpc(
                function = "ensure_reciprocal_friend",
                parameters =
                    buildJsonObject {
                        put("p_owner_user_id", ownerUserId)
                        put("p_friend_user_id", friendUserId)
                        put("p_email", email)
                        put("p_display_name", displayName)
                    },
            )
        }

        /**
         * Loads a public invite preview by token (works for anonymous callers).
         *
         * @param token Opaque invite token from the deep link.
         * @return Preview DTO, or null when missing / already accepted.
         */
        suspend fun fetchInvitePreview(token: String): InvitePreviewDto? {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return null
            return runCatching {
                supabase.postgrest
                    .rpc(
                        function = "get_invite_preview",
                        parameters =
                            buildJsonObject {
                                put("p_token", trimmed)
                            },
                    ).decodeAs<InvitePreviewDto>()
            }.getOrNull()
        }

        /**
         * Claims a specific invite for the signed-in user by token.
         *
         * @param token Opaque invite token.
         */
        suspend fun acceptInviteByToken(token: String): Int {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return 0
            return supabase.postgrest
                .rpc(
                    function = "accept_invite_by_token",
                    parameters =
                        buildJsonObject {
                            put("p_token", trimmed)
                        },
                ).decodeAs<Int>()
        }
    }
