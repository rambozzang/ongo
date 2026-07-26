package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
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
    fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus
    fun getVideoAnalytics(platformVideoId: String, accessToken: String, startDate: LocalDate, endDate: LocalDate): PlatformAnalytics
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
    ): PlatformFeedResult = PlatformFeedResult(emptyList())

    /** 플랫폼 OAuth 토큰 폐기. 지원하지 않는 플랫폼은 true 반환 */
    fun revokeToken(accessToken: String): Boolean = true

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
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val publishedAt: String? = null,
)

data class PlatformFeedResult(
    val items: List<PlatformFeedItem>,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
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
