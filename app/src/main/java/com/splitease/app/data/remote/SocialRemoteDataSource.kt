package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.FriendDto
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
