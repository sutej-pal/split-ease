package com.splitease.app.data.sync

import android.os.SystemClock
import android.util.Log
import com.splitease.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug timing for login hydrate.
 *
 * Filter Logcat with tag `SyncNet`. Also writes `sync-hydrate-trace.txt`
 * under the app's external files dir (pulled after a first-login run).
 */
object SyncNetworkLog {
    const val TAG = "SyncNet"
    const val TRACE_FILE_NAME = "sync-hydrate-trace.txt"

    private val requestSeq = AtomicInteger(0)
    private val requestCount = AtomicInteger(0)
    private val requestMs = AtomicLong(0)
    private val hydrateRequestCountStart = AtomicInteger(0)
    private val hydrateRequestMsStart = AtomicLong(0)
    private val hydrateStartedAtElapsed = AtomicLong(0)

    @Volatile
    private var traceFile: File? = null

    private val clockFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Opens (or recreates) the on-device trace file. */
    fun attach(filesDir: File) {
        if (!BuildConfig.DEBUG) return
        val file = File(filesDir, TRACE_FILE_NAME)
        traceFile = file
        runCatching {
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.writeText("SyncNet trace started ${Date()}\n")
            }
        }
    }

    fun beginHydrate(initial: Boolean) {
        if (!BuildConfig.DEBUG) return
        hydrateRequestCountStart.set(requestCount.get())
        hydrateRequestMsStart.set(requestMs.get())
        hydrateStartedAtElapsed.set(SystemClock.elapsedRealtime())
        emit(
            "HYDRATE begin initial=$initial " +
                "(each HTTP line is one Supabase/PostgREST round-trip)",
        )
    }

    fun endHydrate() {
        if (!BuildConfig.DEBUG) return
        val wallMs = SystemClock.elapsedRealtime() - hydrateStartedAtElapsed.get()
        val httpCount = requestCount.get() - hydrateRequestCountStart.get()
        val httpMs = requestMs.get() - hydrateRequestMsStart.get()
        val avg = if (httpCount == 0) 0L else httpMs / httpCount
        emit(
            "HYDRATE end wallMs=$wallMs httpCount=$httpCount " +
                "sumHttpMs=$httpMs avgHttpMs=$avg " +
                "(wall ≈ sequential wait; avgHttpMs is per-request latency)",
        )
    }

    fun info(message: String) {
        emit(message)
    }

    suspend fun <T> phase(
        name: String,
        block: suspend () -> T,
    ): T {
        if (!BuildConfig.DEBUG) return block()
        val httpBefore = requestCount.get()
        val start = SystemClock.elapsedRealtime()
        emit("PHASE begin $name")
        return try {
            val result = block()
            val httpDelta = requestCount.get() - httpBefore
            emit(
                "PHASE end $name elapsedMs=${SystemClock.elapsedRealtime() - start} " +
                    "httpCount=$httpDelta",
            )
            result
        } catch (err: Throwable) {
            val httpDelta = requestCount.get() - httpBefore
            emit(
                "PHASE fail $name elapsedMs=${SystemClock.elapsedRealtime() - start} " +
                    "httpCount=$httpDelta err=${err.message}",
                warn = true,
            )
            throw err
        }
    }

    /** OkHttp interceptor: one line per request. Debug builds only. */
    class HttpInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!BuildConfig.DEBUG) {
                return chain.proceed(request)
            }
            val n = requestSeq.incrementAndGet()
            val reqBytes = request.body?.contentLength()?.takeIf { it >= 0 } ?: -1L
            val label = describe(request.method, request.url.toString())
            val startNs = System.nanoTime()
            return try {
                val response = chain.proceed(request)
                val ms = (System.nanoTime() - startNs) / 1_000_000L
                requestCount.incrementAndGet()
                requestMs.addAndGet(ms)
                val bytes = response.header("Content-Length") ?: "-"
                emit(
                    "#$n $label reqBytes=$reqBytes -> ${response.code} ${ms}ms bytes=$bytes",
                )
                response
            } catch (err: Exception) {
                val ms = (System.nanoTime() - startNs) / 1_000_000L
                emit(
                    "#$n $label reqBytes=$reqBytes FAILED ${ms}ms " +
                        "${err.javaClass.simpleName}: ${err.message}",
                    warn = true,
                )
                throw err
            }
        }
    }

    internal fun describe(
        method: String,
        rawUrl: String,
    ): String {
        val noQuery = rawUrl.substringBefore('?')
        val path =
            noQuery
                .substringAfter("://")
                .substringAfter('/', missingDelimiterValue = noQuery)
        val query = rawUrl.substringAfter('?', missingDelimiterValue = "")
        val cleaned =
            query
                .split('&')
                .filter { part ->
                    val key = part.substringBefore('=').lowercase()
                    key != "apikey" && key != "authorization"
                }.joinToString("&")
                .let { if (it.length <= 180) it else it.take(177) + "..." }
        return if (cleaned.isEmpty()) {
            "$method $path"
        } else {
            "$method $path?$cleaned"
        }
    }

    private fun emit(
        message: String,
        warn: Boolean = false,
    ) {
        if (!BuildConfig.DEBUG) return
        val line = "${clockFormat.format(Date())} $message"
        if (warn) {
            Log.w(TAG, message)
        } else {
            Log.i(TAG, message)
        }
        val file = traceFile ?: return
        runCatching {
            synchronized(file) {
                file.appendText(line + "\n")
            }
        }
    }
}

