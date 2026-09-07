package com.mipastudio.memostamp.core.i18n

import com.mipastudio.memostamp.domain.model.AudienceType
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Static & unit tests verifying the production localization architecture:
 * 1. 100% Key parity between Android values/strings.xml and values-vi/strings.xml
 * 2. Format placeholder parity (%s, %d) between English base and Vietnamese resources
 * 3. iOS string catalog & .strings resource parity with Android
 * 4. Elimination of legacy string(vi:en:) authority from iOS production screens
 * 5. AppLanguageMode definitions and legacy preference migration logic
 * 6. SYSTEM mode defaults (new users must never be forced to Vietnamese)
 * 7. Decoupling of stable internal IDs from display labels
 */
class LocalizationArchitectureStaticTest {

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

    private fun parseXmlStrings(xmlFile: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlFile)
        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue
            val text = node.textContent ?: ""
            if (name != null) {
                result[name] = text
            }
        }
        return result
    }

    @Test
    fun testAndroidBaseAndViKeyParity() {
        val baseXml = File(workspaceRoot, "androidApp/src/main/res/values/strings.xml")
        val viXml = File(workspaceRoot, "androidApp/src/main/res/values-vi/strings.xml")

        assertTrue("values/strings.xml must exist", baseXml.exists())
        assertTrue("values-vi/strings.xml must exist", viXml.exists())

        val baseKeys = parseXmlStrings(baseXml)
        val viKeys = parseXmlStrings(viXml)

        assertTrue("Base strings must contain over 300 keys (found: ${baseKeys.size})", baseKeys.size > 300)
        assertTrue("Vietnamese strings must contain over 300 keys (found: ${viKeys.size})", viKeys.size > 300)

        val missingInVi = baseKeys.keys - viKeys.keys
        val missingInBase = viKeys.keys - baseKeys.keys

        assertTrue("Missing keys in values-vi: $missingInVi", missingInVi.isEmpty())
        assertTrue("Extra/missing keys in base values: $missingInBase", missingInBase.isEmpty())
        assertEquals("Total key count must match exactly", baseKeys.size, viKeys.size)
    }

    @Test
    fun testFormatPlaceholderParity() {
        val baseXml = File(workspaceRoot, "androidApp/src/main/res/values/strings.xml")
        val viXml = File(workspaceRoot, "androidApp/src/main/res/values-vi/strings.xml")

        val baseStrings = parseXmlStrings(baseXml)
        val viStrings = parseXmlStrings(viXml)

        val placeholderPattern = Pattern.compile("%(\\d+\\$)?[sdf]")

        for ((key, baseVal) in baseStrings) {
            val viVal = viStrings[key] ?: error("Missing key in VI: $key")

            val basePlaceholders = mutableListOf<String>()
            val baseMatcher = placeholderPattern.matcher(baseVal)
            while (baseMatcher.find()) {
                basePlaceholders.add(baseMatcher.group())
            }

            val viPlaceholders = mutableListOf<String>()
            val viMatcher = placeholderPattern.matcher(viVal)
            while (viMatcher.find()) {
                viPlaceholders.add(viMatcher.group())
            }

            assertEquals(
                "Placeholder count mismatch for key '$key': base=[$baseVal], vi=[$viVal]",
                basePlaceholders.size,
                viPlaceholders.size
            )
        }
    }

    @Test
    fun testIOSLocalizationResourcesExistAndMatch() {
        val xcstrings = File(workspaceRoot, "iosApp/iosApp/Localizable.xcstrings")
        val enStrings = File(workspaceRoot, "iosApp/iosApp/en.lproj/Localizable.strings")
        val viStrings = File(workspaceRoot, "iosApp/iosApp/vi.lproj/Localizable.strings")

        assertTrue("Localizable.xcstrings must exist", xcstrings.exists())
        assertTrue("en.lproj/Localizable.strings must exist", enStrings.exists())
        assertTrue("vi.lproj/Localizable.strings must exist", viStrings.exists())

        val baseXml = File(workspaceRoot, "androidApp/src/main/res/values/strings.xml")
        val baseKeys = parseXmlStrings(baseXml)

        val enContent = enStrings.readText()
        val viContent = viStrings.readText()

        // Verify key iOS resources contain representative production keys
        val sampleKeys = listOf(
            "auth_tab_login",
            "auth_tab_register",
            "nav_home",
            "nav_passport",
            "common_cancel",
            "settings_language_section",
            "settings_language_system",
            "settings_language_vi",
            "friends_unfriend_btn",
            "feed_filter_all_friends",
            "chat_status_seen"
        )

        for (k in sampleKeys) {
            assertTrue("Base keys must contain $k", baseKeys.containsKey(k))
            assertTrue("en.lproj must contain $k", enContent.contains("\"$k\""))
            assertTrue("vi.lproj must contain $k", viContent.contains("\"$k\""))
        }
    }

    @Test
    fun testNoLegacyStringViEnAuthorityInProductionIOS() {
        val iosAppDir = File(workspaceRoot, "iosApp/iosApp")
        assertTrue("iosApp directory must exist", iosAppDir.exists())

        val violationPattern = Pattern.compile("langManager\\.string\\(vi:")
        val violations = mutableListOf<String>()

        iosAppDir.walkTopDown()
            .filter { it.isFile && it.extension == "swift" && !it.name.contains("Test") }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    if (violationPattern.matcher(line).find()) {
                        violations.add("${file.name}:${index + 1}: $line")
                    }
                }
            }

        assertTrue(
            "Found legacy langManager.string(vi:en:) in production Swift code:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun testAppLanguageModeLegacyPreferenceMigration() {
        // 1. Legacy "vi" -> VIETNAMESE
        assertEquals(AppLanguageMode.VIETNAMESE, AppLanguageMode.fromCode("vi"))

        // 2. Legacy "en" -> ENGLISH
        assertEquals(AppLanguageMode.ENGLISH, AppLanguageMode.fromCode("en"))

        // 3. "system" -> SYSTEM
        assertEquals(AppLanguageMode.SYSTEM, AppLanguageMode.fromCode("system"))

        // 4. Null / empty / unknown / corrupt -> SYSTEM (Safe fallback)
        assertEquals(AppLanguageMode.SYSTEM, AppLanguageMode.fromCode(null))
        assertEquals(AppLanguageMode.SYSTEM, AppLanguageMode.fromCode(""))
        assertEquals(AppLanguageMode.SYSTEM, AppLanguageMode.fromCode("   "))
        assertEquals(AppLanguageMode.SYSTEM, AppLanguageMode.fromCode("corrupted_value"))
        assertEquals(AppLanguageMode.SYSTEM, AppLanguageMode.fromCode("fr"))
        assertEquals(AppLanguageMode.SYSTEM, AppLanguageMode.fromCode("de"))
    }

    @Test
    fun testDefaultLanguageModeNeverForcesVietnamese() {
        // A new user has no stored preference -> fromCode(null)
        val defaultMode = AppLanguageMode.fromCode(null)
        assertEquals(
            "Default language mode for fresh install must be SYSTEM",
            AppLanguageMode.SYSTEM,
            defaultMode
        )
        assertNotEquals(
            "Fresh install must not force VIETNAMESE",
            AppLanguageMode.VIETNAMESE,
            defaultMode
        )
    }

    @Test
    fun testStableSemanticIdsSeparatedFromLabels() {
        // Verify enum IDs remain stable internal contracts and are not translated strings
        assertEquals("FRIENDS", AudienceType.FRIENDS.name)
        assertEquals("SPECIFIC_FRIENDS", AudienceType.SPECIFIC_FRIENDS.name)
        assertEquals("ONLY_ME", AudienceType.ONLY_ME.name)

        // Verify labelRes is pointing to resource IDs, not raw strings
        assertNotEquals(0, AudienceType.FRIENDS.labelRes)
        assertNotEquals(0, AudienceType.SPECIFIC_FRIENDS.labelRes)
        assertNotEquals(0, AudienceType.ONLY_ME.labelRes)
    }
}
