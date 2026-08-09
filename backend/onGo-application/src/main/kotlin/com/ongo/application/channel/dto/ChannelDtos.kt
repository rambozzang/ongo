package com.ongo.application.channel.dto

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelStatus
import java.time.LocalDateTime

data class ChannelResponse(
    val id: Long,
    val platform: Platform,
    val channelName: String,
    val channelUrl: String?,
    val subscriberCount: Long,
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
