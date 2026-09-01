package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.ExpenseCommentDto
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpensePhotoDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.data.sync.fetchCompleteInFilter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
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
        suspend fun fetchByGroup(groupId: String): List<ExpenseDto> = fetchByGroupIds(listOf(groupId))

        /**
         * Fetches expenses whose [group_id] is in [groupIds] (chunked `in.` filter).
         * Pages past PostgREST's per-SELECT row cap so one busy group cannot hide another.
         *
         * @param groupIds Group ids.
         * @return Expense rows (order not guaranteed).
         */
        suspend fun fetchByGroupIds(groupIds: List<String>): List<ExpenseDto> =
            selectByIn("expenses", "group_id", groupIds)

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
            fetchByIds(listOf(expenseId)).firstOrNull()

        /**
         * Fetches expenses whose id is in [expenseIds] (chunked `in.` filter).
         *
         * @param expenseIds Expense ids.
         * @return Expense rows (order not guaranteed).
         */
        suspend fun fetchByIds(expenseIds: List<String>): List<ExpenseDto> =
            selectByIn("expenses", "id", expenseIds)

        /**
         * Fetches splits for an expense.
         *
         * @param expenseId Expense id.
         * @return Split rows.
         */
        suspend fun fetchSplits(expenseId: String): List<ExpenseSplitDto> =
            fetchSplitsForExpenseIds(listOf(expenseId))

        /**
         * Fetches splits whose [expense_id] is in [expenseIds] (chunked `in.` filter).
         *
         * @param expenseIds Parent expense ids.
         * @return Split rows (order not guaranteed).
         */
        suspend fun fetchSplitsForExpenseIds(expenseIds: List<String>): List<ExpenseSplitDto> =
            selectByIn("expense_splits", "expense_id", expenseIds)

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

        /** Upserts an expense comment row. */
        suspend fun upsertComment(comment: ExpenseCommentDto) {
            supabase.from("expense_comments").upsert(comment)
        }

        /** Fetches comments for an expense, oldest-first. */
        suspend fun fetchComments(expenseId: String): List<ExpenseCommentDto> =
            fetchCommentsForExpenseIds(listOf(expenseId))

        /**
         * Fetches comments whose [expense_id] is in [expenseIds] (chunked `in.` filter).
         *
         * @param expenseIds Parent expense ids.
         * @return Comment rows (order not guaranteed).
         */
        suspend fun fetchCommentsForExpenseIds(expenseIds: List<String>): List<ExpenseCommentDto> =
            selectByIn("expense_comments", "expense_id", expenseIds)

        /** Upserts an expense photo metadata row. */
        suspend fun upsertPhoto(photo: ExpensePhotoDto) {
            supabase.from("expense_photos").upsert(photo)
        }

        /** Fetches photo metadata for an expense, oldest-first. */
        suspend fun fetchPhotos(expenseId: String): List<ExpensePhotoDto> =
            fetchPhotosForExpenseIds(listOf(expenseId))

        /**
         * Fetches photo metadata whose [expense_id] is in [expenseIds] (chunked `in.` filter).
         *
         * @param expenseIds Parent expense ids.
         * @return Photo rows (order not guaranteed).
         */
        suspend fun fetchPhotosForExpenseIds(expenseIds: List<String>): List<ExpensePhotoDto> =
            selectByIn("expense_photos", "expense_id", expenseIds)

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

@kotlinx.serialization.Serializable
private data class ExpenseSplitExpenseIdDto(
    @kotlinx.serialization.SerialName("expense_id") val expenseId: String,
)
