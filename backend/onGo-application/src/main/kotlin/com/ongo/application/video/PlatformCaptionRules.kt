package com.ongo.application.video

import com.ongo.common.enums.Platform

/**
 * Text sent to providers that expose one caption field instead of independent
 * title/description fields. Keep this composition identical to the writers so
 * validation prevents silent provider-side truncation.
 */
object PlatformCaptionRules {
    fun compose(platform: Platform, title: String, description: String, tags: List<String>): String? {
        val body = listOf(title.trim(), description.trim())
            .filter(String::isNotBlank)
            .joinToString("\n\n")
        val hashtags = tags
            .map { it.removePrefix("#").trim() }
            .filter(String::isNotBlank)
            .joinToString(" ") { "#$it" }

        return when (platform) {
            Platform.TWITTER, Platform.TIKTOK, Platform.INSTAGRAM, Platform.THREADS -> listOf(body, hashtags)
                .filter(String::isNotBlank)
                .joinToString("\n\n")
            // These clients send the description field (not the provider title)
            // together with hashtags as the post text.
            Platform.FACEBOOK, Platform.LINKEDIN -> listOf(description.trim(), hashtags)
                .filter(String::isNotBlank)
                .joinToString("\n\n")
            else -> null
        }
    }
}
