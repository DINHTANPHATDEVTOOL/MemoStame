package com.mipastudio.memostamp.core.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Task #66: iOS Location Grounding Parity Static & Contract Test Suite
 *
 * Validates that iOS location and postmark grounding behavior matches Android's production
 * architecture while adhering strictly to zero mobile Gemini provider secrets, server-authoritative
 * JWT authentication, account-switch isolation, search generation race guards, and defensive decoding.
 */
class IOSLocationGroundingParityStaticTest {

    private lateinit var workspaceRoot: File
    private lateinit var clientFile: File
    private lateinit var pickerFile: File
    private lateinit var noteFile: File
    private lateinit var editorFile: File
    private lateinit var pbxFile: File

    @Before
    fun setUp() {
        var currentDir = File(System.getProperty("user.dir") ?: ".")
        while (!File(currentDir, "settings.gradle.kts").exists()) {
            val parent = currentDir.parentFile ?: break
            currentDir = parent
        }
        workspaceRoot = currentDir
        clientFile = File(workspaceRoot, "iosApp/iosApp/Data/IOSMapsGroundingClient.swift")
        pickerFile = File(workspaceRoot, "iosApp/iosApp/Features/Location/LocationPickerSheetView.swift")
        noteFile = File(workspaceRoot, "iosApp/iosApp/Features/Note/MemoryNoteScreenView.swift")
        editorFile = File(workspaceRoot, "iosApp/iosApp/Features/Editor/StampEditorScreenView.swift")
        pbxFile = File(workspaceRoot, "iosApp/iosApp.xcodeproj/project.pbxproj")
    }

    // --------------------------------------------------------------------------
    // 1. Core Swift Files Exist
    // --------------------------------------------------------------------------
    @Test
    fun test01_iosMapsGroundingClientFileExists() {
        assertTrue("IOSMapsGroundingClient.swift must exist", clientFile.exists())
    }

    @Test
    fun test02_locationPickerSheetViewFileExists() {
        assertTrue("LocationPickerSheetView.swift must exist", pickerFile.exists())
    }

    // --------------------------------------------------------------------------
    // 2. Gateway Endpoint & Auth Authority
    // --------------------------------------------------------------------------
    @Test
    fun test03_iosUsesAuthoritativeMapsGroundingEndpoint() {
        val content = clientFile.readText()
        assertTrue(
            "IOSMapsGroundingClient must call /functions/v1/maps-grounding",
            content.contains("/functions/v1/maps-grounding")
        )
    }

    @Test
    fun test04_bearerTokenIsCurrentAuthenticatedUserToken() {
        val content = clientFile.readText()
        assertTrue(
            "IOSMapsGroundingClient must attach Authorization: Bearer with session.accessToken",
            content.contains("Bearer \\(session.accessToken)")
        )
        assertTrue(
            "IOSMapsGroundingClient must use Supabase anonKey as apikey header",
            content.contains("SupabaseAuthService.shared.anonKey")
        )
    }

    @Test
    fun test05_noAuthCausesZeroGroundedServerRequest() {
        val content = clientFile.readText()
        assertTrue(
            "Client must check for valid authenticated session before firing network request",
            content.contains("SupabaseAuthService.shared.loadOrRefreshSession") &&
            content.contains("guard let session = session")
        )
    }

    @Test
    fun test06_expiredSessionUsesRefreshContractAndSafelyFallsBack() {
        val content = clientFile.readText()
        assertTrue(
            "loadOrRefreshSession handles refresh and delivers native fallback on failure",
            content.contains("onStateChange(.serverFallback(nativeCandidates")
        )
    }

    // --------------------------------------------------------------------------
    // 3. Search Payload Allowed Fields & Bounded Contracts
    // --------------------------------------------------------------------------
    @Test
    fun test07_searchPlacesContainsOnlyAllowedFields() {
        val content = clientFile.readText()
        assertTrue("Payload must declare action SEARCH_PLACES", content.contains("\"action\": \"SEARCH_PLACES\""))
        assertTrue("Payload must bind query", content.contains("\"query\": String(trimmedQuery.prefix(100))"))
        assertTrue("Payload must bind categoryFilter", content.contains("\"categoryFilter\": effectiveCategory"))
    }

    @Test
    fun test08_storyRequestContainsOnlyAllowedFields() {
        val content = clientFile.readText()
        assertTrue("Payload must declare action GENERATE_POSTMARK_STORY", content.contains("\"action\": \"GENERATE_POSTMARK_STORY\""))
        assertTrue("Payload must bind placeName", content.contains("\"placeName\": trimmedName"))
        assertTrue("Payload must bind locationAddress", content.contains("\"locationAddress\": trimmedAddress"))
    }

