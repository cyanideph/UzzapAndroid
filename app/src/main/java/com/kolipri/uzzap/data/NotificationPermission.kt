package com.kolipri.uzzap.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Single policy for the POST_NOTIFICATIONS runtime permission (Android 13+).
 *
 * Problem it fixes: the app requested this permission from both
 * SplashMenuActivity (every cold start) and BuddyListFragment (every time the
 * tab was shown), ignored the result, and never remembered a denial — so a
 * user who tapped "Don't allow" was nagged again on the next launch or tab
 * switch. The system permission dialog is also outside the app window, which
 * made repeated prompts look like taps were being ignored during device tests.
 *
 * Policy: ask at most [MAX_ASKS] times per install while the system still
 * allows prompting. After that (or once the system reports the permission as
 * permanently declined) stop asking; the user can enable notifications later
 * in Android Settings. Grant resets the budget.
 */
object NotificationPermission {
    private const val PREFS = "uzzap_notif_perm"
    private const val KEY_ASKED_COUNT = "asked_count"
    private const val KEY_DECLINED_PERMANENTLY = "declined_permanently"
    const val MAX_ASKS = 2

    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean {
        if (!isRequired()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** True when the app should show the system permission prompt now. */
    fun shouldRequest(context: Context): Boolean {
        if (!isRequired() || isGranted(context)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DECLINED_PERMANENTLY, false)) return false
        return prefs.getInt(KEY_ASKED_COUNT, 0) < MAX_ASKS
    }

    /**
     * Records the outcome of a system prompt.
     *
     * @param canAskAgain the value of shouldShowRequestPermissionRationale()
     * read AFTER the denial (true = denied but prompt may be shown again,
     * false = "don't ask again" selected or double-denied on Android 13+).
     */
    fun onResult(context: Context, granted: Boolean, canAskAgain: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (granted) {
            prefs.edit()
                .putInt(KEY_ASKED_COUNT, 0)
                .putBoolean(KEY_DECLINED_PERMANENTLY, false)
                .apply()
            return
        }
        val asked = prefs.getInt(KEY_ASKED_COUNT, 0) + 1
        val permanentlyDeclined = !canAskAgain || asked >= MAX_ASKS
        prefs.edit()
            .putInt(KEY_ASKED_COUNT, asked)
            .putBoolean(KEY_DECLINED_PERMANENTLY, permanentlyDeclined)
            .apply()
    }
}
