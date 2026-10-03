package com.kolipri.uzzap.data

import android.app.Activity
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Continuous, bounded observability for every page and tracked application operation. */
object AppDiagnostics {
    private const val FILE_NAME = "uzzap_diagnostics.log"
    private const val MAX_LINES = 1600
    private const val MAX_MESSAGE = 600
    private const val MAX_LIVE_EVENTS = 300
    private const val MAX_DISK_BYTES = 256 * 1024L
    private val lock = Any()
    private val diskExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "UzzapDiagnosticsDisk").apply { isDaemon = true }
    }
    private var applicationContext: Context? = null

    enum class Level { INFO, OK, WARN, ERROR, STARTED, FAILED }
    enum class Status { IDLE, RUNNING, PASS, FAIL, WARN, SKIPPED }

    data class Event(val id: String, val correlationId: String?, val timestamp: Long, val level: Level, val area: String, val function: String, val status: Status?, val durationMs: Long?, val details: String)
    data class FunctionHealth(val function: String, val status: Status, val lastTimestamp: Long, val durationMs: Long?, val details: String, val correlationId: String?)

    class Operation internal constructor(private val diagnostics: AppDiagnostics, val function: String, val correlationId: String, private val startedAt: Long) {
        private var completed = false
        fun pass(details: String = "SUCCESS") { if (!completed) { completed = true; diagnostics.finish(this, Status.PASS, Level.OK, details, System.currentTimeMillis() - startedAt) } }
        fun warn(details: String) { if (!completed) { completed = true; diagnostics.finish(this, Status.WARN, Level.WARN, details, System.currentTimeMillis() - startedAt) } }
        fun fail(error: String) { if (!completed) { completed = true; diagnostics.finish(this, Status.FAIL, Level.FAILED, "FAILED: ${sanitizeError(error)}", System.currentTimeMillis() - startedAt) } }
        fun skip(details: String = "Not executed") { if (!completed) { completed = true; diagnostics.finish(this, Status.SKIPPED, Level.INFO, details, System.currentTimeMillis() - startedAt) } }
    }

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()
    private val _health = MutableStateFlow<Map<String, FunctionHealth>>(emptyMap())
    val health: StateFlow<Map<String, FunctionHealth>> = _health.asStateFlow()

    fun init(context: Context) { applicationContext = context.applicationContext }
    private fun context(): Context? = applicationContext

    fun begin(function: String, details: String = "", correlationId: String = UUID.randomUUID().toString().take(8)): Operation {
        val op = Operation(this, function, correlationId, System.currentTimeMillis())
        emitEvent(Level.STARTED, function, function, Status.RUNNING, null, details, correlationId)
        return op
    }

    private fun finish(op: Operation, status: Status, level: Level, details: String, durationMs: Long) = emitEvent(level, op.function, op.function, status, durationMs, details, op.correlationId)

    fun event(function: String, status: Status, details: String = "", area: String = function, correlationId: String? = null) {
        val level = when (status) { Status.PASS -> Level.OK; Status.FAIL -> Level.FAILED; Status.WARN -> Level.WARN; Status.RUNNING -> Level.STARTED; Status.IDLE, Status.SKIPPED -> Level.INFO }
        emitEvent(level, area, function, status, null, details, correlationId)
    }

    private fun emitEvent(level: Level, area: String, function: String, status: Status?, durationMs: Long?, details: String, correlationId: String?) {
        val cleanArea = area.replace("|", "/").take(100)
        val cleanFunction = function.replace("|", "/").take(140)
        val cleanDetails = sanitizeError(details).replace("\r", " ").replace("\n", " ").replace("|", "/").take(MAX_MESSAGE)
        val now = System.currentTimeMillis()
        val event = Event(UUID.randomUUID().toString().take(12), correlationId, now, level, cleanArea, cleanFunction, status, durationMs, cleanDetails)
        synchronized(lock) {
            _events.value = (_events.value + event).takeLast(MAX_LIVE_EVENTS)
            if (status != null) {
                val updated = _health.value.toMutableMap()
                updated[cleanFunction] = FunctionHealth(cleanFunction, status, now, durationMs, cleanDetails, correlationId)
                _health.value = updated.toMap()
            }
            val context = context() ?: return@synchronized
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(now))
            val duration = durationMs?.let { " ${it}ms" } ?: ""
            val corr = correlationId?.let { " [${it}]" } ?: ""
            val line = "$stamp|${level.name}|$cleanArea|$cleanFunction|${status?.name ?: ""}$duration$corr|$cleanDetails"
            diskExecutor.execute {
                runCatching {
                    val file = File(context.filesDir, FILE_NAME)
                    file.appendText(line + "\n", Charsets.UTF_8)
                    if (file.length() > MAX_DISK_BYTES) {
                        val lines = file.readLines(Charsets.UTF_8).takeLast(MAX_LINES)
                        file.writeText(lines.joinToString("\n") + if (lines.isNotEmpty()) "\n" else "", Charsets.UTF_8)
                    }
                }
            }
        }
    }

    fun log(context: Context, level: Level, area: String, message: String) = emitEvent(level, area, area, null, null, message, null)
    fun info(context: Context, area: String, message: String) = log(context, Level.INFO, area, message)
    fun ok(context: Context, area: String, message: String) = log(context, Level.OK, area, message)
    fun warn(context: Context, area: String, message: String) = log(context, Level.WARN, area, message)
    fun error(context: Context, area: String, message: String) = log(context, Level.ERROR, area, message)
    fun started(context: Context, function: String, details: String = "") = event(function, Status.RUNNING, details)
    fun success(context: Context, function: String, details: String = "") = event(function, Status.PASS, "SUCCESS${if (details.isBlank()) "" else ": $details"}")
    fun failed(context: Context, function: String, error: String) = event(function, Status.FAIL, "FAILED: ${sanitizeError(error)}")
    fun started(function: String, details: String = "") { context()?.let { started(it, function, details) } }
    fun success(function: String, details: String = "") { context()?.let { success(it, function, details) } }
    fun failed(function: String, error: String) { context()?.let { failed(it, function, error) } }
    fun exception(context: Context, area: String, throwable: Throwable) = failed(context, area, "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}")

    private fun sanitizeError(error: String): String = error.replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "Bearer <redacted>").replace(Regex("(?i)(access[_-]?token|refresh[_-]?token|password)=?[^&\\s]+"), "$1=<redacted>").take(MAX_MESSAGE)

    fun activity(context: Context, activityName: String, event: String) = info(context, "Activity", "$activityName $event")
    fun monitorActivity(activity: Activity, lifecycleEvent: String) { activity(applicationContext ?: activity, activity.javaClass.simpleName, lifecycleEvent); if (lifecycleEvent == "created") event("Page.${activity.javaClass.simpleName}", Status.PASS, "PAGE_CREATED") }
    fun monitorPage(name: String, eventName: String = "opened") = event("Page.$name", Status.PASS, "PAGE_$eventName".uppercase(Locale.US), "Page")

    /** Declare a function/page so the console can distinguish NOT TESTED from missing instrumentation. */
    fun register(vararg functions: String) { functions.forEach { if (_health.value[it] == null) event(it, Status.IDLE, "NOT_TESTED") } }

    fun read(context: Context): String = synchronized(lock) { runCatching { File(context.applicationContext.filesDir, FILE_NAME).readText(Charsets.UTF_8) }.getOrDefault("") }

    fun healthSummary(context: Context): String = synchronized(lock) {
        _health.value.values.sortedBy { it.function.lowercase(Locale.US) }.joinToString("\n") { h ->
            val duration = h.durationMs?.let { " • ${it}ms" } ?: ""
            "${h.function} → ${h.status.name} — ${h.details}$duration"
        }
    }

    fun clear(context: Context) = synchronized(lock) { runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }; _events.value = emptyList(); _health.value = emptyMap() }
    fun deviceSummary(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}"
}
