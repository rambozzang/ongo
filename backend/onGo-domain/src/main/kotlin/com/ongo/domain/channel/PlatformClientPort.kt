package com.ongo.domain.channel

import com.ongo.common.enums.Platform
import java.time.LocalDate

/**
 * Domain port interface for platform client operations.
 * Implemented by infrastructure layer (PlatformClientFactory adapter).
 */
interface PlatformClientPort {

    fun getVideoAnalytics(platform: Platform, platformVideoId: String, accessToken: String, startDate: LocalDate, endDate: LocalDate): PlatformAnalyticsResult

    fun getChannelInfo(platform: Platform, accessToken: String): PlatformChannelInfoResult

    fun refreshToken(platform: Platform, refreshToken: String): PlatformTokenRefreshResult

    /** 플랫폼에서 영상 삭제. 성공 시 true, 미지원/실패 시 false */
    fun deleteVideo(platform: Platform, platformVideoId: String, accessToken: String): Boolean

    /** 플랫폼의 영상 메타데이터(제목, 설명, 태그) 업데이트. 성공 시 true, 미지원/실패 시 false */
    fun updateVideoMetadata(platform: Platform, platformVideoId: String, accessToken: String, title: String, description: String, tags: List<String>): Boolean

    /** 플랫폼에서 영상 메타데이터 조회 (제목, 설명, 조회수 등). 미지원 시 null */
    fun getVideoMetadata(platform: Platform, platformVideoId: String, accessToken: String): PlatformVideoMetadataResult?

    /** 플랫폼에서 사용자의 영상 목록을 조회 */
    fun listVideos(platform: Platform, accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedPortResult

    /** 플랫폼 OAuth 토큰 폐기. 성공 시 true, 실패/미지원 시 false */
    fun revokeToken(platform: Platform, accessToken: String): Boolean
}

data class PlatformVideoMetadataResult(
    val title: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val status: String,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
)

data class PlatformFeedPortResult(
    val items: List<FeedItemResult>,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
)

data class FeedItemResult(
    val platformVideoId: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val platformUrl: String? = null,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val publishedAt: String? = null,
)

data class PlatformAnalyticsResult(
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val subscriberGained: Int,
    val impressions: Long = 0,
    val avgViewDurationSeconds: Long = 0,
)

data class PlatformChannelInfoResult(
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val subscriberCount: Long,
    val profileImageUrl: String?,
)

data class PlatformTokenRefreshResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)
