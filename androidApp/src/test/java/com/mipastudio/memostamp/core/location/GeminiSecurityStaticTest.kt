package com.mipastudio.memostamp.core.location

import com.mipastudio.memostamp.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GeminiSecurityStaticTest {

    @Test
    fun test1_noGeminiApiKeyInBuildConfig() {
        val declaredFieldNames = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse(
            "BuildConfig must not contain GEMINI_API_KEY; provider credential was removed from Android artifacts",
            declaredFieldNames.contains("GEMINI_API_KEY")
        )
    }

    @Test
    fun test2_noDirectGeminiProviderUrlInProductionCode() {
        var currentDir: File = File(System.getProperty("user.dir") ?: ".")
        while (!File(currentDir, "settings.gradle.kts").exists()) {
            val parent = currentDir.parentFile ?: break
            currentDir = parent
        }

        val mainSrcDir = File(currentDir, "androidApp/src/main")
        if (mainSrcDir.exists()) {
            val violations = mutableListOf<String>()
            mainSrcDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.extension == "kt" || file.extension == "java" || file.extension == "xml")) {
                    val content = file.readText()
                    if (content.contains("generativelanguage.googleapis.com", ignoreCase = true)) {
                        violations.add("${file.relativeTo(currentDir).path}: contains direct Gemini provider URL")
                    }
                    if (content.contains("BuildConfig.GEMINI_API_KEY")) {
                        violations.add("${file.relativeTo(currentDir).path}: references BuildConfig.GEMINI_API_KEY")
                    }
                }
            }
            assertTrue(
                "Production Android source code must not contain Gemini provider URLs or keys:\n${violations.joinToString("\n")}",
                violations.isEmpty()
            )
        }
    }

    @Test
    fun test3_noGeminiApiKeyInEnvExample() {
        var currentDir: File = File(System.getProperty("user.dir") ?: ".")
        while (!File(currentDir, "settings.gradle.kts").exists()) {
            val parent = currentDir.parentFile ?: break
            currentDir = parent
        }

        val envExample = File(currentDir, ".env.example")
        if (envExample.exists()) {
            val content = envExample.readText()
            assertFalse(
                ".env.example must not instruct developers to put GEMINI_API_KEY in mobile app environment",
                content.contains("GEMINI_API_KEY")
            )
            assertTrue(
                ".env.example should retain client GOOGLE_MAPS_API_KEY",
                content.contains("GOOGLE_MAPS_API_KEY")
            )
        }
    }
}
