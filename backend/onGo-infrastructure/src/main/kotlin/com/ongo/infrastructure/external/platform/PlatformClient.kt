package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.common.enums.MediaType
import com.ongo.common.exception.PlatformUploadException
import com.ongo.application.publicapi.PlatformToolDefinition
import com.ongo.domain.analytics.RevenueReport
import com.ongo.domain.channel.PlainToken
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files

private val fileDownloadClient: RestClient = RestClient.create()

/**
 * 파일 URL에서 바이너리를 다운로드한다.
 * 플랫폼 업로드 시 fileUrl만으로는 실제 파일 내용을 전송할 수 없으므로
 * 필요한 클라이언트에서 이 함수를 사용한다.
 */
fun downloadFileBytes(fileUrl: String): ByteArray {
    val response = fileDownloadClient.get()
        .uri(fileUrl)
        .retrieve()
        .toEntity(ByteArray::class.java)

    if (!response.statusCode.is2xxSuccessful || response.body == null) {
        throw PlatformUploadException("DOWNLOAD", "파일 다운로드 실패: $fileUrl (status=${response.statusCode})")
    }

    return response.body!!
}

fun downloadFileToTemp(fileUrl: String): java.io.File {
    val tempFile = Files.createTempFile("ongo-cloud-source-", ".upload").toFile()
    try {
        val connection = URI.create(fileUrl).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30 * 60 * 1000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        val status = connection.responseCode
        if (status !in 200..299) {
            throw PlatformUploadException("DOWNLOAD", "파일 다운로드 실패 (status=$status)")
        }
        connection.inputStream.use { input ->
            tempFile.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
        return tempFile
    } catch (e: Exception) {
        Files.deleteIfExists(tempFile.toPath())
        throw e
    }
}

interface PlatformClient {
    val platform: Platform

    fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult
    /**
     * Publish a still image through the provider's native image endpoint.
     * Video-only clients retain a safe default so capability validation can
     * reject them before an external request is attempted.
     */
    fun uploadImage(request: PlatformUploadRequest): PlatformUploadResult =
        throw UnsupportedOperationException("${platform.name} 이미지 게시를 지원하지 않습니다")
    fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus
    fun getVideoAnalytics(platformVideoId: String, accessToken: String, startDate: LocalDate, endDate: LocalDate): PlatformAnalytics

    /**
     * 기간별 일별 광고 수익.
     *
     * **[getVideoAnalytics] 와 분리된 호출이다.** 금전 지표는 별도 OAuth scope 를 요구해
     * 실패 확률이 다르다. 여기서 실패해도 일반 분석 수집은 영향을 받지 않아야 한다.
     *
     * 기본값은 [RevenueReport.UNSUPPORTED] — 수익을 수집하지 않는 플랫폼은 아무것도
     * 구현하지 않아도 정직하게 "미지원"으로 남는다. 0 원을 지어내지 않는다.
     */
    fun getVideoRevenue(
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): RevenueReport = RevenueReport.UNSUPPORTED
    fun getChannelInfo(accessToken: String): PlatformChannelInfo
    fun refreshToken(refreshToken: String): PlatformTokenResult
    fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String? = null): PlatformTokenResult
    fun deleteVideo(platformVideoId: String, accessToken: String): Boolean

    /** 플랫폼에서 영상 메타데이터를 조회하여 최신 상태를 반환 */
    fun getVideoMetadata(platformVideoId: String, accessToken: String): PlatformVideoMetadata? = null

    /** 플랫폼의 영상 메타데이터 업데이트. 미지원 플랫폼은 false 반환 */
    fun updateVideoMetadata(
        platformVideoId: String,
        accessToken: String,
        title: String,
        description: String,
        tags: List<String>,
    ): Boolean = false

    /** 플랫폼에서 사용자의 영상 목록을 조회 */
    fun listVideos(
        accessToken: String,
        platformChannelId: String?,
        maxResults: Int = 20,
        pageToken: String? = null,
    ): PlatformFeedResult = throw UnsupportedOperationException(
        "${platform.name}은(는) 영상 목록 API를 구현하지 않았습니다",
    )

    /** 플랫폼 OAuth 토큰 폐기. 미지원 플랫폼은 false를 반환한다. */
    fun revokeToken(accessToken: String): Boolean = false

    /**
     * Operations exposed through the Postiz-compatible integration tool API.
     * The default operation is deliberately limited to a real provider call;
     * clients add provider-specific operations only when they implement them.
     */
    fun integrationTools(): List<PlatformToolDefinition> = listOf(
        PlatformToolDefinition(
            methodName = "getChannelInfo",
            description = "Fetch the current connected channel profile and follower count",
        ),
    )

    fun invokeIntegrationTool(
        accessToken: String,
        platformChannelId: String?,
        methodName: String,
        data: Map<String, Any?>,
    ): Any? = when (methodName) {
        "getChannelInfo" -> getChannelInfo(accessToken)
        else -> throw IllegalArgumentException("지원하지 않는 ${platform.name} tool입니다: $methodName")
    }

    // --- Comment API (default implementations for unsupported platforms) ---

    fun getCommentCapabilities(): PlatformCommentCapabilities = PlatformCommentCapabilities()

    fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String? = null,
        maxResults: Int = 100,
        publishedAfter: java.time.LocalDateTime? = null,
    ): PlatformCommentListResult = PlatformCommentListResult(emptyList())

    fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String? = null,
    ): PlatformCommentReplyResult = throw UnsupportedOperationException("${platform.name} does not support comment replies")

    fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult = throw UnsupportedOperationException("${platform.name} does not support comment deletion")

    fun likeComment(
        platformCommentId: String,
        accessToken: String,
    ): Boolean = throw UnsupportedOperationException("${platform.name} does not support comment likes")
}

