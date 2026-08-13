package com.splitease.app.data.remote

import com.splitease.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client for the SplitEase Server mail endpoint.
 *
 * Prefer [sendTemplate] so copy/HTML live in `server/mail-templates/`.
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
            html: String? = null,
        ) = withContext(Dispatchers.IO) {
            val payload =
                JSONObject().apply {
                    put("to", to)
                    put("subject", subject)
                    put("text", text)
                    put("fromName", fromName)
                    if (!html.isNullOrBlank()) put("html", html)
                }
            postSendMail(payload)
        }

        /**
         * Sends mail using a named server template under `mail-templates/`.
         *
         * @param to Recipient email.
         * @param template Template id (welcome, invite-friend, invite-group, reminder).
         * @param vars Placeholder values for the template.
         * @param subject Optional subject override.
         * @param fromName Optional from-name override.
         */
        suspend fun sendTemplate(
            to: String,
            template: String,
            vars: Map<String, String> = emptyMap(),
            subject: String? = null,
            fromName: String? = null,
        ) = withContext(Dispatchers.IO) {
            val payload =
                JSONObject().apply {
                    put("to", to)
                    put("template", template)
                    put(
                        "vars",
                        JSONObject().apply {
                            vars.forEach { (k, v) -> put(k, v) }
                        },
                    )
                    if (!subject.isNullOrBlank()) put("subject", subject)
                    if (!fromName.isNullOrBlank()) put("fromName", fromName)
                }
            postSendMail(payload)
        }

        private fun postSendMail(payload: JSONObject) {
            val baseUrl = BuildConfig.MAIL_SERVICE_BASE_URL.trim().trimEnd('/')
            require(baseUrl.isNotEmpty()) { "MAIL_SERVICE_BASE_URL is missing." }

            val endpoint = URL("$baseUrl/send-mail")
            val connection =
                (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    // Mail-service cold start + provider handshake can exceed 20s.
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

            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
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
