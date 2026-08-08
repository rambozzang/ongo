package com.ongo.infrastructure.external.linkedin

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.linkedin.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import org.springframework.web.client.RestClient
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import java.io.RandomAccessFile

@Component
class LinkedInClient(
    private val linkedInApi: LinkedInApi,
    private val linkedInVideosApi: LinkedInVideosApi,
    private val linkedInOAuthApi: LinkedInOAuthApi,
    private val linkedInConfig: LinkedInConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(LinkedInClient::class.java)

    override val platform: Platform = Platform.LINKEDIN

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("LinkedIn 영상 업로드 시작: title={}", request.title)

        val personId = request.platformChannelId
            ?: throw PlatformUploadException("LinkedIn", "사용자 ID가 필요합니다")

        try {
            // LinkedIn Assets API is deprecated for video uploads. Videos API requires
            // initialize → 4MB range PUTs → ETag finalize → AVAILABLE confirmation.
            val videoUrn = uploadLinkedInVideo(request.fileUrl, request.accessToken, personId)

            // Step 3: Create UGC post
            val ugcPost = LinkedInUgcPostRequest(
                author = "urn:li:person:$personId",
                specificContent = LinkedInUgcPostRequest.SpecificContent(
                    shareContent = LinkedInUgcPostRequest.ShareContent(
                        shareCommentary = LinkedInUgcPostRequest.ShareCommentary(
                            text = request.description.take(3000),
                        ),
                        media = listOf(
                            LinkedInUgcPostRequest.ShareMedia(
                                media = videoUrn,
                                title = LinkedInUgcPostRequest.MediaTitle(
                                    text = request.title.take(200),
                                ),
                                description = LinkedInUgcPostRequest.MediaDescription(
                                    text = request.description.take(256),
                                ),
                            ),
                        ),
                    ),
                ),
                visibility = LinkedInUgcPostRequest.Visibility(
                    memberNetworkVisibility = if (request.visibility.uppercase() == "PUBLIC") "PUBLIC" else "CONNECTIONS",
                ),
            )

            val postResponse = linkedInApi.createUgcPost(
                authorization = "Bearer ${request.accessToken}",
                request = ugcPost,
            )

            log.info("LinkedIn 업로드 완료: postId={}", postResponse.id)

            return PlatformUploadResult(
                platformVideoId = postResponse.id,
                platformUrl = "https://www.linkedin.com/feed/update/${postResponse.id}",
                status = "published",
            )
        } catch (e: PlatformUploadException) {
            throw e
        } catch (e: Exception) {
            log.error("LinkedIn 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("LinkedIn", e.message ?: "알 수 없는 오류", e)
        }
    }

    private fun uploadLinkedInVideo(fileUrl: String, accessToken: String, personId: String): String {
        val sourceFile = downloadFileToTemp(fileUrl)
        try {
            val initialize = linkedInVideosApi.initializeUpload(
                authorization = "Bearer $accessToken",
                linkedinVersion = linkedInConfig.getApiVersion(),
                restliVersion = RESTLI_VERSION,
                request = LinkedInVideoInitializeRequest(
                    initializeUploadRequest = LinkedInVideoInitializeRequest.InitializeUploadRequest(
                        owner = "urn:li:person:$personId",
                        fileSizeBytes = sourceFile.length(),
                    ),
                ),
            )
            val value = initialize.value
                ?: throw PlatformUploadException("LinkedIn", "동영상 업로드 세션 생성 실패")
            val videoUrn = value.video
                ?: throw PlatformUploadException("LinkedIn", "동영상 URN을 받지 못했습니다")
            if (value.uploadInstructions.isEmpty()) {
                throw PlatformUploadException("LinkedIn", "동영상 업로드 파트 지시가 없습니다")
            }

            val uploadedPartIds = value.uploadInstructions.map { instruction ->
                val length = instruction.lastByte - instruction.firstByte + 1
                require(length in 1..MAX_VIDEO_PART_BYTES) {
                    "LinkedIn 업로드 파트 크기가 허용 범위를 벗어났습니다"
                }
                val bytes = ByteArray(length.toInt())
                RandomAccessFile(sourceFile, "r").use { file ->
                    file.seek(instruction.firstByte)
                    file.readFully(bytes)
                }
                val response = RestClient.create()
                    .put()
                    .uri(instruction.uploadUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Linkedin-Version", linkedInConfig.getApiVersion())
                    .header("X-Restli-Protocol-Version", RESTLI_VERSION)
                    .header("Content-Length", length.toString())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity()
                if (!response.statusCode.is2xxSuccessful) {
                    throw PlatformUploadException("LinkedIn", "동영상 파트 업로드 실패: ${response.statusCode}")
                }
                response.headers.getFirst("ETag")?.trim('"')
                    ?: throw PlatformUploadException("LinkedIn", "동영상 파트 응답에 ETag가 없습니다")
            }

            linkedInVideosApi.finalizeUpload(
                authorization = "Bearer $accessToken",
                linkedinVersion = linkedInConfig.getApiVersion(),
                restliVersion = RESTLI_VERSION,
                request = LinkedInVideoFinalizeRequest(
                    finalizeUploadRequest = LinkedInVideoFinalizeRequest.FinalizeUploadRequest(
                        video = videoUrn,
                        uploadToken = value.uploadToken ?: "",
                        uploadedPartIds = uploadedPartIds,
                    ),
                ),
            )

            awaitVideoAvailable(videoUrn, accessToken)
            return videoUrn
        } finally {
            sourceFile.delete()
        }
    }

    private fun awaitVideoAvailable(videoUrn: String, accessToken: String) {
        val attempts = linkedInConfig.getVideoPollAttempts().coerceAtLeast(1)
        repeat(attempts) { attempt ->
            val response = linkedInVideosApi.getVideo(
                videoUrn = videoUrn,
                authorization = "Bearer $accessToken",
                linkedinVersion = linkedInConfig.getApiVersion(),
                restliVersion = RESTLI_VERSION,
            )
            when (response.status?.uppercase()) {
                "AVAILABLE" -> return
                "PROCESSING_FAILED", "FAILED" -> throw PlatformUploadException(
                    "LinkedIn",
                    response.processingFailureReason ?: "동영상 처리 실패",
                )
            }
            if (attempt < attempts - 1) {
                Thread.sleep(linkedInConfig.getVideoPollIntervalMillis().coerceAtLeast(0))
            }
        }
        throw PlatformUploadException("LinkedIn", "동영상 처리 시간이 초과되었습니다")
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("LinkedIn 게시물 상태 조회: postId={}", platformVideoId)

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
        log.debug("LinkedIn 분석 데이터 조회: postId={}", platformVideoId)

        return try {
            val response = linkedInApi.getShareStatistics(
                q = "organizationalEntity",
                shares = platformVideoId,
                authorization = "Bearer $accessToken",
            )

            val stats = response.elements?.firstOrNull()?.totalShareStatistics

            PlatformAnalytics(
                views = stats?.impressionCount ?: 0,
                likes = stats?.likeCount ?: 0,
                comments = stats?.commentCount ?: 0,
                shares = stats?.shareCount ?: 0,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("LinkedIn 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("LinkedIn 프로필 정보 조회")

        val response = linkedInApi.getProfile(
            authorization = "Bearer $accessToken",
        )

        val profileImage = response.profilePicture?.displayImage?.elements
            ?.lastOrNull()?.identifiers?.firstOrNull()?.identifier

        val fullName = listOfNotNull(response.localizedFirstName, response.localizedLastName)
            .joinToString(" ")

        return PlatformChannelInfo(
            channelId = response.id,
            channelName = fullName,
            channelUrl = response.vanityName?.let { "https://www.linkedin.com/in/$it" } ?: "",
            subscriberCount = 0,
            profileImageUrl = profileImage,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String?): PlatformTokenResult {
        log.debug("LinkedIn OAuth 인가 코드 교환")

        val response = linkedInOAuthApi.exchangeToken(
            contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            body = LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", authorizationCode)
                add("redirect_uri", redirectUri)
                add("client_id", linkedInConfig.getClientId())
                add("client_secret", linkedInConfig.getClientSecret())
            },
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("LinkedIn OAuth 토큰 갱신")

        val response = linkedInOAuthApi.exchangeToken(
            contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            body = LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
                add("client_id", linkedInConfig.getClientId())
                add("client_secret", linkedInConfig.getClientSecret())
            },
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("LinkedIn 게시물 삭제: postId={}", platformVideoId)

        return try {
            linkedInApi.deletePost(
                postId = platformVideoId,
                authorization = "Bearer $accessToken",
            )
            true
        } catch (e: Exception) {
            log.error("LinkedIn 게시물 삭제 실패: {}", e.message)
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
        publishedAfter: java.time.LocalDateTime?,
    ): PlatformCommentListResult {
        log.debug("LinkedIn 댓글 조회: activityId={}", platformVideoId)

        return try {
            val start = pageToken?.toIntOrNull() ?: 0
            val response = linkedInApi.getComments(
                activityId = platformVideoId,
                start = start,
                count = maxResults.coerceAtMost(100),
                authorization = "Bearer $accessToken",
            )

            val comments = response.elements?.mapNotNull { element ->
                PlatformComment(
                    platformCommentId = element.urn ?: return@mapNotNull null,
                    authorName = element.actor ?: "Unknown",
                    content = element.message?.text ?: "",
                    likeCount = element.likesSummary?.totalLikes ?: 0,
                    replyCount = element.commentsSummary?.totalFirstLevelComments ?: 0,
                    publishedAt = element.created?.time?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    },
                )
            } ?: emptyList()

            val nextStart = start + comments.size
            val hasMore = (response.paging?.total ?: 0) > nextStart

            PlatformCommentListResult(
                comments = comments,
                nextPageToken = if (hasMore) nextStart.toString() else null,
                totalCount = response.paging?.total,
            )
        } catch (e: Exception) {
            log.warn("LinkedIn 댓글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("LinkedIn 댓글 답글: activityId={}", platformVideoId)

        return try {
            val activityId = platformVideoId ?: platformCommentId
            val user = linkedInApi.getProfile(
                projection = "(id)",
                authorization = "Bearer $accessToken",
            )
            val actorUrn = "urn:li:person:${user.id}"

            val response = linkedInApi.createComment(
                activityId = activityId,
                authorization = "Bearer $accessToken",
                body = LinkedInCommentRequest(
                    message = LinkedInCommentRequest.LinkedInMessageBody(text = content),
                    actor = actorUrn,
                ),
            )
            PlatformCommentReplyResult(
                platformCommentId = response.urn ?: "",
                success = response.urn != null,
            )
        } catch (e: Exception) {
            log.error("LinkedIn 댓글 답글 실패: {}", e.message)
            PlatformCommentReplyResult("", success = false, errorMessage = e.message)
        }
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("LinkedIn 댓글 삭제: commentId={}", platformCommentId)

        return try {
            // Extract activityId from comment URN if possible, otherwise use platformCommentId
            linkedInApi.deleteComment(
                activityId = platformCommentId,
                commentId = platformCommentId,
                authorization = "Bearer $accessToken",
            )
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("LinkedIn 댓글 삭제 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
        }
    }

    override fun likeComment(
        platformCommentId: String,
        accessToken: String,
    ): Boolean {
        log.info("LinkedIn 좋아요: commentId={}", platformCommentId)

        return try {
            val user = linkedInApi.getProfile(
                projection = "(id)",
                authorization = "Bearer $accessToken",
            )
            val actorUrn = "urn:li:person:${user.id}"

            linkedInApi.likeActivity(
                activityId = platformCommentId,
                authorization = "Bearer $accessToken",
                body = LinkedInLikeRequest(actor = actorUrn, targetObject = platformCommentId),
            )
            true
        } catch (e: Exception) {
            log.error("LinkedIn 좋아요 실패: {}", e.message)
            false
        }
    }

    private companion object {
        const val RESTLI_VERSION = "2.0.0"
        const val MAX_VIDEO_PART_BYTES = 4L * 1024 * 1024
    }
}
