package com.ongo.api.channel.dto

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
    @field:jakarta.validation.constraints.NotBlank(message = "인가 코드는 필수입니다")
    @field:jakarta.validation.constraints.Size(max = 2048, message = "인가 코드는 최대 2048자까지 입력할 수 있습니다")
    val authorizationCode: String,
    @field:jakarta.validation.constraints.NotBlank(message = "리다이렉트 URI는 필수입니다")
    @field:jakarta.validation.constraints.Size(max = 2048, message = "리다이렉트 URI는 최대 2048자까지 입력할 수 있습니다")
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
