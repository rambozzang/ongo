package com.ongo.infrastructure.external.tumblr

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.tumblr.dto.TumblrNpfPostRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class TumblrClient(
    private val tumblrApi: TumblrApi,
    private val tumblrOAuthApi: TumblrOAuthApi,
    private val tumblrConfig: TumblrConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(TumblrClient::class.java)

    override val platform: Platform = Platform.TUMBLR

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Tumblr 영상 업로드 시작: title={}", request.title)

        val blogName = request.platformChannelId
            ?: throw PlatformUploadException("Tumblr", "블로그 이름이 필요합니다")

        try {
            val npfPost = TumblrNpfPostRequest(
                content = listOf(
                    TumblrNpfPostRequest.ContentBlock(
                        type = "video",
                        url = request.fileUrl,
                    ),
                    TumblrNpfPostRequest.ContentBlock(
                        type = "text",
                        text = request.title,
                    ),
                ),
                tags = request.tags.joinToString(","),
                state = mapVisibility(request.visibility),
            )

            val response = tumblrApi.createPost(
                blogName = blogName,
                authorization = "Bearer ${request.accessToken}",
                request = npfPost,
            )

            val postId = response.response?.idString ?: response.response?.id?.toString()
                ?: throw PlatformUploadException("Tumblr", "게시물 생성 응답에 ID가 없습니다")

            log.info("Tumblr 업로드 완료: postId={}", postId)

            return PlatformUploadResult(
                platformVideoId = postId,
                platformUrl = "https://$blogName.tumblr.com/post/$postId",
                status = "published",
            )
        } catch (e: PlatformUploadException) {
            throw e
        } catch (e: Exception) {
            log.error("Tumblr 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Tumblr", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Tumblr 게시물 상태 조회: postId={}", platformVideoId)

        return PlatformVideoStatus(
            platformVideoId = platformVideoId,
            status = "published",
        )
    }

    override fun getVideoAnalytics(
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PlatformAnalytics {
        log.debug("Tumblr 분석 데이터 조회: postId={}", platformVideoId)

        return try {
            val response = tumblrApi.getPostNotes(
                blogName = "me",
                postId = platformVideoId,
                authorization = "Bearer $accessToken",
            )

            var likes = 0L
            var reblogs = 0L
            var replies = 0L

            response.response?.notes?.forEach { note ->
                when (note.type) {
                    "like" -> likes++
                    "reblog" -> reblogs++
                    "reply" -> replies++
                }
            }

            PlatformAnalytics(
                views = response.response?.totalNotes ?: 0,
                likes = likes,
                comments = replies,
                shares = reblogs,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Tumblr 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Tumblr 사용자 정보 조회")

        val response = tumblrApi.getUserInfo(
            authorization = "Bearer $accessToken",
        )

        val primaryBlog = response.response?.user?.blogs?.firstOrNull { it.primary == true }
            ?: response.response?.user?.blogs?.firstOrNull()
            ?: throw PlatformUploadException("Tumblr", "블로그 정보를 가져올 수 없습니다")

        val avatar = primaryBlog.avatar
            ?.maxByOrNull { it.width ?: 0 }?.url

        return PlatformChannelInfo(
            channelId = primaryBlog.name ?: "",
            channelName = primaryBlog.title ?: primaryBlog.name ?: "",
            channelUrl = primaryBlog.url ?: "",
            subscriberCount = primaryBlog.followers ?: 0,
            profileImageUrl = avatar,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Tumblr OAuth 인가 코드 교환")

        val response = tumblrOAuthApi.exchangeToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to authorizationCode,
                "redirect_uri" to redirectUri,
                "client_id" to tumblrConfig.getConsumerKey(),
                "client_secret" to tumblrConfig.getConsumerSecret(),
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("Tumblr OAuth 토큰 갱신")

        val response = tumblrOAuthApi.exchangeToken(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to tumblrConfig.getConsumerKey(),
                "client_secret" to tumblrConfig.getConsumerSecret(),
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("Tumblr 게시물 삭제: postId={}", platformVideoId)

        return try {
            tumblrApi.deletePost(
                blogName = "me",
                authorization = "Bearer $accessToken",
                body = mapOf("id" to platformVideoId),
            )
            true
        } catch (e: Exception) {
            log.error("Tumblr 게시물 삭제 실패: {}", e.message)
            false
        }
    }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("Tumblr notes 조회: postId={}", platformVideoId)

        return try {
            // platformVideoId format: "blogName:postId"
            val parts = platformVideoId.split(":")
            val blogName = parts.getOrElse(0) { "" }
            val postId = parts.getOrElse(1) { platformVideoId }

            val response = tumblrApi.getPostNotes(
                blogName = blogName,
                postId = postId,
                authorization = "Bearer $accessToken",
            )

            val comments = response.response?.notes
                ?.filter { it.type == "reply" }
                ?.mapNotNull { note ->
                    PlatformComment(
                        platformCommentId = "${note.blogName}_${note.timestamp}",
                        authorName = note.blogName ?: "Unknown",
                        authorChannelUrl = note.blogUrl,
                        content = note.replyText ?: "",
                        publishedAt = note.timestamp?.let {
                            java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        },
                    )
                } ?: emptyList()

            PlatformCommentListResult(
                comments = comments.take(maxResults),
                totalCount = response.response?.totalNotes?.toInt(),
            )
        } catch (e: Exception) {
            log.warn("Tumblr notes 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    private fun mapVisibility(visibility: String): String =
        when (visibility.uppercase()) {
            "PUBLIC" -> "published"
            "PRIVATE" -> "private"
            "UNLISTED" -> "draft"
            else -> "draft"
        }
}
