package com.mipastudio.memostamp.core.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static test suite verifying store privacy compliance:
 * - Apple Privacy Manifest (PrivacyInfo.xcprivacy) validity & first-party data types
 * - In-app Privacy Policy link discoverability on Android and iOS
 * - In-app Account Deletion discoverability and backend deletion truth (#47)
 * - Deployable web resources (site/privacy, site/account-deletion)
 * - Absence of advertising SDKs, analytics SDKs, or cross-app tracking
 */
class StorePrivacyComplianceStaticTest {

    private val workspaceRoot: File by lazy {
        val userDir = System.getProperty("user.dir") ?: "."
        var current: File? = File(userDir)
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) {
                return@lazy current
            }
            current = current.parentFile
        }
        File(userDir)
    }

    @Test
    fun testApplePrivacyManifestCollectedDataTypesPopulated() {
        val manifest = File(workspaceRoot, "iosApp/iosApp/PrivacyInfo.xcprivacy")
        assertTrue("PrivacyInfo.xcprivacy must exist", manifest.exists())
        val content = manifest.readText()

        assertFalse(
            "NSPrivacyCollectedDataTypes must not be empty",
            content.contains("<key>NSPrivacyCollectedDataTypes</key>\n    <array/>") ||
                content.contains("<key>NSPrivacyCollectedDataTypes</key><array/>")
        )

        val requiredConstants = listOf(
            "NSPrivacyCollectedDataTypeEmailAddress",
            "NSPrivacyCollectedDataTypeName",
            "NSPrivacyCollectedDataTypeUserID",
            "NSPrivacyCollectedDataTypeDeviceID",
            "NSPrivacyCollectedDataTypePreciseLocation",
            "NSPrivacyCollectedDataTypeCoarseLocation",
            "NSPrivacyCollectedDataTypePhotosorVideos",
            "NSPrivacyCollectedDataTypeEmailsOrTextMessages",
            "NSPrivacyCollectedDataTypeOtherUserContent"
        )

        for (constant in requiredConstants) {
            assertTrue("Privacy manifest must contain $constant", content.contains(constant))
        }

        assertTrue("NSPrivacyTracking must be declared", content.contains("NSPrivacyTracking"))
        assertTrue("UserDefaults API reason CA92.1 must be declared", content.contains("CA92.1"))
    }

    @Test
    fun testAndroidInAppPrivacyPolicyLinkWired() {
        val passportScreen = File(workspaceRoot, "androidApp/src/main/java/com/mipastudio/memostamp/feature/profile/PassportScreen.kt")
        assertTrue("PassportScreen.kt must exist", passportScreen.exists())
        val screenContent = passportScreen.readText()

        assertTrue(
            "PassportScreen must contain Privacy Policy button calling PrivacyConfig",
            screenContent.contains("PrivacyConfig.getPrivacyPolicyUrl()")
        )

        val buildGradle = File(workspaceRoot, "androidApp/build.gradle.kts")
        val gradleContent = buildGradle.readText()
        assertTrue(
            "build.gradle.kts must define PRIVACY_POLICY_URL buildConfigField",
            gradleContent.contains("buildConfigField(\"String\", \"PRIVACY_POLICY_URL\"")
        )
    }

    @Test
    fun testIosInAppPrivacyPolicyLinkWired() {
        val passportView = File(workspaceRoot, "iosApp/iosApp/Features/Profile/PassportScreenView.swift")
        assertTrue("PassportScreenView.swift must exist", passportView.exists())
        val content = passportView.readText()

        assertTrue(
            "PassportScreenView.swift must contain Privacy Policy button opening PrivacyConfig.privacyPolicyUrl",
            content.contains("PrivacyConfig.privacyPolicyUrl")
        )
    }

    @Test
    fun testProductionPrivacyPolicyUrlIsSecureHttps() {
        val privacyConfig = File(workspaceRoot, "androidApp/src/main/java/com/mipastudio/memostamp/core/privacy/PrivacyConfig.kt")
        assertTrue("PrivacyConfig.kt must exist", privacyConfig.exists())
        val content = privacyConfig.readText()

        assertTrue(
            "Default Privacy Policy URL must be HTTPS",
            content.contains("DEFAULT_PRIVACY_POLICY_URL = \"https://")
        )
        assertFalse(
            "Default Privacy Policy URL must not be localhost or example.com",
            content.contains("localhost") || content.contains("127.0.0.1") || content.contains("example.com")
        )
    }

    @Test
    fun testInAppAccountDeletionDiscoverableOnBothPlatforms() {
        // Android
        val androidScreen = File(workspaceRoot, "androidApp/src/main/java/com/mipastudio/memostamp/feature/profile/PassportScreen.kt")
        val androidContent = androidScreen.readText()
        assertTrue("Android must have delete account dialog", androidContent.contains("showDeleteAccountDialog"))

        // iOS
        val iosScreen = File(workspaceRoot, "iosApp/iosApp/Features/Profile/PassportScreenView.swift")
        val iosContent = iosScreen.readText()
        assertTrue("iOS must have delete account sheet", iosContent.contains("showDeleteAccountSheet"))

        // Backend Edge Function
        val edgeFunction = File(workspaceRoot, "supabase/functions/delete-account/index.ts")
        assertTrue("delete-account Edge Function must exist", edgeFunction.exists())
        val edgeContent = edgeFunction.readText()
        assertTrue("Edge Function must purge storage under stamp-media", edgeContent.contains("stamp-media"))
        assertTrue("Edge Function must delete user from auth admin API", edgeContent.contains("/auth/v1/admin/users/"))
    }

    @Test
    fun testWebComplianceResourcesExist() {
        val privacyHtml = File(workspaceRoot, "site/privacy/index.html")
        assertTrue("site/privacy/index.html must exist", privacyHtml.exists())
        val privacyContent = privacyHtml.readText()
        assertTrue("Privacy policy must mention MemoStamp", privacyContent.contains("MemoStamp Privacy Policy"))
        assertTrue("Privacy policy must disclose sub-processors", privacyContent.contains("Supabase"))

        val deletionHtml = File(workspaceRoot, "site/account-deletion/index.html")
        assertTrue("site/account-deletion/index.html must exist", deletionHtml.exists())
        val deletionContent = deletionHtml.readText()
        assertTrue("Deletion page must explain in-app deletion", deletionContent.contains("Method 1: Direct In-App Deletion"))
        assertTrue("Deletion page must clarify stamp trade retention", deletionContent.contains("Accepted Stamp Trades"))
    }

    @Test
    fun testComplianceDocumentationExists() {
        val inventoryDoc = File(workspaceRoot, "docs/release/privacy-data-inventory.md")
        assertTrue("docs/release/privacy-data-inventory.md must exist", inventoryDoc.exists())
        val inventoryContent = inventoryDoc.readText()
        assertTrue("Inventory must contain data matrix", inventoryContent.contains("Comprehensive Data Inventory Matrix"))

        val safetyDoc = File(workspaceRoot, "docs/release/google-play-data-safety.md")
        assertTrue("docs/release/google-play-data-safety.md must exist", safetyDoc.exists())
        val safetyContent = safetyDoc.readText()
        assertTrue("Data safety doc must cover Personal Info", safetyContent.contains("Personal Info"))
    }

    @Test
    fun testZeroAdvertisingOrTrackingSDKs() {
        val buildGradle = File(workspaceRoot, "androidApp/build.gradle.kts").readText()
        val manifest = File(workspaceRoot, "androidApp/src/main/AndroidManifest.xml").readText()

        val disallowed = listOf(
            "play-services-ads",
            "firebase-analytics",
            "facebook-android-sdk",
            "af-android-sdk",
            "adjust-android",
            "android.permission.AD_ID"
        )

        for (pattern in disallowed) {
            assertFalse(
                "Build config or manifest must not include $pattern",
                buildGradle.contains(pattern) || manifest.contains(pattern)
            )
        }
    }

    @Test
    fun testStorePrivacyPreflightScriptPresent() {
        val script = File(workspaceRoot, "scripts/check-store-privacy-readiness.sh")
        assertTrue("scripts/check-store-privacy-readiness.sh must exist", script.exists())
        val content = script.readText()
        assertTrue("Script must distinguish CODE_READY", content.contains("CODE_READY"))
        assertTrue("Script must distinguish EXTERNAL_SETUP_REQUIRED", content.contains("EXTERNAL_SETUP_REQUIRED"))
    }
}