    @Test
    fun test09_noClientActingUid() {
        val content = clientFile.readText()
        val forbidden = listOf("\"acting_uid\"", "\"actingUid\"", "\"userId\"", "\"user_id\"", "\"actorId\"")
        for (token in forbidden) {
            assertFalse("Client request payload must never contain $token", content.contains(token))
        }
    }

    @Test
    fun test10_noClientProviderUrl() {
        val content = clientFile.readText()
        val forbidden = listOf("\"provider_url\"", "\"providerUrl\"")
        for (token in forbidden) {
            assertFalse("Client request payload must never contain $token", content.contains(token))
        }
    }

    @Test
    fun test11_noClientModelConfiguration() {
        val content = clientFile.readText()
        assertFalse("Client request payload must never specify model", content.contains("\"model\""))
    }

    @Test
    fun test12_noClientArbitraryPrompt() {
        val content = clientFile.readText()
        val forbidden = listOf("\"prompt\"", "\"system_prompt\"", "\"systemPrompt\"", "\"tools\"")
        for (token in forbidden) {
            assertFalse("Client request payload must never specify $token", content.contains(token))
        }
    }

    // --------------------------------------------------------------------------
    // 4. Account-Switch & Query Generation Race Guards
    // --------------------------------------------------------------------------
    @Test
    fun test13_accountSwitchIsolationGuard() {
        val content = clientFile.readText()
        assertTrue(
            "Client must capture current auth UID before dispatch and verify match upon response",
            content.contains("let capturedAuthUid = session.userId") &&
            content.contains("currentUid == capturedAuthUid")
        )
    }

    @Test
    fun test14_searchGenerationRaceConditionGuard() {
        val content = clientFile.readText()
        assertTrue(
            "Client must increment search generation counter and discard superseded responses",
            content.contains("self.activeSearchGeneration += 1") &&
            content.contains("isGenerationValid")
        )
    }

    // --------------------------------------------------------------------------
    // 5. Defensive Response Decoding
    // --------------------------------------------------------------------------
    @Test
    fun test15_malformedJsonHandledDefensively() {
        val content = clientFile.readText()
        assertTrue(
            "Parsing must use optional serialization (try? JSONSerialization.jsonObject)",
            content.contains("try? JSONSerialization.jsonObject")
        )
    }

    @Test
    fun test16_unknownCategorySanitizedAgainstAllowedEnum() {
        val content = clientFile.readText()
        assertTrue(
            "Allowed categories set must be defined and checked",
            content.contains("allowedCategories") &&
            content.contains("allowedCategories.contains")
        )
    }

    @Test
    fun test17_negativeAndNonFiniteDistanceRejected() {
        val content = clientFile.readText()
        assertTrue(
            "Distance validation must reject NaN, infinite, or negative values",
            content.contains("!distNum.isNaN") &&
            content.contains("!distNum.isInfinite") &&
            content.contains("distNum >= 0")
        )
    }

    @Test
    fun test18_blankPlaceNameRejected() {
        val content = clientFile.readText()
        assertTrue(
            "Blank place names must be rejected during parsing",
            content.contains("!rawName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty")
        )
    }

    @Test
    fun test19_serverFailureAndTimeoutSafelyDeliverNativeCandidates() {
        val content = clientFile.readText()
        assertTrue(
            "Server errors or network failure must safely yield native fallback results",
            content.contains("onStateChange(.serverFallback(nativeCandidates")
        )
    }

    @Test
    fun test20_rateLimitHttp429HandledWithRetrySeconds() {
        val content = clientFile.readText()
        assertTrue(
            "HTTP 429 status code and retry_after_seconds must be parsed",
            content.contains("httpResp.statusCode == 429") &&
            content.contains("retry_after_seconds") &&
            content.contains("onStateChange(.rateLimited")
        )
    }

    // --------------------------------------------------------------------------
    // 6. MemoryNote Screen Non-Destructive Autofill
    // --------------------------------------------------------------------------
    @Test
    fun test21_userWrittenTitleIsNeverOverwritten() {
        val content = noteFile.readText()
        assertTrue(
            "MemoryNoteScreenView must only autofill suggested title if user title is currently blank",
            content.contains("title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty")
        )
    }

    @Test
    fun test22_userWrittenNoteIsNeverOverwritten() {
        val content = noteFile.readText()
        assertTrue(
            "MemoryNoteScreenView must only autofill poetic note if user caption is currently blank",
            content.contains("caption.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty")
        )
    }

    @Test
    fun test23_memoryNoteBindsLocationPickerSheet() {
        val content = noteFile.readText()
        assertTrue(
            "MemoryNoteScreenView must bind LocationPickerSheetView",
            content.contains("LocationPickerSheetView(") &&
            content.contains("showLocationPickerSheet")
        )
    }

