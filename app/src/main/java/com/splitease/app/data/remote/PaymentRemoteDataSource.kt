package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.PaymentDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
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
    }
