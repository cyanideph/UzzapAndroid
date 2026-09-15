package com.kolipri.uzzap.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashDiagnostics {
    private const val FILE_NAME = "last_crash.txt"
    private const val TAG = "UzzapCrash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try { write(appContext, thread, throwable) }
            catch (writeError: Throwable) { Log.e(TAG, "Unable to persist crash report", writeError) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun write(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val report = buildString {
            appendLine("UZZAP CRASH DIAGNOSTIC")
            appendLine("Time: $stamp")
            appendLine("Thread: ${thread.name} (${thread.id})")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("App package: ${context.packageName}")
            appendLine()
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message ?: "<none>"}")
            appendLine()
            appendLine("STACK TRACE")
            appendLine(throwable.stackTraceToString())
            var cause = throwable.cause
            var depth = 1
            while (cause != null && depth <= 5) {
                appendLine()
                appendLine("CAUSE #$depth: ${cause.javaClass.name}: ${cause.message ?: "<none>"}")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
                depth++
            }
        }
        File(context.filesDir, FILE_NAME).writeText(report, Charsets.UTF_8)
    }

    fun hasCrash(context: Context): Boolean = File(context.filesDir, FILE_NAME).isFile

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.isFile) file.readText(Charsets.UTF_8) else null
    }

    fun clear(context: Context) { runCatching { File(context.filesDir, FILE_NAME).delete() } }
}
