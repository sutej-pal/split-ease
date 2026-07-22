package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.PaymentDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
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
    }
