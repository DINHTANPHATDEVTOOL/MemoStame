package com.mipastudio.memostamp.domain.model

/**
 * Platform-neutral semantic icon vocabulary for MemoStamp.
 * Platform targets resolve these keys to native vectors:
 * - Android: Material Icons or custom Compose ImageVector
 * - iOS: SF Symbols or custom SwiftUI Vector
 *
 * Never persist platform-specific symbol names (e.g. "location.fill", "Icons.Outlined.LocationOn")
 * or emoji glyphs (e.g. "📍") as cross-platform semantic data.
 */
enum class MemoStampIconKey(val key: String) {
    // Platform & Actions
    LOCATION("location"),
    SEARCH("search"),
    CAMERA("camera"),
    GALLERY("gallery"),
    SHARE("share"),
    FAVORITE("favorite"),
    COMMENT("comment"),
    CHAT("chat"),
    SETTINGS("settings"),
    NOTIFICATION("notification"),
    LOCK("lock"),
    PRIVACY("privacy"),
    DELETE("delete"),
    EDIT("edit"),
    CHECK("check"),
    RETRY("retry"),
    SUCCESS("success"),
    WARNING("warning"),
    ERROR("error"),
    INFO("info"),
    CLOUD("cloud"),
    CALENDAR("calendar"),
    MAP("map"),
    QR_CODE("qr_code"),
    REPORT("report"),
    BLOCK("block"),
    CLOSE("close"),
    BACK("back"),
    FILTER("filter"),
    MORE("more"),

    // Postal & MemoStamp Identity
    STAMP("stamp"),
    POSTMARK("postmark"),
    MAIL("mail"),
    REPLY_STAMP("reply_stamp"),
    ALBUM_BOOK("album_book"),
    COLLECTION("collection"),
    PASSPORT("passport"),
    TRADE("trade"),
    FRIENDS("friends"),
    PROFILE("profile"),
    THEME("theme"),

    // Categories & Collections
    TRAVEL("travel"),
    CAFE("cafe"),
    BEACH("beach"),
    EDUCATION("education"),
    NATURE("nature"),
    HEART("heart"),
    ART("art"),
    SPECIAL("special"),
    FLOWER("flower"),
    FOOD("food"),
    LIFESTYLE("lifestyle"),
    CELEBRATION("celebration"),

    // Moods
    HAPPY("happy"),
    LOVE("love"),
    CHILL("chill"),
    EXCITED("excited"),
    NOSTALGIC("nostalgic"),
    PEACEFUL("peaceful");

    companion object {
        private val keyMap = entries.associateBy { it.key.lowercase() }
        private val nameMap = entries.associateBy { it.name.uppercase() }

        fun fromKey(raw: String?): MemoStampIconKey? {
            if (raw == null) return null
            val trimmed = raw.trim()
            return keyMap[trimmed.lowercase()] ?: nameMap[trimmed.uppercase()]
        }

        fun isKnown(raw: String?): Boolean = fromKey(raw) != null
    }
}
