package com.mipastudio.memostamp.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidReleaseHardeningTest {

    @Test
    fun test1_canonicalProductionSupabaseConstants() {
        assertEquals("https://mghmhhbyhmuvherlyrqa.supabase.co", SupabaseConfig.DEFAULT_SUPABASE_URL)
        assertNotNull(SupabaseConfig.DEFAULT_ANON_KEY)
        assertTrue(SupabaseConfig.DEFAULT_ANON_KEY.startsWith("eyJ"))
        assertEquals("mghmhhbyhmuvherlyrqa", SupabaseConfig.DEFAULT_PROJECT_ID)
    }

    @Test
    fun test2_getSupabaseUrl_defaultsToCanonical() {
        val url = SupabaseConfig.getSupabaseUrl(null)
        assertEquals("https://mghmhhbyhmuvherlyrqa.supabase.co", url)
    }

    @Test
    fun test3_getAnonKey_defaultsToCanonical() {
        val anonKey = SupabaseConfig.getAnonKey(null)
        assertEquals(SupabaseConfig.DEFAULT_ANON_KEY, anonKey)
    }
}
