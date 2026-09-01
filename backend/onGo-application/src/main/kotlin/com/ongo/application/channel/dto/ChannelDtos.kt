package com.ongo.application.channel.dto

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelStatus
import java.time.LocalDateTime

data class ChannelResponse(
    val id: Long,
    val platform: Platform,
    val channelName: String,
    val channelUrl: String?,
    /**
     * 구독자(팔로워) 수. **그 플랫폼이 조회하지 않으면 `null`.**
     *
     * `ThreadsClient.kt:205` 와 `LinkedInClient.kt:264` 는 팔로워 수를 묻지도 않고
     * `subscriberCount = 0` 을 박아 넣고, `NaverClipClient.kt:40` 은 조회 자체가 예외다.
     * 저장 컬럼이 `Long` non-null 이라 그 자리에 `0` 이 남는데, 그것은 **묻지 않아서
     * 남은 기본값**이지 "구독자가 없다" 가 아니다. 화면이 그 `0` 을 "구독자 0명" 으로
     * 그렸다.
     *
     * 조회하는 플랫폼의 `0` 은 그대로 `0` 이다 — 갓 만든 채널의 구독자 0 명은 관측이다.
     */
    val subscriberCount: Long?,
    val profileImageUrl: String?,
    val status: ChannelStatus,
    val tokenStatus: String,
    val connectedAt: LocalDateTime?,
    val lastSyncedAt: LocalDateTime?,
    val tokenExpiresAt: LocalDateTime?,
)

data class ConnectChannelRequest(
    val authorizationCode: String,
    val redirectUri: String,
    /** 서버가 발급하고 사용자·플랫폼·redirect URI에 바인딩한 OAuth state */
    val state: String,
    /** Twitter OAuth 2.0 PKCE code_verifier (Twitter 연동 시 필수) */
    val codeVerifier: String? = null,
    /** 같은 플랫폼의 다른 계정을 추가할 때 true. 기본값은 기존 계정 재연결이다. */
    val addAsNew: Boolean = false,
)

data class ConnectChannelResponse(
    val channel: ChannelResponse
)

data class ChannelListResponse(
    val channels: List<ChannelResponse>,
    val maxAllowed: Int,
    val currentCount: Int
)
