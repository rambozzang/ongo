package com.ongo.infrastructure.external.twitter

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.twitter.dto.TwitterCreateTweetRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class TwitterClient(
    private val twitterApi: TwitterApi,
    private val twitterMediaApi: TwitterMediaApi,
    private val twitterOAuthApi: TwitterOAuthApi,
    private val twitterConfig: TwitterConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(TwitterClient::class.java)

    override val platform: Platform = Platform.TWITTER

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Twitter 영상 업로드 시작: title={}", request.title)

        try {
            // Step 1: Init chunked media upload
            val initResponse = twitterMediaApi.initUpload(
                authorization = "Bearer ${request.accessToken}",
                command = "INIT",
                totalBytes = request.fileSize,
                mediaType = "video/mp4",
                mediaCategory = "tweet_video",
            )

            val mediaId = initResponse.mediaIdString

            // Step 2: Finalize (assumes file is uploaded via chunks externally)
            val finalizeResponse = twitterMediaApi.finalizeUpload(
                authorization = "Bearer ${request.accessToken}",
                command = "FINALIZE",
                mediaId = mediaId,
            )

            // Step 3: Wait for processing if needed
            if (finalizeResponse.processingInfo?.state == "pending" ||
                finalizeResponse.processingInfo?.state == "in_progress"
            ) {
                waitForMediaProcessing(mediaId, request.accessToken)
            }

            // Step 4: Create tweet with media
            val tweetText = request.title.take(280)
            val tweetRequest = TwitterCreateTweetRequest(
                text = tweetText,
                media = TwitterCreateTweetRequest.MediaPayload(
                    mediaIds = listOf(mediaId),
                ),
            )

            val tweetResponse = twitterApi.createTweet(
                authorization = "Bearer ${request.accessToken}",
                request = tweetRequest,
            )

            val tweetId = tweetResponse.data?.id
                ?: throw PlatformUploadException("Twitter", "트윗 생성 응답에 ID가 없습니다")

            log.info("Twitter 업로드 완료: tweetId={}", tweetId)

            return PlatformUploadResult(
                platformVideoId = tweetId,
                platformUrl = "https://twitter.com/i/status/$tweetId",
                status = "published",
            )
        } catch (e: PlatformUploadException) {
            throw e
        } catch (e: Exception) {
            log.error("Twitter 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Twitter", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Twitter 트윗 상태 조회: tweetId={}", platformVideoId)

        return try {
            val response = twitterApi.getTweet(
                tweetId = platformVideoId,
                tweetFields = "id,text",
                authorization = "Bearer $accessToken",
            )

            PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = if (response.data != null) "published" else "not_found",
            )
        } catch (e: Exception) {
            PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = "not_found",
                errorMessage = e.message,
            )
        }
    }

    override fun getVideoAnalytics(
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PlatformAnalytics {
        log.debug("Twitter 분석 데이터 조회: tweetId={}", platformVideoId)

        return try {
            val response = twitterApi.getTweet(
                tweetId = platformVideoId,
                tweetFields = "public_metrics",
                authorization = "Bearer $accessToken",
            )

            val metrics = response.data?.publicMetrics
            PlatformAnalytics(
                views = metrics?.impressionCount ?: 0,
                likes = metrics?.likeCount ?: 0,
                comments = metrics?.replyCount ?: 0,
                shares = metrics?.retweetCount ?: 0,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Twitter 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Twitter 사용자 정보 조회")

        val response = twitterApi.getMe(
            userFields = "id,name,username,profile_image_url,public_metrics",
            authorization = "Bearer $accessToken",
        )

        val user = response.data
            ?: throw PlatformUploadException("Twitter", "사용자 정보를 가져올 수 없습니다")

        return PlatformChannelInfo(
            channelId = user.id,
            channelName = user.name ?: user.username ?: "",
            channelUrl = user.username?.let { "https://twitter.com/$it" } ?: "",
            subscriberCount = user.publicMetrics?.followersCount ?: 0,
            profileImageUrl = user.profileImageUrl,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Twitter OAuth 인가 코드 교환")

        val response = twitterOAuthApi.exchangeToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to authorizationCode,
                "redirect_uri" to redirectUri,
                "client_id" to twitterConfig.getClientId(),
                "code_verifier" to "challenge",
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("Twitter OAuth 토큰 갱신")

        val response = twitterOAuthApi.exchangeToken(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to twitterConfig.getClientId(),
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("Twitter 트윗 삭제: tweetId={}", platformVideoId)

        return try {
            twitterApi.deleteTweet(
                tweetId = platformVideoId,
                authorization = "Bearer $accessToken",
            )
            true
        } catch (e: Exception) {
            log.error("Twitter 트윗 삭제 실패: {}", e.message)
            false
        }
    }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true, canReply = true, canLike = true, canDelete = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("Twitter 답글 조회: tweetId={}", platformVideoId)

        return try {
            val response = twitterApi.searchReplies(
                query = "conversation_id:$platformVideoId",
                tweetFields = "author_id,public_metrics,created_at,in_reply_to_user_id",
                expansions = "author_id",
                userFields = "name,username,profile_image_url",
                maxResults = maxResults.coerceIn(10, 100),
                nextToken = pageToken,
                authorization = "Bearer $accessToken",
            )

            val userMap = response.includes?.users?.associateBy { it.id } ?: emptyMap()

            val comments = response.data?.map { tweet ->
                val user = tweet.authorId?.let { userMap[it] }
                PlatformComment(
                    platformCommentId = tweet.id,
                    authorName = user?.name ?: user?.username ?: "Unknown",
                    authorAvatarUrl = user?.profileImageUrl,
                    authorChannelUrl = user?.username?.let { "https://twitter.com/$it" },
                    content = tweet.text ?: "",
                    likeCount = tweet.publicMetrics?.likeCount ?: 0,
                    replyCount = tweet.publicMetrics?.replyCount ?: 0,
                    publishedAt = tweet.createdAt?.let { parseIsoDateTime(it) },
                )
            } ?: emptyList()

            PlatformCommentListResult(
                comments = comments,
                nextPageToken = response.meta?.nextToken,
                totalCount = response.meta?.resultCount,
            )
        } catch (e: Exception) {
            log.warn("Twitter 답글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("Twitter 답글 작성: parentId={}", platformCommentId)

        return try {
            val request = TwitterCreateTweetRequest(
                text = content.take(280),
                reply = TwitterCreateTweetRequest.ReplyPayload(
                    inReplyToTweetId = platformCommentId,
                ),
            )
            val response = twitterApi.createTweet(
                authorization = "Bearer $accessToken",
                request = request,
            )
            PlatformCommentReplyResult(
                platformCommentId = response.data?.id ?: "",
                success = response.data?.id != null,
            )
        } catch (e: Exception) {
            log.error("Twitter 답글 작성 실패: {}", e.message)
            PlatformCommentReplyResult("", success = false, errorMessage = e.message)
        }
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("Twitter 댓글 삭제: tweetId={}", platformCommentId)

        return try {
            twitterApi.deleteTweet(
                tweetId = platformCommentId,
                authorization = "Bearer $accessToken",
            )
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("Twitter 댓글 삭제 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
        }
    }

    override fun likeComment(
        platformCommentId: String,
        accessToken: String,
    ): Boolean {
        log.info("Twitter 좋아요: tweetId={}", platformCommentId)

        return try {
            // Get user ID first
            val user = twitterApi.getMe(
                userFields = "id",
                authorization = "Bearer $accessToken",
            )
            val userId = user.data?.id ?: return false

            val response = twitterApi.likeTweet(
                userId = userId,
                authorization = "Bearer $accessToken",
                body = com.ongo.infrastructure.external.twitter.dto.TwitterLikeRequest(tweetId = platformCommentId),
            )
            response.data?.liked ?: false
        } catch (e: Exception) {
            log.error("Twitter 좋아요 실패: {}", e.message)
            false
        }
    }

    private fun parseIsoDateTime(iso: String): java.time.LocalDateTime? =
        try {
            java.time.OffsetDateTime.parse(iso).toLocalDateTime()
        } catch (_: Exception) {
            null
        }

    private fun waitForMediaProcessing(mediaId: String, accessToken: String) {
        var attempts = 0
        val maxAttempts = 30

        while (attempts < maxAttempts) {
            val status = twitterMediaApi.checkStatus(
                authorization = "Bearer $accessToken",
                command = "STATUS",
                mediaId = mediaId,
            )

            when (status.processingInfo?.state) {
                "succeeded" -> return
                "failed" -> throw PlatformUploadException(
                    "Twitter",
                    status.processingInfo.error?.message ?: "미디어 처리 실패",
                )
                else -> {
                    val waitSeconds = status.processingInfo?.checkAfterSecs ?: 5
                    Thread.sleep(waitSeconds * 1000L)
                    attempts++
                }
            }
        }

        throw PlatformUploadException("Twitter", "미디어 처리 시간 초과")
    }
}
