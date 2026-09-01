package com.ongo.infrastructure.external.tumblr

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformApiException
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.tumblr.dto.TumblrNpfPostRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import java.time.LocalDate

@Component
class TumblrClient(
    private val tumblrApi: TumblrApi,
    private val tumblrOAuthApi: TumblrOAuthApi,
    private val tumblrConfig: TumblrConfig,
    private val objectMapper: ObjectMapper,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(TumblrClient::class.java)

    override val platform: Platform = Platform.TUMBLR

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Tumblr 영상 업로드 시작: title={}", request.title)

        val blogName = request.platformChannelId
            ?: throw PlatformUploadException("Tumblr", "블로그 이름이 필요합니다")
        val sourceFile = downloadFileToTemp(request.fileUrl)

        try {
            val npfPost = TumblrNpfPostRequest(
                content = listOf(
                    TumblrNpfPostRequest.ContentBlock(
                        type = "video",
                        media = TumblrNpfPostRequest.Media(
                            type = "video/mp4",
                            identifier = "ongo-video",
                        ),
                    ),
                    TumblrNpfPostRequest.ContentBlock(
                        type = "text",
                        text = listOf(request.title.trim(), request.description.trim())
                            .filter(String::isNotBlank)
                            .joinToString("\n\n"),
                    ),
                ),
                tags = request.tags.map { it.removePrefix("#").trim() }
                    .filter(String::isNotBlank)
                    .joinToString(",")
                    .take(500),
                state = mapVisibility(request.visibility),
            )

            val responseBody = fileTransferHelper.postMultipartJsonWithFile(
                url = "${tumblrConfig.getApiBaseUrl()}/v2/blog/$blogName/posts",
                authorization = "Bearer ${request.accessToken.value}",
                jsonBody = objectMapper.writeValueAsString(npfPost),
                filePartName = "ongo-video",
                file = sourceFile,
            )
            val response = objectMapper.readValue(responseBody, com.ongo.infrastructure.external.tumblr.dto.TumblrPostResponse::class.java)

            val postId = response.response?.idString ?: response.response?.id?.toString()
                ?: throw PlatformUploadException("Tumblr", "게시물 생성 응답에 ID가 없습니다")

            log.info("Tumblr 업로드 완료: postId={}", postId)

            return PlatformUploadResult(
                // 상태/분석 API가 blog 이름을 요구하므로 외부 식별자에 함께
                // 보관한다. 내부 DB에는 provider가 돌려준 ID를 그대로 잃지 않는다.
                platformVideoId = "$blogName:$postId",
                platformUrl = "https://$blogName.tumblr.com/post/$postId",
                status = "published",
            )
        } catch (e: PlatformUploadException) {
            throw e
        } catch (e: Exception) {
            log.error("Tumblr 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Tumblr", e.message ?: "알 수 없는 오류", e)
        } finally {
            sourceFile.delete()
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Tumblr 게시물 상태 조회: postId={}", platformVideoId)
        val parts = platformVideoId.split(":", limit = 2)
        val blogName = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: throw PlatformApiException("Tumblr", "블로그 식별자가 없는 게시 ID입니다.")
        val postId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: throw PlatformApiException("Tumblr", "게시물 식별자가 없는 게시 ID입니다.")
        val response = tumblrApi.getPost(
            blogName = blogName,
            postId = postId,
            authorization = "Bearer $accessToken",
        )
        val post = response.response
            ?: return PlatformVideoStatus(platformVideoId, "NOT_FOUND", "Tumblr 게시물을 찾지 못했습니다.")
        return PlatformVideoStatus(
            platformVideoId = "$blogName:${post.idString ?: post.id?.toString() ?: postId}",
            status = post.state?.uppercase() ?: "PUBLISHED",
            platformUrl = post.postUrl,
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
            val parts = platformVideoId.split(":", limit = 2)
            val blogName = parts.getOrNull(0) ?: ""
            val postId = parts.getOrNull(1) ?: platformVideoId
            val response = tumblrApi.getPostNotes(
                blogName = blogName,
                postId = postId,
                authorization = "Bearer $accessToken",
            )

            /*
             * **노트 목록이 없으면 세지 못한 것이지 "0 건" 이 아니다.**
             *
             * 목록이 실제로 비어 있으면(빈 배열) 그 0 은 관측이므로 그대로 센다. 목록
             * 자체가 오지 않았다면 좋아요·답글·리블로그를 잴 근거가 없다.
             *
             * 주의: 이 집계는 **응답에 담긴 노트만** 센다. 페이지네이션은 이번 범위 밖이며
             * 별도 위험으로 보고한다.
             */
            val notes = response.response?.notes
                .requireMetric("Tumblr", "response.notes")

            var likes = 0L
            var reblogs = 0L
            var replies = 0L

            notes.forEach { note ->
                when (note.type) {
                    "like" -> likes++
                    "reblog" -> reblogs++
                    "reply" -> replies++
                }
            }

            PlatformAnalytics(
                // total_notes 는 조회수가 아니다(노트 총합) — availability 가 VIEWS 를
                // 미수집으로 선언하므로 검증 대상이 아니다.
                views = response.response?.totalNotes ?: 0,
                likes = likes,
                comments = replies,
                shares = reblogs,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: PlatformApiException) {
            // 어떤 지표가 빠졌는지 담은 메시지를 그대로 올려 보낸다. 일반 메시지로 덮으면
            // 스케줄러 경고 로그에서 원인 지표를 알 수 없다.
            throw e
        } catch (e: Exception) {
            log.warn("Tumblr 분석 데이터 조회 실패: {}", e.message)
            throw PlatformApiException("Tumblr", "분석 데이터 조회 실패", e)
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
            subscriberCount = primaryBlog.followers,
            profileImageUrl = avatar,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String?): PlatformTokenResult {
        log.debug("Tumblr OAuth 인가 코드 교환")

        val response = tumblrOAuthApi.exchangeToken(
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", authorizationCode)
                add("redirect_uri", redirectUri)
                add("client_id", tumblrConfig.getConsumerKey())
                add("client_secret", tumblrConfig.getConsumerSecret())
            },
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
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
                add("client_id", tumblrConfig.getConsumerKey())
                add("client_secret", tumblrConfig.getConsumerSecret())
            },
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
            val parts = platformVideoId.split(":", limit = 2)
            val blogName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return false
            val postId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return false
            tumblrApi.deletePost(
                blogName = blogName,
                authorization = "Bearer $accessToken",
                body = mapOf("id" to postId),
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
        publishedAfter: java.time.LocalDateTime?,
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
