package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidReleaseHardeningTest {

    private val retiredProjectHost = "mghmhhbyhmuvherlyrqa.supabase.co"
    private val canonicalUrl = "https://byjedmjbzcwxjkewtkzx.supabase.co"
    private val canonicalProjectId = "byjedmjbzcwxjkewtkzx"

    @Test
    fun test1_canonicalProductionSupabaseConstants() {
        // Defaults must come from BuildConfig (.env / .env.example), not retired project.
        assertEquals(BuildConfig.SUPABASE_URL, SupabaseConfig.DEFAULT_SUPABASE_URL)
        assertEquals(BuildConfig.SUPABASE_PROJECT_ID, SupabaseConfig.DEFAULT_PROJECT_ID)
        assertEquals(canonicalUrl, SupabaseConfig.DEFAULT_SUPABASE_URL)
        assertEquals(canonicalProjectId, SupabaseConfig.DEFAULT_PROJECT_ID)
        assertFalse(SupabaseConfig.DEFAULT_SUPABASE_URL.contains(retiredProjectHost))
        assertNotNull(SupabaseConfig.DEFAULT_ANON_KEY)
        assertTrue(SupabaseConfig.DEFAULT_ANON_KEY.startsWith("eyJ"))
    }

    @Test
    fun test2_getSupabaseUrl_defaultsToCanonical() {
        val url = SupabaseConfig.getSupabaseUrl(null)
        assertEquals(canonicalUrl, url)
    }

    @Test
    fun test3_getAnonKey_defaultsToCanonical() {
        val anonKey = SupabaseConfig.getAnonKey(null)
        assertEquals(SupabaseConfig.DEFAULT_ANON_KEY, anonKey)
    }
}
