package com.splitease.app.data.sync

import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.data.payment.PaymentInteractor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscribes to Supabase Realtime postgres changes for a group's expenses and payments
 * while the group detail screen is visible, then refreshes Room from PostgREST.
 */
@Singleton
class GroupLiveSync
    @Inject
    constructor(
        private val supabase: SupabaseClient,
        private val expenseInteractor: ExpenseInteractor,
        private val paymentInteractor: PaymentInteractor,
    ) {
        private var channel: RealtimeChannel? = null
        private var jobs: Job? = null

        /**
         * Starts listening for cloud changes on [groupId]. Safe to call repeatedly;
         * previous subscriptions are torn down first.
         *
         * @param groupId Group being viewed.
         * @param scope Coroutine scope tied to the screen lifecycle (cancelled on leave).
         */
        suspend fun start(
            groupId: String,
            scope: CoroutineScope,
        ) {
            stop()
            val trimmed = groupId.trim()
            if (trimmed.isEmpty()) return

            val ch = supabase.channel("group-$trimmed")
            channel = ch

            val expenseFlow =
                ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "expenses"
                    filter("group_id", FilterOperator.EQ, trimmed)
                }
            val paymentFlow =
                ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "payments"
                    filter("group_id", FilterOperator.EQ, trimmed)
                }

            jobs =
                merge(expenseFlow, paymentFlow)
                    .debounce(DEBOUNCE_MS)
                    .onEach {
                        runCatching {
                            expenseInteractor.refreshGroupExpenses(trimmed)
                            paymentInteractor.refreshGroupPayments(trimmed)
                        }
                    }.launchIn(scope)

            runCatching { ch.subscribe() }
        }

        /**
         * Unsubscribes and cancels collect jobs.
         */
        suspend fun stop() {
            jobs?.cancel()
            jobs = null
            val ch = channel
            channel = null
            if (ch != null) {
                runCatching { supabase.realtime.removeChannel(ch) }
            }
        }

        companion object {
            private const val DEBOUNCE_MS = 400L
        }
    }
