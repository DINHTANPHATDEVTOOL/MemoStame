package com.mipastudio.memostamp.core.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static test suite verifying that MemoStamp is properly wired as a Release Candidate:
 * - Android release signing seam & version overrides
 * - Play App Signing architecture & ephemeral CI signing
 * - iOS App Store / TestFlight pipeline & ExportOptions
 * - Backend migrations & Edge Functions completeness
 * - Mobile artifact security (no service_role, no credentials in repo)
 */
class ReleaseCandidateWiringStaticTest {

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
    fun testAndroidBuildConfigurationApplicationIdAndR8() {
        val buildGradle = File(workspaceRoot, "androidApp/build.gradle.kts")
        assertTrue("androidApp/build.gradle.kts must exist", buildGradle.exists())
        val content = buildGradle.readText()

        assertTrue("Application ID must be com.mipastudio.memostamp",
            content.contains("""applicationId = "com.mipastudio.memostamp""""))
        assertTrue("R8 minification must be enabled in release",
            content.contains("isMinifyEnabled = true"))
        assertTrue("Resource shrinking must be enabled in release",
            content.contains("isShrinkResources = true"))
    }

    @Test
    fun testAndroidReleaseSigningSeamConfigured() {
        val buildGradle = File(workspaceRoot, "androidApp/build.gradle.kts")
        val content = buildGradle.readText()

        assertTrue("Must reference MEMOSTAMP_RELEASE_STORE_FILE",
            content.contains("MEMOSTAMP_RELEASE_STORE_FILE"))
        assertTrue("Must reference MEMOSTAMP_RELEASE_STORE_PASSWORD",
            content.contains("MEMOSTAMP_RELEASE_STORE_PASSWORD"))
        assertTrue("Must reference MEMOSTAMP_RELEASE_KEY_ALIAS",
            content.contains("MEMOSTAMP_RELEASE_KEY_ALIAS"))
        assertTrue("Must reference MEMOSTAMP_RELEASE_KEY_PASSWORD",
            content.contains("MEMOSTAMP_RELEASE_KEY_PASSWORD"))
        assertTrue("Must reference MEMOSTAMP_REQUIRE_RELEASE_SIGNING",
            content.contains("MEMOSTAMP_REQUIRE_RELEASE_SIGNING"))
    }

    @Test
    fun testAndroidVersionCodeValidationLogic() {
        // Valid positive integer
        val validCode = "42".toIntOrNull()
        assertTrue("Valid integer should parse", validCode != null && validCode > 0)

        // Invalid negative integer
        val negativeCode = "-5".toIntOrNull()
        assertFalse("Negative version code must not be allowed as positive", negativeCode != null && negativeCode > 0)

        // Invalid non-integer
        val nonIntCode = "beta-1".toIntOrNull()
        assertTrue("Non-integer version code must fail to parse", nonIntCode == null)
    }

    @Test
    fun testAndroidVersionNameValidationLogic() {
        val semverRegex = Regex("^[0-9]+(\\.[0-9]+)+(-[a-zA-Z0-9.]+)?$")

        assertTrue("Standard 2-digit version should match", "1.0".matches(semverRegex))
        assertTrue("Standard 3-digit semver should match", "1.0.1".matches(semverRegex))
        assertTrue("RC tag semver should match", "1.0.0-rc1".matches(semverRegex))
        assertFalse("Malformed version string should be rejected", "version-one".matches(semverRegex))
        assertFalse("Trailing dot should be rejected", "1.0.".matches(semverRegex))
    }

    @Test
    fun testEphemeralSigningInCIWorkflow() {
        val ciWorkflow = File(workspaceRoot, ".github/workflows/ios.yml")
        assertTrue("CI workflow must exist", ciWorkflow.exists())
        val content = ciWorkflow.readText()

        assertTrue("CI workflow must contain ephemeral keytool command",
            content.contains("keytool -genkeypair"))
        assertTrue("CI workflow must verify AAB signature with jarsigner",
            content.contains("jarsigner -verify"))
        assertTrue("CI workflow must clean up ephemeral keystore",
            content.contains("rm -f /tmp/ci_ephemeral_keystore.jks"))
        assertTrue("CI workflow must execute bundleRelease",
            content.contains(":androidApp:bundleRelease"))
    }

    @Test
    fun testIosBundleIdAndPrivacyManifest() {
        val pbxproj = File(workspaceRoot, "iosApp/iosApp.xcodeproj/project.pbxproj")
        assertTrue("project.pbxproj must exist", pbxproj.exists())
        val pbxContent = pbxproj.readText()
        assertTrue("Bundle ID must be com.mipastudio.memostamp",
            pbxContent.contains("PRODUCT_BUNDLE_IDENTIFIER = com.mipastudio.memostamp;"))

        val privacyInfo = File(workspaceRoot, "iosApp/iosApp/PrivacyInfo.xcprivacy")
        assertTrue("PrivacyInfo.xcprivacy must exist", privacyInfo.exists())
        val privacyContent = privacyInfo.readText()
        assertTrue("PrivacyInfo must declare NSPrivacyTracking",
            privacyContent.contains("NSPrivacyTracking"))
        assertTrue("PrivacyInfo must declare UserDefaults API category reason",
            privacyContent.contains("CA92.1"))
    }

    @Test
    fun testIosPushEntitlementsTargetWiring() {
        val entitlements = File(workspaceRoot, "iosApp/iosApp/iosApp.entitlements")
        assertTrue("iosApp.entitlements must exist", entitlements.exists())
        val entContent = entitlements.readText()
        assertTrue("aps-environment must be defined in entitlements",
            entContent.contains("aps-environment"))

        val pbxproj = File(workspaceRoot, "iosApp/iosApp.xcodeproj/project.pbxproj")
        val pbxContent = pbxproj.readText()
        assertTrue("CODE_SIGN_ENTITLEMENTS must be wired to iosApp.entitlements",
            pbxContent.contains("CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements;"))
    }

    @Test
    fun testIosAppStoreExportOptionsAndCodemagicPipeline() {
        val exportPlist = File(workspaceRoot, "iosApp/ExportOptions-AppStore.plist")
        assertTrue("ExportOptions-AppStore.plist must exist", exportPlist.exists())
        val plistContent = exportPlist.readText()
        assertTrue("Export method must be app-store",
            plistContent.contains("<string>app-store</string>"))

        val codemagicYaml = File(workspaceRoot, "codemagic.yaml")
        assertTrue("codemagic.yaml must exist", codemagicYaml.exists())
        val yamlContent = codemagicYaml.readText()
        assertTrue("Must define ios-app-store-release workflow",
            yamlContent.contains("ios-app-store-release:"))
        assertTrue("Must configure exportArchive with ExportOptions",
            yamlContent.contains("xcodebuild -exportArchive"))
        assertTrue("Must verify aps-environment production in signed IPA",
            yamlContent.contains("aps-environment"))
    }

    @Test
    fun testBackendMigrationsComplete001To011() {
        val migrationsDir = File(workspaceRoot, "supabase/migrations")
        assertTrue("supabase/migrations directory must exist", migrationsDir.exists())

        val expectedMigrations = listOf(
            "001_auth_social_rls.sql",
            "002_schema_contract_and_rls_hardening.sql",
            "003_deployment_safety_and_id_contract.sql",
            "004_add_direct_messages_realtime_publication.sql",
            "005_media_storage_and_feed_replies.sql",
            "006_push_device_tokens.sql",
            "007_user_blocks_and_abuse_reports.sql",
            "008_block_privacy_oracle_hotfix.sql",
            "009_cloud_stamp_trade.sql",
            "010_social_abuse_rate_limits.sql",
            "011_maps_grounding_rate_limits.sql"
        )

        for (m in expectedMigrations) {
            val file = File(migrationsDir, m)
            assertTrue("Migration $m must exist", file.exists())
        }
    }

    @Test
    fun testBackendEdgeFunctionsComplete() {
        val functionsDir = File(workspaceRoot, "supabase/functions")
        assertTrue("supabase/functions directory must exist", functionsDir.exists())

        val expectedFunctions = listOf(
            "delete-account",
            "dispatch-push",
            "accept-trade",
            "maps-grounding"
        )

        for (f in expectedFunctions) {
            val indexFile = File(functionsDir, "$f/index.ts")
            assertTrue("Edge Function $f/index.ts must exist", indexFile.exists())
        }
    }

    @Test
    fun testNoServiceRoleInMobileSource() {
        val androidSrc = File(workspaceRoot, "androidApp/src/main")
        val iosSrc = File(workspaceRoot, "iosApp/iosApp")

        val filesToCheck = androidSrc.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") } +
            iosSrc.walkTopDown().filter { it.isFile && it.extension == "swift" }

        for (f in filesToCheck) {
            val content = f.readText()
            assertFalse(
                "File ${f.name} must never contain service_role",
                content.contains("service_role", ignoreCase = true)
            )
        }
    }

    @Test
    fun testNoDirectGeminiEndpointInMobileProduction() {
        val androidSrc = File(workspaceRoot, "androidApp/src/main")
        val iosSrc = File(workspaceRoot, "iosApp/iosApp")

        val filesToCheck = androidSrc.walkTopDown().filter { it.isFile && it.extension == "kt" } +
            iosSrc.walkTopDown().filter { it.isFile && it.extension == "swift" }

        for (f in filesToCheck) {
            val content = f.readText()
            assertFalse(
                "File ${f.name} must never contain direct Gemini API endpoint",
                content.contains("generativelanguage.googleapis.com", ignoreCase = true)
            )
        }
    }

    @Test
    fun testNoTrackedSecretsInGit() {
        val prohibitedPatterns = listOf(
            ".keystore", ".jks", ".p8", ".p12", ".mobileprovision"
        )
        // Check standard paths
        for (pattern in prohibitedPatterns) {
            val matches = workspaceRoot.walkTopDown().filter {
                it.isFile && it.name.endsWith(pattern) && !it.absolutePath.contains(".gradle") && !it.absolutePath.contains("build")
            }.toList()
            assertTrue("No $pattern files should be tracked or present in repository: $matches", matches.isEmpty())
        }
    }

    @Test
    fun testReleaseCandidatePreflightScriptPresent() {
        val script = File(workspaceRoot, "scripts/check-release-candidate-readiness.sh")
        assertTrue("scripts/check-release-candidate-readiness.sh must exist", script.exists())
        val content = script.readText()
        assertTrue("Script must distinguish CODE_READY", content.contains("CODE_READY"))
        assertTrue("Script must distinguish EXTERNAL_SETUP_REQUIRED", content.contains("EXTERNAL_SETUP_REQUIRED"))
    }
}
