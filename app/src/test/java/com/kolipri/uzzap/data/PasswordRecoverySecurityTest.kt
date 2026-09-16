package com.kolipri.uzzap.data

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRecoverySecurityTest {

    @Test
    fun rejectsNullAndMalformedRecoveryLinks() {
        assertFalse(PasswordRecoveryClient.consumeRecoveryIntent(null))
        assertFalse(PasswordRecoveryClient.consumeRecoveryIntent(Uri.parse("https://example.com/auth/reset#type=recovery&access_token=fake")))
        assertFalse(PasswordRecoveryClient.consumeRecoveryIntent(Uri.parse("uzzap://other/reset#type=recovery&access_token=fake")))
        assertFalse(PasswordRecoveryClient.consumeRecoveryIntent(Uri.parse("uzzap://auth/wrong#type=recovery&access_token=fake")))
    }

    @Test
    fun rejectsNonRecoveryAndMissingAccessTokenLinks() {
        assertFalse(PasswordRecoveryClient.consumeRecoveryIntent(Uri.parse("uzzap://auth/reset#type=email&access_token=fake")))
        assertFalse(PasswordRecoveryClient.consumeRecoveryIntent(Uri.parse("uzzap://auth/reset#type=recovery")))
    }

    @Test
    fun acceptsExactRecoveryLinkShape() {
        assertTrue(PasswordRecoveryClient.consumeRecoveryIntent(Uri.parse("uzzap://auth/reset#type=recovery&access_token=fake")))
        AppState.authToken = ""
        SupabaseClient.setAccessTokenForLegacy(null)
    }
}
