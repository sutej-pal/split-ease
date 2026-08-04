package com.splitease.app.data.imports

import com.splitease.app.data.expense.CreateExpenseInput
import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.domain.imports.CsvTransactionParser
import com.splitease.app.domain.imports.ImportedTransaction
import com.splitease.app.domain.model.Category
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of importing CSV rows as expenses.
 *
 * @property imported Count created successfully.
 * @property failures Row error messages.
 */
data class ImportResult(
    val imported: Int,
    val failures: List<String>,
)

/**
 * Parses CSV and creates one-person equal-split expenses for the signed-in user.
 */
@Singleton
class TransactionImportInteractor
    @Inject
    constructor(
        private val expenseInteractor: ExpenseInteractor,
        private val categoryRepository: CategoryRepository,
    ) {
        /**
         * @param csv Raw CSV text.
         * @param viewerUserId Signed-in user (payer + sole participant).
         * @param defaultCurrency App currency fallback.
         */
        suspend fun importCsv(
            csv: String,
            viewerUserId: String,
            defaultCurrency: String,
        ): ImportResult {
            val rows = CsvTransactionParser.parse(csv, defaultCurrency)
            var imported = 0
            val failures = mutableListOf<String>()
            val categories = categoryRepository.observeCategories().first()
            rows.forEach { row ->
                runCatching {
                    val categoryId = resolveCategoryId(row, categories)
                    expenseInteractor
                        .createExpense(
                        CreateExpenseInput(
                            description = row.description,
                            amount = row.amount,
                            currencyCode = row.currencyCode ?: defaultCurrency,
                            paidByUserId = viewerUserId,
                            participantIds = listOf(viewerUserId),
                            splitType = SplitType.EQUAL,
                            categoryId = categoryId,
                            expenseDateEpochMs = row.dateEpochMs,
                            notes = "Imported",
                        ),
                    ).getOrThrow()
                    imported++
                }.onFailure { err ->
                    failures += "${row.description}: ${err.message ?: "failed"}"
                }
            }
            return ImportResult(imported = imported, failures = failures)
        }

        private suspend fun resolveCategoryId(
            row: ImportedTransaction,
            categories: List<Category>,
        ): String? {
            val name = row.categoryName?.trim()?.ifBlank { null } ?: return null
            categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id?.let { return it }
            val id = UUID.randomUUID().toString()
            categoryRepository.upsert(
                Category(
                    id = id,
                    name = name,
                    iconKey = "category_custom",
                    isDefault = false,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            )
            return id
        }
    }
