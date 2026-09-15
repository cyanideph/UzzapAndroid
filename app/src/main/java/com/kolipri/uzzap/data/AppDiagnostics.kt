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
    private const val MAX_LINES = 400
    private const val MAX_MESSAGE = 600
    private val lock = Any()

    enum class Level { INFO, OK, WARN, ERROR }

    fun log(context: Context, level: Level, area: String, message: String) {
        val cleanArea = area.replace("|", "/").take(80)
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

    fun exception(context: Context, area: String, throwable: Throwable) {
        error(context, area, "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}")
    }

    fun activity(context: Context, activityName: String, event: String) {
        info(context, "Activity", "$activityName $event")
    }

    fun read(context: Context): String {
        synchronized(lock) {
            return runCatching { File(context.applicationContext.filesDir, FILE_NAME).readText(Charsets.UTF_8) }
                .getOrDefault("")
        }
    }

    fun clear(context: Context) {
        synchronized(lock) { runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() } }
    }

    fun deviceSummary(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}"
}
