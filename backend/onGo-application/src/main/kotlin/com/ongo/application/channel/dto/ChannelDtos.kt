package com.ongo.application.channel.dto

import com.ongo.common.enums.Platform
import java.time.LocalDateTime

data class ChannelResponse(
    val id: Long,
    val platform: Platform,
    val channelName: String,
    val channelUrl: String?,
    val subscriberCount: Long,
    val profileImageUrl: String?,
    val status: String,
    val tokenStatus: String,
    val connectedAt: LocalDateTime?
)

data class ConnectChannelRequest(
    val authorizationCode: String,
    val redirectUri: String
)

data class ConnectChannelResponse(
    val channel: ChannelResponse
)

data class ChannelListResponse(
    val channels: List<ChannelResponse>,
    val maxAllowed: Int,
    val currentCount: Int
)
