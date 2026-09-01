package com.ongo.domain.channel

import com.ongo.common.enums.Platform
import java.time.LocalDate

/**
 * Domain port interface for platform client operations.
 * Implemented by infrastructure layer (PlatformClientFactory adapter).
 */
interface PlatformClientPort {

    fun getVideoAnalytics(platform: Platform, platformVideoId: String, accessToken: PlainToken, startDate: LocalDate, endDate: LocalDate): PlatformAnalyticsResult

    /**
     * 기간별 일별 광고 수익. **[getVideoAnalytics] 와 별도 호출이다.**
     *
     * 금전 지표는 OAuth scope 가 따로라 실패 확률이 다르다. 이 호출은 예외를 던지지 않고
     * 상태를 돌려준다 — 수익 조회 실패가 같은 주기의 일반 분석 수집을 멈추면 안 된다.
     */
    fun getVideoRevenue(
        platform: Platform,
        platformVideoId: String,
        accessToken: PlainToken,
        startDate: LocalDate,
        endDate: LocalDate,
    ): com.ongo.domain.analytics.RevenueReport

    fun getChannelInfo(platform: Platform, accessToken: PlainToken): PlatformChannelInfoResult

    fun refreshToken(platform: Platform, refreshToken: PlainToken): PlatformTokenRefreshResult

    /** 플랫폼에서 영상 삭제. 성공 시 true, 미지원/실패 시 false */
    fun deleteVideo(platform: Platform, platformVideoId: String, accessToken: PlainToken): Boolean

    /** 플랫폼의 영상 메타데이터(제목, 설명, 태그) 업데이트. 성공 시 true, 미지원/실패 시 false */
    fun updateVideoMetadata(platform: Platform, platformVideoId: String, accessToken: PlainToken, title: String, description: String, tags: List<String>): Boolean

    /** 플랫폼에서 영상 메타데이터 조회 (제목, 설명, 조회수 등). 미지원 시 null */
    fun getVideoMetadata(platform: Platform, platformVideoId: String, accessToken: PlainToken): PlatformVideoMetadataResult?

    /** 플랫폼에서 사용자의 영상 목록을 조회 */
    fun listVideos(platform: Platform, accessToken: PlainToken, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedPortResult

    /** 플랫폼 OAuth 토큰 폐기. 성공 시 true, 실패/미지원 시 false */
    fun revokeToken(platform: Platform, accessToken: PlainToken): Boolean
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
    /**
     * 목록 API가 정상적으로 비어 있는 것이 아니라 외부 조회에 실패했을 때의 표시.
     * 빈 목록만 반환하면 화면이 "콘텐츠 없음"으로 오해하므로 오류 상태를 보존한다.
     */
    val errorMessage: String? = null,
)

data class FeedItemResult(
    val platformVideoId: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val platformUrl: String? = null,
    /**
     * 피드 지표. **플랫폼이 주지 않거나 응답에 없으면 `null`** — `0` 이 아니다.
     *
     * 이 값은 어디에도 저장되지 않고 `/videos/feed` 응답으로 화면에 바로 나간다. 그래서
     * `analytics_daily`(`NOT NULL DEFAULT 0`) 와 달리 **`null` 을 그대로 실어 보낼 수 있다.**
     *
     * 예전에는 `?: 0` 이었다. Instagram 미디어 목록은 조회수를 주지 않는데 그 자리가 `0` 이
     * 되어, 목록 화면이 "조회수 0" 을 그리고 **조회수 정렬에서 모든 Instagram 영상이 맨
     * 아래로 밀렸다.** 응답이 실제로 0 을 주면 그 0 은 관측이므로 그대로 둔다.
     */
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val commentCount: Long? = null,
    val shareCount: Long? = null,
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
    /**
     * 구독자(팔로워) 수. **플랫폼이 그 값을 주지 않으면 `null`** — `0` 이 아니다.
     *
     * Threads·LinkedIn 어댑터는 팔로워 수를 요청조차 하지 않고, 나머지 어댑터도 응답에
     * 필드가 없을 수 있다. 저장·표시 경로가 그 차이를 잃지 않도록 여기서부터 nullable 이다.
     */
    val subscriberCount: Long?,
    val profileImageUrl: String?,
)

data class PlatformTokenRefreshResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)
