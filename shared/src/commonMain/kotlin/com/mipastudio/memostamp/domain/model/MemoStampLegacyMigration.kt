package com.mipastudio.memostamp.domain.model

/**
 * Deterministic legacy migration mapping for MemoStamp.
 * Converts legacy emoji representations, string variants, and legacy codes
 * to bounded semantic icon keys.
 *
 * Requirements:
 * - Existing collections and stamps survive without corruption.
 * - Legacy emoji mappings are isolated inside this migration file.
 * - Unknown legacy emoji falls back safely to neutral defaults.
 */
object MemoStampLegacyMigration {

    /**
     * Maps legacy collection emoji / keys to semantic collection icon key.
     * Guaranteed fallback to "collection" for any unknown or corrupted values.
     */
    fun mapLegacyCollectionIcon(legacy: String?): String {
        if (legacy == null || legacy.isBlank()) return MemoStampIconKey.COLLECTION.key

        val clean = legacy.trim()

        // Exact match against semantic keys first
        val directMatch = MemoStampIconKey.fromKey(clean)
        if (directMatch != null) return directMatch.key

        return when (clean) {
            "📁", "folder", "album", "NORMAL" -> MemoStampIconKey.COLLECTION.key
            "✈️", "✈", "plane", "flight" -> MemoStampIconKey.TRAVEL.key
            "☕", "coffee", "cafe" -> MemoStampIconKey.CAFE.key
            "🏖️", "🏖", "beach" -> MemoStampIconKey.BEACH.key
            "🎓", "graduation", "education" -> MemoStampIconKey.EDUCATION.key
            "📮", "stamp", "post", "mailbox" -> MemoStampIconKey.STAMP.key
            "🏞️", "🏞", "mountain", "nature", "landscape" -> MemoStampIconKey.NATURE.key
            "📸", "📷", "camera", "photo" -> MemoStampIconKey.CAMERA.key
            "💖", "♡", "❤️", "heart", "love" -> MemoStampIconKey.HEART.key
            "🌲", "tree", "forest" -> MemoStampIconKey.NATURE.key
            "🎨", "art", "palette" -> MemoStampIconKey.ART.key
            "👑", "crown", "star", "SERIES" -> MemoStampIconKey.SPECIAL.key
            "🌸", "✿", "flower", "cherry", "sakura" -> MemoStampIconKey.FLOWER.key
            "🍔", "food", "burger" -> MemoStampIconKey.FOOD.key
            "🌿", "leaf", "plant", "sage", "daily" -> MemoStampIconKey.LIFESTYLE.key
            "🎉", "celebration", "party", "milestone" -> MemoStampIconKey.CELEBRATION.key
            else -> MemoStampIconKey.COLLECTION.key
        }
    }

    fun resolveCollectionIcon(keyOrEmoji: String?): String = mapLegacyCollectionIcon(keyOrEmoji)

    /**
     * Maps legacy mood emoji / strings to semantic mood identifier.
     * Guaranteed fallback to "special" for any unknown or empty values.
     */
    fun mapLegacyMood(legacy: String?): String {
        if (legacy == null || legacy.isBlank()) return MemoStampIconKey.SPECIAL.key

        val clean = legacy.trim()

        // Check if already matches a semantic key
        val directMatch = MemoStampIconKey.fromKey(clean)
        if (directMatch != null) {
            when (directMatch) {
                MemoStampIconKey.HAPPY,
                MemoStampIconKey.LOVE,
                MemoStampIconKey.TRAVEL,
                MemoStampIconKey.CHILL,
                MemoStampIconKey.EXCITED,
                MemoStampIconKey.NOSTALGIC,
                MemoStampIconKey.PEACEFUL,
                MemoStampIconKey.SPECIAL -> return directMatch.key
                else -> {}
            }
        }

        // Check legacy emoji strings and combined labels (e.g. "😊 Happy", "❤️ Love", etc.)
        val upper = clean.uppercase()
        return when {
            clean.contains("😊") || upper.contains("HAPPY") -> MemoStampIconKey.HAPPY.key
            clean.contains("❤️") || clean.contains("💖") || upper.contains("LOVE") -> MemoStampIconKey.LOVE.key
            clean.contains("✈️") || clean.contains("✈") || upper.contains("TRAVEL") -> MemoStampIconKey.TRAVEL.key
            clean.contains("☕") || upper.contains("CHILL") || upper.contains("COFFEE") -> MemoStampIconKey.CHILL.key
            clean.contains("🔥") || upper.contains("EXCITED") -> MemoStampIconKey.EXCITED.key
            clean.contains("📜") || clean.contains("🕰️") || clean.contains("🕰") || upper.contains("NOSTALGIC") -> MemoStampIconKey.NOSTALGIC.key
            clean.contains("🌿") || upper.contains("PEACEFUL") -> MemoStampIconKey.PEACEFUL.key
            clean.contains("✨") || clean.contains("⭐") || upper.contains("SPECIAL") -> MemoStampIconKey.SPECIAL.key
            else -> MemoStampIconKey.SPECIAL.key
        }
    }

    /**
     * Maps legacy notification emoji to semantic notification icon key.
     */
    fun mapLegacyNotification(legacy: String?): String {
        if (legacy == null || legacy.isBlank()) return MemoStampIconKey.NOTIFICATION.key

        val clean = legacy.trim()
        val direct = MemoStampIconKey.fromKey(clean)
        if (direct != null) return direct.key

        return when {
            clean.contains("💬") || clean.equals("chat", ignoreCase = true) -> MemoStampIconKey.CHAT.key
            clean.contains("🤝") || clean.equals("friend", ignoreCase = true) -> MemoStampIconKey.FRIENDS.key
            clean.contains("📮") || clean.equals("stamp", ignoreCase = true) -> MemoStampIconKey.STAMP.key
            clean.contains("🎉") || clean.equals("success", ignoreCase = true) -> MemoStampIconKey.SUCCESS.key
            clean.contains("⚠️") || clean.equals("warning", ignoreCase = true) -> MemoStampIconKey.WARNING.key
            clean.contains("✉️") || clean.contains("✉") || clean.equals("mail", ignoreCase = true) -> MemoStampIconKey.MAIL.key
            else -> MemoStampIconKey.NOTIFICATION.key
        }
    }
}