data class PlatformUploadRequest(
    val fileUrl: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val visibility: String,
    val thumbnailUrl: String?,
    val accessToken: PlainToken,
    val platformChannelId: String? = null,
    val fileSize: Long = 0,
    val scheduledAt: LocalDateTime? = null,
    /** Provider-specific Postiz settings preserved through the upload boundary. */
    val customSettingsJson: String? = null,
    val mediaType: MediaType = MediaType.VIDEO,
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
    val platformUrl: String? = null,
)

data class PlatformAnalytics(
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val subscriberGained: Int,
    val impressions: Long = 0,
    val avgViewDurationSeconds: Long = 0,
)

data class PlatformVideoMetadata(
    val title: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val status: String,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
)

data class PlatformFeedItem(
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

data class PlatformFeedResult(
    val items: List<PlatformFeedItem>,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
    /** 외부 목록 조회 실패를 빈 성공 목록과 구분하기 위한 사용자 안전 메시지. */
    val errorMessage: String? = null,
)

data class PlatformChannelInfo(
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    /**
     * 구독자(팔로워) 수. **응답에 그 필드가 없거나 요청하지 않았으면 `null`.**
     *
     * 예전에는 어댑터마다 `?: 0` 을 붙였고 Threads·LinkedIn 은 `0` 을 그대로 박아 넣었다.
     * 그 `0` 은 `channels.subscriber_count` 에 저장돼 **"구독자 0명"** 이라는 관측처럼
     * 흘러다녔다 — 실제로는 물어본 적이 없을 뿐이다.
     *
     * 응답이 실제로 0 을 주면 그것은 관측이므로 `0` 이다.
     */
    val subscriberCount: Long?,
    val profileImageUrl: String?,
)

data class PlatformTokenResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)

// --- Comment data classes ---

data class PlatformCommentCapabilities(
    val canListComments: Boolean = false,
    val canReply: Boolean = false,
    val canLike: Boolean = false,
    val canDelete: Boolean = false,
    val canHide: Boolean = false,
)

data class PlatformComment(
    val platformCommentId: String,
    val parentCommentId: String? = null,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val authorChannelUrl: String? = null,
    val content: String,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val publishedAt: LocalDateTime? = null,
)

data class PlatformCommentListResult(
    val comments: List<PlatformComment>,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
)

data class PlatformCommentReplyResult(
    val platformCommentId: String,
    val success: Boolean = true,
    val errorMessage: String? = null,
)

data class PlatformCommentDeleteResult(
    val success: Boolean = true,
    val errorMessage: String? = null,
)