    // --------------------------------------------------------------------------
    // 7. StampEditor Location Parity
    // --------------------------------------------------------------------------
    @Test
    fun test24_stampEditorBindsLocationPickerSheet() {
        val content = editorFile.readText()
        assertTrue(
            "StampEditorScreenView must bind LocationPickerSheetView",
            content.contains("LocationPickerSheetView(") &&
            content.contains("showLocationPickerSheet")
        )
    }

    @Test
    fun test25_stampEditorUpdatesLocationStateAndLivePreview() {
        val content = editorFile.readText()
        assertTrue(
            "StampEditorScreenView must update stampLocation and pass it to DieCutStampView",
            content.contains("stampLocation = locationName") &&
            content.contains("location: stampLocation")
        )
    }

    // --------------------------------------------------------------------------
    // 8. Zero Mobile Gemini Secrets & Forbidden Tokens in iOS Source Tree
    // --------------------------------------------------------------------------
    @Test
    fun test26_noGeminiApiKeyInIosSourceTree() {
        val iosSrcDir = File(workspaceRoot, "iosApp/iosApp")
        val violations = mutableListOf<String>()
        iosSrcDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "swift") {
                val text = file.readText()
                if (text.contains("GEMINI_API_KEY")) {
                    violations.add("${file.name} contains GEMINI_API_KEY")
                }
            }
        }
        assertTrue("No iOS file may contain GEMINI_API_KEY:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun test27_noDirectGeminiHostInIosSourceTree() {
        val iosSrcDir = File(workspaceRoot, "iosApp/iosApp")
        val violations = mutableListOf<String>()
        iosSrcDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "swift") {
                val text = file.readText()
                if (text.contains("generativelanguage.googleapis.com", ignoreCase = true)) {
                    violations.add("${file.name} contains direct Gemini provider host")
                }
            }
        }
        assertTrue("No iOS file may contain generativelanguage.googleapis.com:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun test28_noServiceRoleInIosSourceTree() {
        val iosSrcDir = File(workspaceRoot, "iosApp/iosApp")
        val violations = mutableListOf<String>()
        iosSrcDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "swift") {
                val text = file.readText()
                if (text.contains("service_role", ignoreCase = true)) {
                    violations.add("${file.name} contains service_role")
                }
            }
        }
        assertTrue("No iOS file may contain service_role:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    // --------------------------------------------------------------------------
    // 9. Xcode Project Wiring
    // --------------------------------------------------------------------------
    @Test
    fun test29_xcodeProjectContainsClientFileReferenceAndBuildSource() {
        val content = pbxFile.readText()
        assertTrue("project.pbxproj must include IOSMapsGroundingClient.swift", content.contains("IOSMapsGroundingClient.swift"))
        assertTrue("project.pbxproj must include IOSMapsGroundingClient.swift in Sources", content.contains("IOSMapsGroundingClient.swift in Sources"))
    }

    @Test
    fun test30_xcodeProjectContainsPickerFileReferenceAndBuildSource() {
        val content = pbxFile.readText()
        assertTrue("project.pbxproj must include LocationPickerSheetView.swift", content.contains("LocationPickerSheetView.swift"))
        assertTrue("project.pbxproj must include LocationPickerSheetView.swift in Sources", content.contains("LocationPickerSheetView.swift in Sources"))
    }

    // --------------------------------------------------------------------------
    // 10. Privacy & Edge Function Contracts Preserved
    // --------------------------------------------------------------------------
    @Test
    fun test31_privacyManifestRetainedWithTrackingFalse() {
        val privacyInfo = File(workspaceRoot, "iosApp/iosApp/PrivacyInfo.xcprivacy")
        assertTrue("PrivacyInfo.xcprivacy must exist", privacyInfo.exists())
        val content = privacyInfo.readText()
        assertTrue("NSPrivacyTracking must remain false", content.contains("<key>NSPrivacyTracking</key>") && content.contains("<false/>"))
    }

    @Test
    fun test32_mapsGroundingEdgeFunctionContractPreserved() {
        val edgeFunction = File(workspaceRoot, "supabase/functions/maps-grounding/index.ts")
        assertTrue("maps-grounding Edge Function must exist", edgeFunction.exists())
        val content = edgeFunction.readText()
        assertTrue("Edge function must enforce SEARCH_PLACES", content.contains("SEARCH_PLACES"))
        assertTrue("Edge function must enforce GENERATE_POSTMARK_STORY", content.contains("GENERATE_POSTMARK_STORY"))
        assertTrue("Edge function must derive UID from JWT", content.contains("auth/v1/user"))
    }
}
