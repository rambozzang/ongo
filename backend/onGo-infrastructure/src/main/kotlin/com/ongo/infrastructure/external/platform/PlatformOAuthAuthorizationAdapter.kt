package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.PlatformOAuthAuthorizationPort
import com.ongo.domain.channel.PlatformOAuthScopes
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/** Server-owned provider client IDs and scopes for the authenticated UI flow. */
@Component
class PlatformOAuthAuthorizationAdapter(
    @param:Value("\${platform.google.client-id:}") private val googleClientId: String,
    @param:Value("\${platform.tiktok.client-key:}") private val tiktokClientId: String,
    @param:Value("\${platform.instagram.app-id:}") private val instagramClientId: String,
    @param:Value("\${platform.twitter.client-id:}") private val twitterClientId: String,
    @param:Value("\${platform.facebook.app-id:}") private val facebookClientId: String,
    @param:Value("\${platform.threads.app-id:}") private val threadsClientId: String,
    @param:Value("\${platform.pinterest.app-id:}") private val pinterestClientId: String,
    @param:Value("\${platform.linkedin.client-id:}") private val linkedinClientId: String,
    @param:Value("\${platform.wordpress.client-id:}") private val wordpressClientId: String,
    @param:Value("\${platform.tumblr.consumer-key:}") private val tumblrClientId: String,
    @param:Value("\${platform.vimeo.client-id:}") private val vimeoClientId: String,
    @param:Value("\${platform.dailymotion.api-key:}") private val dailymotionClientId: String,
) : PlatformOAuthAuthorizationPort {

    override fun buildAuthorizationUrl(
        platform: Platform,
        redirectUri: String,
        state: String,
        codeChallenge: String?,
    ): String {
        val builder = UriComponentsBuilder.fromUriString(baseUrl(platform))
            .queryParam("client_id", requiredClientId(platform))
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", scopes(platform))
            .queryParam("state", state)

        if (platform == Platform.YOUTUBE) {
            builder.queryParam("access_type", "offline")
            builder.queryParam("prompt", "consent")
        }
        if (platform == Platform.TWITTER) {
            builder.queryParam("code_challenge", codeChallenge)
            builder.queryParam("code_challenge_method", "S256")
        }
        return builder.build().encode().toUriString()
    }

    private fun requiredClientId(platform: Platform): String {
        val value = when (platform) {
            Platform.YOUTUBE -> googleClientId
            Platform.TIKTOK -> tiktokClientId
            Platform.INSTAGRAM -> instagramClientId
            Platform.NAVER_CLIP -> throw unsupportedNaver()
            Platform.TWITTER -> twitterClientId
            Platform.FACEBOOK -> facebookClientId
            Platform.THREADS -> threadsClientId
            Platform.PINTEREST -> pinterestClientId
            Platform.LINKEDIN -> linkedinClientId
            Platform.WORDPRESS -> wordpressClientId
            Platform.TUMBLR -> tumblrClientId
            Platform.VIMEO -> vimeoClientId
            Platform.DAILYMOTION -> dailymotionClientId
        }.trim()
        if (value.isBlank() || listOf("dummy", "placeholder", "change-me", "your-").any(value.lowercase()::contains)) {
            throw BusinessException("OAUTH_NOT_CONFIGURED", "${platform.name} OAuth client가 설정되지 않았습니다")
        }
        return value
    }

    private fun baseUrl(platform: Platform): String = when (platform) {
        Platform.YOUTUBE -> "https://accounts.google.com/o/oauth2/v2/auth"
        Platform.TIKTOK -> "https://www.tiktok.com/v2/auth/authorize/"
        Platform.INSTAGRAM -> "https://api.instagram.com/oauth/authorize"
        Platform.NAVER_CLIP -> throw unsupportedNaver()
        Platform.TWITTER -> "https://twitter.com/i/oauth2/authorize"
        Platform.FACEBOOK -> "https://www.facebook.com/v21.0/dialog/oauth"
        Platform.THREADS -> "https://threads.net/oauth/authorize"
        Platform.PINTEREST -> "https://www.pinterest.com/oauth/"
        Platform.LINKEDIN -> "https://www.linkedin.com/oauth/v2/authorization"
        Platform.WORDPRESS -> "https://public-api.wordpress.com/oauth2/authorize"
        Platform.TUMBLR -> "https://www.tumblr.com/oauth2/authorize"
        Platform.VIMEO -> "https://api.vimeo.com/oauth/authorize"
        Platform.DAILYMOTION -> "https://api.dailymotion.com/oauth/authorize"
    }

    private fun scopes(platform: Platform): String = when (platform) {
        Platform.YOUTUBE -> PlatformOAuthScopes.YOUTUBE
        Platform.TIKTOK -> "video.publish,video.upload,video.list"
        Platform.INSTAGRAM -> "instagram_business_basic,instagram_business_content_publish"
        Platform.NAVER_CLIP -> throw unsupportedNaver()
        Platform.TWITTER -> "tweet.read tweet.write users.read offline.access"
        Platform.FACEBOOK -> "pages_manage_posts,pages_read_engagement,pages_show_list"
        Platform.THREADS -> "threads_basic,threads_content_publish,threads_manage_insights"
        Platform.PINTEREST -> "boards:read,boards:write,pins:read,pins:write"
        Platform.LINKEDIN -> "openid profile w_member_social"
        Platform.WORDPRESS -> "global"
        Platform.TUMBLR -> "basic write offline_access"
        Platform.VIMEO -> "public private upload edit"
        Platform.DAILYMOTION -> "video.manage video.read account.read offline"
    }

    private fun unsupportedNaver() = BusinessException(
        "OAUTH_NOT_SUPPORTED",
        "Naver Clip은 공개 업로드 API가 없어 현재 연동할 수 없습니다",
    )
}
