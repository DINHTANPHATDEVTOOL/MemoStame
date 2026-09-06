package com.mipastudio.memostamp.core.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PushCapabilityWiringStaticTest {

    private fun getRepoRoot(): File {
        var dir: File = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists()) {
            val parent = dir.parentFile ?: break
            dir = parent
        }
        return dir
    }

    @Test
    fun test1_androidFcmServicePresent() {
        val root = getRepoRoot()
        val manifestFile = File(root, "androidApp/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val manifestContent = manifestFile.readText()
        assertTrue(
            "AndroidManifest.xml must declare MemoStampFirebaseMessagingService",
            manifestContent.contains("MemoStampFirebaseMessagingService")
        )
        assertTrue(
            "AndroidManifest.xml must declare com.google.firebase.MESSAGING_EVENT intent-filter",
            manifestContent.contains("com.google.firebase.MESSAGING_EVENT")
        )

        val serviceFile = File(root, "androidApp/src/main/java/com/mipastudio/memostamp/core/notification/MemoStampFirebaseMessagingService.kt")
        assertTrue("MemoStampFirebaseMessagingService.kt must exist", serviceFile.exists())
    }

    @Test
    fun test2_firebaseMessagingDependencyPresent() {
        val root = getRepoRoot()
        val buildFile = File(root, "androidApp/build.gradle.kts")
        assertTrue("androidApp/build.gradle.kts must exist", buildFile.exists())

        val content = buildFile.readText()
        assertTrue(
            "androidApp/build.gradle.kts must include com.google.firebase:firebase-messaging",
            content.contains("com.google.firebase:firebase-messaging")
        )
    }

    @Test
    fun test3_googleServicesPluginWired() {
        val root = getRepoRoot()

        // 1. Check version catalog
        val catalogFile = File(root, "gradle/libs.versions.toml")
        assertTrue("gradle/libs.versions.toml must exist", catalogFile.exists())
        val catalogContent = catalogFile.readText()
        assertTrue(
            "Version catalog must define google-services plugin",
            catalogContent.contains("com.google.gms.google-services")
        )

        // 2. Check root build.gradle.kts
        val rootBuildFile = File(root, "build.gradle.kts")
        assertTrue("root build.gradle.kts must exist", rootBuildFile.exists())
        val rootContent = rootBuildFile.readText()
        assertTrue(
            "Root build.gradle.kts must declare google.services plugin",
            rootContent.contains("alias(libs.plugins.google.services)")
        )

        // 3. Check androidApp/build.gradle.kts conditional wiring
        val appBuildFile = File(root, "androidApp/build.gradle.kts")
        val appContent = appBuildFile.readText()
        assertTrue(
            "androidApp/build.gradle.kts must conditionally apply com.google.gms.google-services",
            appContent.contains("com.google.gms.google-services") &&
                    appContent.contains("hasGoogleServicesConfig")
        )
    }

    @Test
    fun test4_noFirebaseAuthFirestoreStorageDependency() {
        val root = getRepoRoot()
        val buildFile = File(root, "androidApp/build.gradle.kts")
        val content = buildFile.readText()

        val forbiddenDependencies = listOf(
            "firebase-auth",
            "firebase-firestore",
            "firebase-database",
            "firebase-storage",
            "firebase-analytics",
            "firebase-crashlytics"
        )

        for (dep in forbiddenDependencies) {
            assertFalse(
                "androidApp must not depend on $dep (Supabase is the sole authority)",
                content.contains(dep, ignoreCase = true)
            )
        }
    }

    @Test
    fun test5_noEmbeddedServiceAccount() {
        val root = getRepoRoot()
        val violations = mutableListOf<String>()

        root.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.contains("service-account", ignoreCase = true) || file.name == "google-services.json")) {
                // Ignore .git directory and build caches
                val path = file.relativeTo(root).path
                if (!path.startsWith(".git") && !path.contains("build") && !path.contains(".gradle")) {
                    violations.add(path)
                }
            }
        }

        assertTrue(
            "Repository source tree must not contain committed service-account or google-services.json files:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun test6_noEmbeddedFcmPrivateCredential() {
        val root = getRepoRoot()
        val androidSrcDir = File(root, "androidApp/src/main")
        if (androidSrcDir.exists()) {
            val violations = mutableListOf<String>()
            androidSrcDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.extension == "kt" || file.extension == "java" || file.extension == "xml")) {
                    val content = file.readText()
                    if (content.contains("BEGIN PRIVATE KEY") || content.contains("BEGIN RSA PRIVATE KEY")) {
                        violations.add("${file.relativeTo(root).path}: Contains embedded private key header")
                    }
                    if (content.contains("client_secret") || content.contains("private_key_id")) {
                        violations.add("${file.relativeTo(root).path}: Contains service account key field")
                    }
                }
            }
            assertTrue(
                "Production Android source code must not contain private credentials:\n${violations.joinToString("\n")}",
                violations.isEmpty()
            )
        }
    }

    @Test
    fun test7_fcmAbsentConfigFailClosed() {
        val root = getRepoRoot()
        val managerFile = File(root, "androidApp/src/main/java/com/mipastudio/memostamp/core/notification/PushTokenManager.kt")
        assertTrue("PushTokenManager.kt must exist", managerFile.exists())

        val content = managerFile.readText()
        assertTrue(
            "PushTokenManager must check FirebaseApp.getApps(context).isNotEmpty() before requesting token",
            content.contains("FirebaseApp.getApps(context).isNotEmpty()")
        )
        assertTrue(
            "PushTokenManager must fail closed by passing null when FirebaseApp is not initialized",
            content.contains("onToken(null)")
        )
    }

    @Test
    fun test8_iosPushManagerPresent() {
        val root = getRepoRoot()
        val iosManagerFile = File(root, "iosApp/iosApp/Services/IOSPushNotificationManager.swift")
        assertTrue("IOSPushNotificationManager.swift must exist", iosManagerFile.exists())

        val content = iosManagerFile.readText()
        assertTrue(
            "IOSPushNotificationManager must implement requestAuthorizationAndRegister",
            content.contains("func requestAuthorizationAndRegister")
        )
        assertTrue(
            "IOSPushNotificationManager must implement handleDeviceToken",
            content.contains("func handleDeviceToken")
        )
        assertTrue(
            "IOSPushNotificationManager must implement handleRegistrationError",
            content.contains("func handleRegistrationError")
        )
    }

    @Test
    fun test9_iosPushEntitlementsPresent() {
        val root = getRepoRoot()
        val entitlementsFile = File(root, "iosApp/iosApp/iosApp.entitlements")
        assertTrue("iosApp/iosApp/iosApp.entitlements must exist", entitlementsFile.exists())

        val content = entitlementsFile.readText()
        assertTrue(
            "iosApp.entitlements must configure aps-environment",
            content.contains("aps-environment")
        )
    }

    @Test
    fun test10_iosTargetUsesEntitlements() {
        val root = getRepoRoot()
        val projectFile = File(root, "iosApp/iosApp.xcodeproj/project.pbxproj")
        assertTrue("project.pbxproj must exist", projectFile.exists())

        val content = projectFile.readText()
        assertTrue(
            "project.pbxproj must configure CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements",
            content.contains("CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements;")
        )
        assertTrue(
            "project.pbxproj must declare iosApp.entitlements file reference",
            content.contains("iosApp.entitlements")
        )
    }

    @Test
    fun test11_noApnsPrivateKeyOrProvisioningProfileInRepo() {
        val root = getRepoRoot()
        val violations = mutableListOf<String>()

        root.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension == "p8" || file.extension == "mobileprovision" || file.extension == "p12")) {
                val path = file.relativeTo(root).path
                if (!path.startsWith(".git") && !path.contains("build")) {
                    violations.add(path)
                }
            }
        }

        assertTrue(
            "Repository must not contain Apple private keys or provisioning profiles:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun test12_noPushTokenLogging() {
        val root = getRepoRoot()
        val pushFiles = listOf(
            File(root, "androidApp/src/main/java/com/mipastudio/memostamp/core/notification/PushTokenManager.kt"),
            File(root, "androidApp/src/main/java/com/mipastudio/memostamp/core/notification/MemoStampFirebaseMessagingService.kt"),
            File(root, "iosApp/iosApp/Services/IOSPushNotificationManager.swift")
        )

        for (file in pushFiles) {
            assertTrue("${file.name} must exist", file.exists())
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val lower = line.lowercase()
                if ((lower.contains("log.") || lower.contains("print(") || lower.contains("nslog(")) &&
                    (lower.contains("token") || lower.contains("tokenhex") || lower.contains("devicetoken"))
                ) {
                    assertFalse(
                        "Sensitive token logging detected at ${file.name}:${index + 1}: $line",
                        true
                    )
                }
            }
        }
    }

    @Test
    fun test13_environmentAccuracy() {
        val root = getRepoRoot()

        // Android
        val androidManager = File(root, "androidApp/src/main/java/com/mipastudio/memostamp/core/notification/PushTokenManager.kt")
        val androidContent = androidManager.readText()
        assertTrue(
            "PushTokenManager must derive environment from BuildConfig.DEBUG",
            androidContent.contains("BuildConfig.DEBUG") &&
                    androidContent.contains("\"development\"") &&
                    androidContent.contains("\"production\"")
        )

        // iOS
        val iosManager = File(root, "iosApp/iosApp/Services/IOSPushNotificationManager.swift")
        val iosContent = iosManager.readText()
        assertTrue(
            "IOSPushNotificationManager must derive environment from #if DEBUG",
            iosContent.contains("#if DEBUG") &&
                    iosContent.contains("\"development\"") &&
                    iosContent.contains("\"production\"")
        )
    }
}
