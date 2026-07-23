package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostgREST access for expenses and splits.
 */
@Singleton
class ExpenseRemoteDataSource
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Upserts an expense row.
         *
         * @param expense Expense DTO.
         */
        suspend fun upsertExpense(expense: ExpenseDto) {
            supabase.from("expenses").upsert(expense)
        }

        /**
         * Upserts split rows (caller deletes stale rows separately when editing).
         *
         * @param splits Split DTOs.
         */
        suspend fun upsertSplits(splits: List<ExpenseSplitDto>) {
            if (splits.isEmpty()) return
            supabase.from("expense_splits").upsert(splits)
        }

        /**
         * Deletes all splits for an expense.
         *
         * @param expenseId Expense id.
         */
        suspend fun deleteSplitsForExpense(expenseId: String) {
            supabase.from("expense_splits").delete {
                filter {
                    eq("expense_id", expenseId)
                }
            }
        }

        /**
         * Fetches expenses for a group.
         *
         * @param groupId Group id.
         * @return Expense rows.
         */
        suspend fun fetchByGroup(groupId: String): List<ExpenseDto> =
            supabase
                .from("expenses")
                .select(Columns.ALL) {
                    filter {
                        eq("group_id", groupId)
                    }
                }.decodeList()

        /**
         * Fetches expenses where [userId] is the payer (covers 1:1 pulls for the creator).
         *
         * @param userId Payer user id.
         * @return Expense rows.
         */
        suspend fun fetchPaidBy(userId: String): List<ExpenseDto> =
            supabase
                .from("expenses")
                .select(Columns.ALL) {
                    filter {
                        eq("paid_by_user_id", userId)
                    }
                }.decodeList()

        /**
         * Fetches expenses that include [userId] as a split participant.
         *
         * @param userId Participant user id.
         * @return Expense rows (may require a second query for parent rows).
         */
        suspend fun fetchSplitExpenseIdsForUser(userId: String): List<String> =
            supabase
                .from("expense_splits")
                .select(Columns.list("expense_id")) {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<ExpenseSplitExpenseIdDto>()
                .map { it.expenseId }
                .distinct()

        /**
         * Fetches a single expense by id.
         *
         * @param expenseId Expense id.
         * @return Expense or null.
         */
        suspend fun fetchExpense(expenseId: String): ExpenseDto? =
            supabase
                .from("expenses")
                .select(Columns.ALL) {
                    filter {
                        eq("id", expenseId)
                    }
                }.decodeList<ExpenseDto>()
                .firstOrNull()

        /**
         * Fetches splits for an expense.
         *
         * @param expenseId Expense id.
         * @return Split rows.
         */
        suspend fun fetchSplits(expenseId: String): List<ExpenseSplitDto> =
            supabase
                .from("expense_splits")
                .select(Columns.ALL) {
                    filter {
                        eq("expense_id", expenseId)
                    }
                }.decodeList()

        /**
         * Deletes an expense row (splits should be deleted first or via cascade).
         *
         * @param expenseId Expense id.
         */
        suspend fun deleteExpense(expenseId: String) {
            supabase.from("expenses").delete {
                filter {
                    eq("id", expenseId)
                }
            }
        }
    }

@kotlinx.serialization.Serializable
private data class ExpenseSplitExpenseIdDto(
    @kotlinx.serialization.SerialName("expense_id") val expenseId: String,
)
