package com.splitease.app.data.remote

import com.splitease.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client for the external mail service endpoint.
 */
@Singleton
class MailRemoteDataSource
    @Inject
    constructor() {
        suspend fun sendMail(
            to: String,
            subject: String,
            text: String,
            fromName: String,
        ) = withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.MAIL_SERVICE_BASE_URL.trim().trimEnd('/')
            require(baseUrl.isNotEmpty()) { "MAIL_SERVICE_BASE_URL is missing." }

            val endpoint = URL("$baseUrl/send-mail")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                // Render free tier cold start + SMTP handshake can exceed 20s.
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")

                val apiKey = BuildConfig.MAIL_SERVICE_API_KEY.trim()
                if (apiKey.isNotEmpty()) {
                    setRequestProperty("x-api-key", apiKey)
                }
            }

            val payload =
                buildString {
                    append("{")
                    append("\"to\":\"${to.jsonEscape()}\",")
                    append("\"subject\":\"${subject.jsonEscape()}\",")
                    append("\"text\":\"${text.jsonEscape()}\",")
                    append("\"fromName\":\"${fromName.jsonEscape()}\"")
                    append("}")
                }

            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val errorBody =
                        runCatching {
                            connection.errorStream
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                .orEmpty()
                        }.getOrDefault("")
                    throw IllegalStateException(
                        "Mail service request failed: HTTP $status${if (errorBody.isNotBlank()) " - $errorBody" else ""}",
                    )
                }
            } finally {
                connection.disconnect()
            }
        }
    }

private fun String.jsonEscape(): String =
    buildString(length) {
        for (ch in this@jsonEscape) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
