package com.splitease.app.data.remote

import com.splitease.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches live exchange rates from [ExchangeRate-API](https://www.exchangerate-api.com/).
 */
@Singleton
class ExchangeRateCurrencyService @Inject constructor() {

    /**
     * Fetches the latest exchange rate from [from] currency to [to] currency.
     *
     * Uses `GET /v6/{key}/latest/{from}` and reads `conversion_rates[to]`.
     */
    suspend fun fetchRate(from: String, to: String): Result<BigDecimal> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fromUpper = from.trim().uppercase()
                val toUpper = to.trim().uppercase()

                if (fromUpper == toUpper) return@runCatching BigDecimal.ONE

                val apiKey = BuildConfig.EXCHANGE_RATE_API_KEY.trim()
                require(apiKey.isNotEmpty()) {
                    "Missing EXCHANGE_RATE_API_KEY in local.properties"
                }

                val url =
                    URL("https://v6.exchangerate-api.com/v6/$apiKey/latest/$fromUpper")
                val connection =
                    (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        setRequestProperty("Accept", "application/json")
                    }

                try {
                    val status = connection.responseCode
                    val body =
                        if (status in 200..299) {
                            connection.inputStream.bufferedReader().use { it.readText() }
                        } else {
                            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        }
                    if (status !in 200..299) {
                        throw IllegalStateException("Exchange rate API failed with status $status")
                    }

                    val json = JSONObject(body)
                    if (json.optString("result") != "success") {
                        throw IllegalStateException("Exchange rate API returned an error")
                    }

                    val rates = json.getJSONObject("conversion_rates")
                    if (!rates.has(toUpper)) {
                        throw IllegalStateException("No rate available for $toUpper")
                    }

                    BigDecimal(rates.getString(toUpper))
                } finally {
                    connection.disconnect()
                }
            }
        }
}
