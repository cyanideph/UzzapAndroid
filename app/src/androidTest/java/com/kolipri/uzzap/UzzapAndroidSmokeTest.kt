package com.kolipri.uzzap

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.kolipri.uzzap.ui.ResetPasswordActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android-level smoke coverage.
 *
 * These tests intentionally exercise the real Activity lifecycle and view tree.
 * They complement JVM tests; they are not a substitute for Supabase/two-user E2E.
 */
@RunWith(AndroidJUnit4::class)
class UzzapAndroidSmokeTest {

    @Test
    fun resetPasswordActivity_missingRecoveryLink_isSafeAndActionable() {
        val scenario = ActivityScenario.launch<ResetPasswordActivity>(
            Intent(Intent.ACTION_VIEW).apply {
                data = null
            }
        )

        onView(withText("Choose a new Uzzap password")).check { view, noViewFoundException ->
            if (noViewFoundException != null) throw noViewFoundException
            check(view.isDisplayed) { "Reset password title must be visible" }
        }
        onView(withText("The reset link is missing or has expired. Request a new reset link from Login."))
            .check { view, noViewFoundException ->
                if (noViewFoundException != null) throw noViewFoundException
                check(view.isDisplayed) { "Missing-link recovery guidance must be visible" }
            }

        scenario.close()
    }
}
