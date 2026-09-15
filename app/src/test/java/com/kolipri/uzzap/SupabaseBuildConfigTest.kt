package com.kolipri.uzzap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseBuildConfigTest {
    @Test
    fun supabaseUrlIsConfigured() {
        assertFalse(BuildConfig.SUPABASE_URL.isBlank())
        assertTrue(BuildConfig.SUPABASE_URL.startsWith("https://"))
        assertTrue(BuildConfig.SUPABASE_URL.endsWith("supabase.co"))
    }

    @Test
    fun supabaseClientKeyIsConfigured() {
        assertFalse(BuildConfig.SUPABASE_ANON_KEY.isBlank())
        assertTrue(BuildConfig.SUPABASE_ANON_KEY.length > 40)
    }
}
