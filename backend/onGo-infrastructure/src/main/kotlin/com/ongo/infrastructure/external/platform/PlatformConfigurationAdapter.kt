package com.ongo.infrastructure.external.platform

import com.ongo.application.platform.PlatformConfigurationPort
import com.ongo.application.platform.PlatformConfigurationStatus
import com.ongo.common.enums.Platform
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Converts deployment credentials into an explicit provider availability
 * contract. Missing credentials are not treated as a temporary provider
 * failure: exposing an OAuth button with an empty client id only creates a
 * predictable, avoidable failure for the user.
 */
@Component
class PlatformConfigurationAdapter(
    @param:Value("\${platform.google.client-id:}") private val googleClientId: String,
    @param:Value("\${platform.google.client-secret:}") private val googleClientSecret: String,
    @param:Value("\${platform.tiktok.client-key:}") private val tiktokClientKey: String,
    @param:Value("\${platform.tiktok.client-secret:}") private val tiktokClientSecret: String,
    @param:Value("\${platform.instagram.app-id:}") private val instagramAppId: String,
    @param:Value("\${platform.instagram.app-secret:}") private val instagramAppSecret: String,
    @param:Value("\${platform.twitter.client-id:}") private val twitterClientId: String,
    @param:Value("\${platform.twitter.client-secret:}") private val twitterClientSecret: String,
    @param:Value("\${platform.facebook.app-id:}") private val facebookAppId: String,
    @param:Value("\${platform.facebook.app-secret:}") private val facebookAppSecret: String,
    @param:Value("\${platform.threads.app-id:}") private val threadsAppId: String,
    @param:Value("\${platform.threads.app-secret:}") private val threadsAppSecret: String,
    @param:Value("\${platform.pinterest.app-id:}") private val pinterestAppId: String,
    @param:Value("\${platform.pinterest.app-secret:}") private val pinterestAppSecret: String,
    @param:Value("\${platform.linkedin.client-id:}") private val linkedinClientId: String,
    @param:Value("\${platform.linkedin.client-secret:}") private val linkedinClientSecret: String,
    @param:Value("\${platform.wordpress.client-id:}") private val wordpressClientId: String,
    @param:Value("\${platform.wordpress.client-secret:}") private val wordpressClientSecret: String,
    @param:Value("\${platform.dailymotion.api-key:}") private val dailymotionApiKey: String,
    @param:Value("\${platform.dailymotion.api-secret:}") private val dailymotionApiSecret: String,
    @param:Value("\${platform.vimeo.client-id:}") private val vimeoClientId: String,
    @param:Value("\${platform.vimeo.client-secret:}") private val vimeoClientSecret: String,
    @param:Value("\${platform.tumblr.consumer-key:}") private val tumblrConsumerKey: String,
    @param:Value("\${platform.tumblr.consumer-secret:}") private val tumblrConsumerSecret: String,
) : PlatformConfigurationPort {

    override fun status(platform: Platform): PlatformConfigurationStatus = when (platform) {
        Platform.YOUTUBE -> status(platform, googleClientId, googleClientSecret)
        Platform.TIKTOK -> status(platform, tiktokClientKey, tiktokClientSecret)
        Platform.INSTAGRAM -> status(platform, instagramAppId, instagramAppSecret)
        Platform.NAVER_CLIP -> PlatformConfigurationStatus(
            configured = false,
            reason = "네이버 클립은 공개 업로드·분석 API가 없어 현재 연동할 수 없습니다.",
        )
        Platform.TWITTER -> status(platform, twitterClientId, twitterClientSecret)
        Platform.FACEBOOK -> status(platform, facebookAppId, facebookAppSecret)
        Platform.THREADS -> status(platform, threadsAppId, threadsAppSecret)
        Platform.PINTEREST -> status(platform, pinterestAppId, pinterestAppSecret)
        Platform.LINKEDIN -> status(platform, linkedinClientId, linkedinClientSecret)
        Platform.WORDPRESS -> status(platform, wordpressClientId, wordpressClientSecret)
        Platform.DAILYMOTION -> status(platform, dailymotionApiKey, dailymotionApiSecret)
        Platform.VIMEO -> status(platform, vimeoClientId, vimeoClientSecret)
        Platform.TUMBLR -> status(platform, tumblrConsumerKey, tumblrConsumerSecret)
    }

    private fun status(platform: Platform, vararg values: String): PlatformConfigurationStatus {
        if (values.all(::isConfiguredValue)) return PlatformConfigurationStatus(configured = true)
        return PlatformConfigurationStatus(
            configured = false,
            reason = "${displayName(platform)} 플랫폼 연동 설정이 운영 서버에 구성되지 않았습니다.",
        )
    }

    private fun displayName(platform: Platform): String = when (platform) {
        Platform.YOUTUBE -> "YouTube"
        Platform.TIKTOK -> "TikTok"
        Platform.INSTAGRAM -> "Instagram"
        Platform.NAVER_CLIP -> "네이버 클립"
        Platform.TWITTER -> "X"
        Platform.FACEBOOK -> "Facebook"
        Platform.THREADS -> "Threads"
        Platform.PINTEREST -> "Pinterest"
        Platform.LINKEDIN -> "LinkedIn"
        Platform.WORDPRESS -> "WordPress"
        Platform.DAILYMOTION -> "Dailymotion"
        Platform.VIMEO -> "Vimeo"
        Platform.TUMBLR -> "Tumblr"
    }

    private fun isConfiguredValue(value: String): Boolean {
        val normalized = value.trim().lowercase()
        // A short value is almost always a copied placeholder or an incomplete
        // secret. Treat it as unavailable so the UI cannot advertise an OAuth
        // flow that will predictably fail at the provider.
        return normalized.length >= MIN_CREDENTIAL_LENGTH &&
            listOf("dummy", "placeholder", "change-me", "your-", "localhost").none(normalized::contains)
    }

    private companion object {
        const val MIN_CREDENTIAL_LENGTH = 8
    }
}
