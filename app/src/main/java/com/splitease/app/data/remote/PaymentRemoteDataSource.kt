package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.PaymentDto
import com.splitease.app.data.sync.fetchCompleteInFilter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostgREST access for settlement payments.
 */
@Singleton
class PaymentRemoteDataSource
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Upserts a payment row.
         *
         * @param payment Payment DTO.
         */
        suspend fun upsert(payment: PaymentDto) {
            supabase.from("payments").upsert(payment)
        }

        /**
         * Deletes a payment row.
         *
         * @param paymentId Payment id.
         */
        suspend fun delete(paymentId: String) {
            supabase.from("payments").delete {
                filter {
                    eq("id", paymentId)
                }
            }
        }

        /**
         * Fetches payments where [userId] is the payer or payee.
         *
         * @param userId Current user id.
         * @return Remote payment rows (deduped).
         */
        suspend fun fetchInvolvingUser(userId: String): List<PaymentDto> {
            val asFrom =
                supabase
                    .from("payments")
                    .select(Columns.ALL) {
                        filter {
                            eq("from_user_id", userId)
                        }
                    }.decodeList<PaymentDto>()
            val asTo =
                supabase
                    .from("payments")
                    .select(Columns.ALL) {
                        filter {
                            eq("to_user_id", userId)
                        }
                    }.decodeList<PaymentDto>()
            return (asFrom + asTo).distinctBy { it.id }
        }

        /**
         * Fetches payments for a group.
         *
         * @param groupId Group id.
         * @return Remote payment rows.
         */
        suspend fun fetchByGroup(groupId: String): List<PaymentDto> = fetchByGroupIds(listOf(groupId))

        /**
         * Fetches payments whose [group_id] is in [groupIds] (chunked `in.` filter).
         * Pages past PostgREST's per-SELECT row cap so one busy group cannot hide another.
         *
         * @param groupIds Group ids.
         * @return Payment rows (order not guaranteed).
         */
        suspend fun fetchByGroupIds(groupIds: List<String>): List<PaymentDto> =
            selectByIn("payments", "group_id", groupIds)

        private suspend inline fun <reified T : Any> selectByIn(
            table: String,
            column: String,
            ids: List<String>,
        ): List<T> =
            fetchCompleteInFilter(
                ids = ids,
                fetchPage = { chunk ->
                    supabase
                        .from(table)
                        .select(Columns.ALL) {
                            filter {
                                isIn(column, chunk)
                            }
                        }.decodeList()
                },
                fetchOffsetPage = { id, offset, limit ->
                    val to = offset + limit - 1
                    supabase
                        .from(table)
                        .select(Columns.ALL) {
                            filter {
                                eq(column, id)
                            }
                            order(column = "id", order = Order.ASCENDING)
                            range(offset.toLong()..to.toLong())
                        }.decodeList()
                },
            )
    }
