package com.kolipri.uzzap.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local, bounded diagnostic event log for troubleshooting the whole app. Never stores tokens/passwords. */
object AppDiagnostics {
    private const val FILE_NAME = "uzzap_diagnostics.log"
    private const val MAX_LINES = 800
    private const val MAX_MESSAGE = 600
    private val lock = Any()

    enum class Level { INFO, OK, WARN, ERROR, STARTED, FAILED }

    fun log(context: Context, level: Level, area: String, message: String) {
        val cleanArea = area.replace("|", "/").take(100)
        val cleanMessage = message.replace("\r", " ").replace("\n", " ").replace("|", "/").take(MAX_MESSAGE)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val line = "$stamp|${level.name}|$cleanArea|$cleanMessage"
        synchronized(lock) {
            runCatching {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                val lines = if (file.isFile) file.readLines(Charsets.UTF_8).takeLast(MAX_LINES - 1) else emptyList()
                file.writeText((lines + line).joinToString("\n"), Charsets.UTF_8)
            }
        }
    }

    fun info(context: Context, area: String, message: String) = log(context, Level.INFO, area, message)
    fun ok(context: Context, area: String, message: String) = log(context, Level.OK, area, message)
    fun warn(context: Context, area: String, message: String) = log(context, Level.WARN, area, message)
    fun error(context: Context, area: String, message: String) = log(context, Level.ERROR, area, message)
    fun started(context: Context, function: String, details: String = "") = log(context, Level.STARTED, function, details.ifBlank { "STARTED" })
    fun success(context: Context, function: String, details: String = "") = log(context, Level.OK, function, "SUCCESS${if (details.isBlank()) "" else ": $details"}")
    fun failed(context: Context, function: String, error: String) = log(context, Level.FAILED, function, "FAILED: ${sanitizeError(error)}")

    fun <T> tracked(context: Context, function: String, block: () -> T): T = runCatching {
        started(context, function)
        block().also { success(context, function) }
    }.getOrElse { e -> failed(context, function, e.message ?: e.javaClass.simpleName); throw e }

    private fun sanitizeError(error: String): String = error
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "Bearer <redacted>")
        .replace(Regex("(?i)(access[_-]?token|refresh[_-]?token|password)=?[^&\\s]+"), "$1=<redacted>")
        .take(MAX_MESSAGE)

    fun exception(context: Context, area: String, throwable: Throwable) {
        failed(context, area, "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}")
    }

    fun activity(context: Context, activityName: String, event: String) {
        info(context, "Activity", "$activityName $event")
    }

    fun read(context: Context): String = synchronized(lock) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).readText(Charsets.UTF_8) }.getOrDefault("")
    }

    /** Returns the latest status for each tracked function. */
    fun healthSummary(context: Context): String {
        val latest = linkedMapOf<String, String>()
        read(context).lineSequence().forEach { line ->
            val parts = line.split('|', limit = 4)
            if (parts.size == 4 && (parts[1] == Level.STARTED.name || parts[1] == Level.OK.name || parts[1] == Level.FAILED.name)) {
                latest[parts[2]] = "${parts[1]} — ${parts[3]}"
            }
        }
        return latest.entries.joinToString("\n") { (name, status) -> "$name → $status" }
    }

    fun clear(context: Context) { synchronized(lock) { runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() } } }

    fun deviceSummary(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}"
}
