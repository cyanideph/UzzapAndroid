package com.kolipri.uzzap.data

import android.net.Uri

/**
 * Uzzap avatar selection backed by DiceBear's deterministic HTTP API.
 * Only curated style names are accepted so a stored profile value cannot
 * become an arbitrary remote URL.
 */
object AvatarManager {
    data class AvatarOption(val style: String, val label: String)

    val options = listOf(
        AvatarOption("pixel-art", "Pixel Art"),
        AvatarOption("bottts", "Bottts"),
        AvatarOption("lorelei", "Lorelei"),
        AvatarOption("adventurer", "Adventurer"),
        AvatarOption("thumbs", "Thumbs"),
        AvatarOption("initials", "Initials")
    )

    private val allowedStyles = options.map { it.style }.toSet()

    fun encode(style: String, seed: String): String {
        val safeStyle = style.takeIf { it in allowedStyles } ?: options.first().style
        return "dicebear:${safeStyle}:${seed.trim()}"
    }

    fun style(value: String?): String? {
        val parts = value.orEmpty().split(":", limit = 3)
        return parts.getOrNull(1)?.takeIf { it in allowedStyles }
    }

    fun seed(value: String?, fallback: String): String {
        val parts = value.orEmpty().split(":", limit = 3)
        return parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun url(value: String?, fallbackSeed: String): String {
        val style = style(value) ?: options.first().style
        val seed = seed(value, fallbackSeed)
        return "https://api.dicebear.com/10.x/$style/png?seed=${Uri.encode(seed)}&size=256&radius=50"
    }
}
