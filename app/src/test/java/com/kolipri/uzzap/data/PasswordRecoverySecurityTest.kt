package com.kolipri.uzzap.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRecoverySecurityTest {

    @Test
    fun rejectsNullAndMalformedRecoveryLinks() {
        assertFalse(PasswordRecoveryClient.isValidRecoveryLink(null))
        assertFalse(PasswordRecoveryClient.isValidRecoveryLink("https://example.com/auth/reset#type=recovery&access_token=fake"))
        assertFalse(PasswordRecoveryClient.isValidRecoveryLink("uzzap://other/reset#type=recovery&access_token=fake"))
        assertFalse(PasswordRecoveryClient.isValidRecoveryLink("uzzap://auth/wrong#type=recovery&access_token=fake"))
    }

    @Test
    fun rejectsNonRecoveryAndMissingAccessTokenLinks() {
        assertFalse(PasswordRecoveryClient.isValidRecoveryLink("uzzap://auth/reset#type=email&access_token=fake"))
        assertFalse(PasswordRecoveryClient.isValidRecoveryLink("uzzap://auth/reset#type=recovery"))
        assertFalse(PasswordRecoveryClient.isValidRecoveryLink("uzzap://auth/reset?redirect=evil#type=recovery&access_token=fake"))
    }

    @Test
    fun rejectsOversizedAccessTokens() {
        val oversized = "a".repeat(4097)
        assertFalse(PasswordRecoveryClient.isValidRecoveryLink("uzzap://auth/reset#type=recovery&access_token=$oversized"))
    }

    @Test
    fun acceptsExactRecoveryLinkShape() {
        assertTrue(PasswordRecoveryClient.isValidRecoveryLink("uzzap://auth/reset#type=recovery&access_token=fake"))
        assertTrue(PasswordRecoveryClient.isValidRecoveryLink("uzzap://auth/reset#type=recovery&access_token=fake&refresh_token=refresh"))
    }
}
