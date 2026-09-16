package com.kolipri.uzzap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRegistrationTest {
    @Test
    fun trimsAndNormalizesRegistrationNickname() {
        assertEquals("Juan Dela Cruz", normalizeRegistrationIdentity("  Juan   Dela Cruz  "))
    }

    @Test
    fun removesControlCharacters() {
        assertEquals("JuanDelaCruz", normalizeRegistrationIdentity("Juan\u0000Dela\u000ACruz"))
    }

    @Test
    fun limitsRegistrationIdentityLength() {
        val value = normalizeRegistrationIdentity("x".repeat(100))
        assertEquals(40, value.length)
    }

    @Test
    fun blankRegistrationIdentityIsRejectedByNormalizedValue() {
        assertTrue(normalizeRegistrationIdentity("   ").isBlank())
    }
}
