package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import java.time.LocalDate
import java.time.LocalDateTime

interface PlatformClient {
    val platform: Platform

    fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult
    fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus
    fun getVideoAnalytics(platformVideoId: String, accessToken: String, startDate: LocalDate, endDate: LocalDate): PlatformAnalytics
    fun getChannelInfo(accessToken: String): PlatformChannelInfo
    fun refreshToken(refreshToken: String): PlatformTokenResult
    fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult
    fun deleteVideo(platformVideoId: String, accessToken: String): Boolean
}

data class PlatformUploadRequest(
    val fileUrl: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val visibility: String,
    val thumbnailUrl: String?,
    val accessToken: String,
    val platformChannelId: String? = null,
    val fileSize: Long = 0,
    val scheduledAt: LocalDateTime? = null,
)

data class PlatformUploadResult(
    val platformVideoId: String,
    val platformUrl: String,
    val status: String,
)

data class PlatformVideoStatus(
    val platformVideoId: String,
    val status: String,
    val errorMessage: String? = null,
)

data class PlatformAnalytics(
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val subscriberGained: Int,
)

data class PlatformChannelInfo(
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val subscriberCount: Long,
    val profileImageUrl: String?,
)

data class PlatformTokenResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)
