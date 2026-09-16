package com.kolipri.uzzap.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseAuthRoleTest {
    @Test
    fun adminRoleIsPreserved() {
        assertEquals("admin", normalizeApplicationRole("admin"))
        assertEquals("admin", normalizeApplicationRole(" ADMIN "))
    }

    @Test
    fun moderatorRoleIsPreserved() {
        assertEquals("moderator", normalizeApplicationRole("moderator"))
        assertEquals("moderator", normalizeApplicationRole(" MODERATOR "))
    }

    @Test
    fun missingOrUnknownRoleFallsBackToUser() {
        assertEquals("user", normalizeApplicationRole(null))
        assertEquals("user", normalizeApplicationRole(""))
        assertEquals("user", normalizeApplicationRole("user"))
        assertEquals("user", normalizeApplicationRole("owner"))
        assertEquals("user", normalizeApplicationRole("ADMINISTRATOR"))
    }
}
