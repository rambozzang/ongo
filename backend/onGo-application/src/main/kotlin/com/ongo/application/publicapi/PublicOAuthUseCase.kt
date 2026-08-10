package com.ongo.application.publicapi

import com.ongo.application.channel.ChannelUseCase
import com.ongo.application.channel.dto.ConnectChannelRequest
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.ChannelRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Owns the public API OAuth flow. The API key identifies the onGo user when
 * the URL is issued; the signed, short-lived state carries that identity to
 * the unauthenticated provider callback without putting a token in the URL.
 */
@Service
class PublicOAuthUseCase(
    private val channelRepository: ChannelRepository,
    private val channelUseCase: ChannelUseCase,
    @param:Value("\${public-api.oauth.callback-url:http://localhost:8070/api/v1/public/v1/social/callback}")
    private val callbackUrl: String,
    @param:Value("\${public-api.oauth.success-redirect:/channels}")
    private val successRedirect: String,
    @param:Value("\${ongo.content-source.google-drive.oauth-state-secret:}")
    private val stateSecret: String,
    @param:Value("\${platform.google.client-id:}") private val googleClientId: String,
    @param:Value("\${platform.tiktok.client-key:}") private val tiktokClientId: String,
    @param:Value("\${platform.instagram.app-id:}") private val instagramClientId: String,
    @param:Value("\${platform.naver.client-id:}") private val naverClientId: String,
    @param:Value("\${platform.twitter.client-id:}") private val twitterClientId: String,
    @param:Value("\${platform.facebook.app-id:}") private val facebookClientId: String,
    @param:Value("\${platform.threads.app-id:}") private val threadsClientId: String,
    @param:Value("\${platform.pinterest.app-id:}") private val pinterestClientId: String,
    @param:Value("\${platform.linkedin.client-id:}") private val linkedinClientId: String,
    @param:Value("\${platform.wordpress.client-id:}") private val wordpressClientId: String,
    @param:Value("\${platform.tumblr.consumer-key:}") private val tumblrClientId: String,
    @param:Value("\${platform.vimeo.client-id:}") private val vimeoClientId: String,
    @param:Value("\${platform.dailymotion.api-key:}") private val dailymotionClientId: String,
) {
    /**
     * 공개 API OAuth callback은 외부 provider가 호출하므로 state를 한 번만
     * 소비해야 한다. 단일 인스턴스 운영 정책에 맞춰 짧은 TTL의
     * 인메모리 저장소를 사용한다.
     */
    private val consumedStates = ConcurrentHashMap<String, Long>()

    fun authorizationUrl(userId: Long, integration: String, refresh: String?): PublicOAuthUrlResponse {
        val platform = parsePlatform(integration)
        requireSupportedProvider(platform)
        refresh?.let { refreshChannelId ->
            val channelId = refreshChannelId.toLongOrNull()
                ?: throw IllegalArgumentException("refresh는 onGo 채널 ID여야 합니다")
            val channel = channelRepository.findById(channelId)
                ?.takeIf { it.userId == userId }
                ?: throw NotFoundException("integration", refreshChannelId)
            require(channel.platform == platform) { "refresh integration의 플랫폼이 일치하지 않습니다" }
        }
        requireStateSecret()

        val verifier = randomUrlSafe(32)
        val state = signState(userId, platform, verifier)
        val parameters = linkedMapOf(
            "client_id" to requiredClientId(platform),
            "redirect_uri" to callbackUrl,
            "response_type" to "code",
            "state" to state,
        )
        if (platform == Platform.TWITTER) {
            parameters["code_challenge"] = codeChallenge(verifier)
            parameters["code_challenge_method"] = "S256"
        }
        parameters["scope"] = scopes(platform)
        return PublicOAuthUrlResponse(buildAuthorizationUrl(platform, parameters))
    }

    fun complete(code: String, state: String): String {
        require(code.isNotBlank()) { "OAuth code가 없습니다" }
        val payload = verifyState(state)
        requireSupportedProvider(payload.platform)
        channelUseCase.connectChannel(
            userId = payload.userId,
            platformStr = payload.platform.name,
            request = ConnectChannelRequest(
                authorizationCode = code,
                redirectUri = callbackUrl,
                codeVerifier = payload.verifier,
            ),
        )
        return redirect(successRedirect, "connected", payload.platform.name.lowercase())
    }

    fun failure(state: String?, providerError: String?): String? {
        val payload = state?.let { runCatching { verifyState(it) }.getOrNull() } ?: return null
        // Never reflect provider error text into a redirect URL. It may contain
        // credentials or HTML supplied by the provider.
        return redirect(successRedirect, "failed", payload.platform.name.lowercase())
    }

    private fun parsePlatform(value: String): Platform {
        val normalized = value.trim().uppercase().replace('-', '_')
        return runCatching { Platform.valueOf(normalized) }
            .getOrElse { throw IllegalArgumentException("지원하지 않는 OAuth integration입니다: $value") }
    }

    private fun requireSupportedProvider(platform: Platform) {
        if (platform == Platform.NAVER_CLIP) {
            throw BusinessException(
                "OAUTH_NOT_SUPPORTED",
                "Naver Clip은 공개 업로드 API가 없어 현재 연동할 수 없습니다",
            )
        }
    }

    private fun requiredClientId(platform: Platform): String {
        val clientId = when (platform) {
            Platform.YOUTUBE -> googleClientId
            Platform.TIKTOK -> tiktokClientId
            Platform.INSTAGRAM -> instagramClientId
            Platform.NAVER_CLIP -> naverClientId
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
        if (clientId.isBlank() || clientId.startsWith("your-", ignoreCase = true) || clientId.startsWith("dummy-")) {
            throw BusinessException("OAUTH_NOT_CONFIGURED", "${platform.name} OAuth client가 설정되지 않았습니다")
        }
        return clientId
    }

    private fun buildAuthorizationUrl(platform: Platform, parameters: Map<String, String>): String {
        val base = when (platform) {
            Platform.YOUTUBE -> "https://accounts.google.com/o/oauth2/v2/auth"
            Platform.TIKTOK -> "https://www.tiktok.com/v2/auth/authorize/"
            Platform.INSTAGRAM -> "https://api.instagram.com/oauth/authorize"
            Platform.NAVER_CLIP -> "https://nid.naver.com/oauth2.0/authorize"
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
        return UriComponentsBuilder.fromUriString(base)
            .queryParam("client_id", parameters["client_id"])
            .queryParam("redirect_uri", parameters["redirect_uri"])
            .queryParam("response_type", parameters["response_type"])
            .queryParam("state", parameters["state"])
            .queryParam("scope", parameters["scope"])
            .apply {
                parameters["code_challenge"]?.let { queryParam("code_challenge", it) }
                parameters["code_challenge_method"]?.let { queryParam("code_challenge_method", it) }
                if (platform == Platform.YOUTUBE) queryParam("access_type", "offline")
            }
            .build()
            .encode()
            .toUriString()
    }

    private fun scopes(platform: Platform): String = when (platform) {
        Platform.YOUTUBE -> "https://www.googleapis.com/auth/youtube"
        // Direct Post requires video.publish; video.upload is kept for the
        // draft/upload flow used by accounts that are not approved for direct
        // posting yet.
        Platform.TIKTOK -> "video.publish,video.upload,video.list"
        Platform.INSTAGRAM -> "instagram_business_basic,instagram_business_content_publish"
        Platform.NAVER_CLIP -> ""
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

    private fun signState(userId: Long, platform: Platform, verifier: String): String {
        val payload = listOf("1", userId.toString(), platform.name, verifier, (System.currentTimeMillis() + STATE_TTL_MS).toString()).joinToString("|")
        val encoded = encode(payload.toByteArray(StandardCharsets.UTF_8))
        return "$encoded.${encode(sign(encoded.toByteArray(StandardCharsets.UTF_8)))}"
    }

    private fun verifyState(state: String): OAuthState {
        requireStateSecret()
        val pieces = state.split('.')
        require(pieces.size == 2) { "OAuth state가 올바르지 않습니다" }
        val encodedPayload = pieces[0]
        val actualSignature = Base64.getUrlDecoder().decode(pieces[1])
        val expectedSignature = sign(encodedPayload.toByteArray(StandardCharsets.UTF_8))
        require(MessageDigest.isEqual(actualSignature, expectedSignature)) { "OAuth state 서명이 올바르지 않습니다" }
        val values = String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8).split('|')
        require(values.size == 5 && values[0] == "1") { "OAuth state 형식이 올바르지 않습니다" }
        val expiresAt = values[4].toLongOrNull() ?: 0L
        require(expiresAt > System.currentTimeMillis()) { "OAuth state가 만료되었습니다" }
        require(consumeStateOnce(state)) { "이미 사용된 OAuth state입니다. CSRF 공격일 수 있습니다." }
        return OAuthState(values[1].toLongOrNull() ?: 0L, Platform.valueOf(values[2]), values[3])
    }

    private fun consumeStateOnce(state: String): Boolean {
        val now = Instant.now().toEpochMilli()
        consumedStates.entries.removeIf { now - it.value > STATE_TTL_MS }
        return consumedStates.putIfAbsent(state, now) == null
    }

    private fun sign(value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(stateSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        doFinal(value)
    }

    private fun codeChallenge(verifier: String): String = encode(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
    )

    private fun randomUrlSafe(bytes: Int): String {
        val value = ByteArray(bytes)
        SecureRandom().nextBytes(value)
        return encode(value)
    }

    private fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun redirect(base: String, status: String, platform: String): String =
        UriComponentsBuilder.fromUriString(base)
            .queryParam("channel", status)
            .queryParam("platform", platform)
            .build()
            .encode()
            .toUriString()

    private fun requireStateSecret() {
        require(stateSecret.length >= 32) { "OAuth state secret이 설정되지 않았습니다" }
    }

    private data class OAuthState(val userId: Long, val platform: Platform, val verifier: String)

    companion object {
        private const val STATE_TTL_MS = 10 * 60 * 1_000L
    }
}
