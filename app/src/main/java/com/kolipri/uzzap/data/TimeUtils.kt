package com.kolipri.uzzap.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object TimeUtils {
    private val isoFormats = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    fun parseIsoToEpochMillis(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        // PostgREST timestamps carry microsecond precision (e.g. .123456).
        // SimpleDateFormat reads every S as milliseconds, so .123456 would
        // parse as 123456ms (~2min off). Truncate to millis first.
        val str = isoString.trim().replace(Regex("(\\.\\d{3})\\d+"), "$1")
        for (pattern in isoFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(str)
                if (date != null) return date.time
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    fun isWithinWindow(isoString: String?, windowSeconds: Long): Boolean {
        val epoch = parseIsoToEpochMillis(isoString) ?: return false
        val now = System.currentTimeMillis()
        val diff = (now - epoch) / 1000L
        return diff in 0..windowSeconds
    }

    fun isFuture(isoString: String?): Boolean {
        val epoch = parseIsoToEpochMillis(isoString) ?: return false
        return epoch > System.currentTimeMillis()
    }
}
