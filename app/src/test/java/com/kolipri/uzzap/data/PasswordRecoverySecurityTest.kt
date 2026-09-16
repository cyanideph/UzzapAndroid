package com.kolipri.uzzap.data

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRecoverySecurityTest {

    @Test
    fun rejectsNullAndMalformedRecoveryLinks() {
        assertFalse(PasswordRecoveryClient.isValidRecoveryIntent(null))
        assertFalse(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("https://example.com/auth/reset#type=recovery&access_token=fake")))
        assertFalse(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("uzzap://other/reset#type=recovery&access_token=fake")))
        assertFalse(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("uzzap://auth/wrong#type=recovery&access_token=fake")))
    }

    @Test
    fun rejectsNonRecoveryAndMissingAccessTokenLinks() {
        assertFalse(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("uzzap://auth/reset#type=email&access_token=fake")))
        assertFalse(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("uzzap://auth/reset#type=recovery")))
        assertFalse(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("uzzap://auth/reset?redirect=evil#type=recovery&access_token=fake")))
    }

    @Test
    fun rejectsOversizedAccessTokens() {
        val oversized = "a".repeat(4097)
        assertFalse(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("uzzap://auth/reset#type=recovery&access_token=$oversized")))
    }

    @Test
    fun acceptsExactRecoveryLinkShape() {
        assertTrue(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("uzzap://auth/reset#type=recovery&access_token=fake")))
        assertTrue(PasswordRecoveryClient.isValidRecoveryIntent(Uri.parse("uzzap://auth/reset#type=recovery&access_token=fake&refresh_token=refresh")))
    }
}
