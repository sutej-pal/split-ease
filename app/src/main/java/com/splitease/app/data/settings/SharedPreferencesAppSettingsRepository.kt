package com.splitease.app.data.settings

import android.content.Context
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SharedPreferences]-backed [AppSettingsRepository].
 *
 * @property context Application context.
 */
@Singleton
class SharedPreferencesAppSettingsRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AppSettingsRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val currencyFlow = MutableStateFlow(readCurrency())

        override fun observeCurrencyCode(): Flow<String> = currencyFlow.asStateFlow()

        override suspend fun getCurrencyCode(): String =
            withContext(Dispatchers.IO) {
                readCurrency()
            }

        override suspend fun setCurrencyCode(code: String) {
            val normalized = code.trim().uppercase().ifBlank { AppCurrencies.DEFAULT }
            withContext(Dispatchers.IO) {
                prefs.edit().putString(KEY_CURRENCY, normalized).apply()
            }
            currencyFlow.value = normalized
        }

        private fun readCurrency(): String =
            prefs.getString(KEY_CURRENCY, AppCurrencies.DEFAULT)
                ?.trim()
                ?.uppercase()
                ?.ifBlank { AppCurrencies.DEFAULT }
                ?: AppCurrencies.DEFAULT

        companion object {
            private const val PREFS_NAME = "splitease_settings"
            private const val KEY_CURRENCY = "currency_code"
        }
    }
