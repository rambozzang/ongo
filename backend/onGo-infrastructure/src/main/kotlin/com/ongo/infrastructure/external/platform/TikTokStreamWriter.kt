package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.channel.PlainToken
import com.ongo.infrastructure.external.tiktok.TikTokApi
import com.ongo.infrastructure.external.tiktok.dto.TikTokInboxVideoUploadRequest
import com.ongo.infrastructure.external.tiktok.dto.TikTokInitUploadRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class TikTokStreamWriterFactory(
    private val tikTokApi: TikTokApi,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformStreamWriterFactory {
    override val platform = Platform.TIKTOK
    override fun createWriter(): PlatformStreamWriter = TikTokStreamWriter(tikTokApi, fileTransferHelper)
}

class TikTokStreamWriter(
    private val tikTokApi: TikTokApi,
    private val fileTransferHelper: PlatformFileTransferHelper,
    private val statusPollIntervalMs: Long = 2_000L,
    private val statusPollMaxAttempts: Int = 15,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = TempFileChunkBuffer("tiktok")
    private var publishId: String? = null
    private var uploadUrl: String? = null
    private var accessToken: String? = null
    private var platformChannelId: String? = null

    companion object {
        private const val CHUNK_SIZE = 10_000_000L // 10MB
        private const val MAX_FILE_SIZE = 4L * 1024 * 1024 * 1024 // 4GB — TikTok 제한이지만 메모리 보호를 위해 실질 제한 적용
        private const val MAX_MEMORY_FILE_SIZE = 2L * 1024 * 1024 * 1024 // 2GB — 메모리 버퍼링 상한
    }

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: PlainToken,
        platformChannelId: String?,
        fileSize: Long,
        scheduledAt: LocalDateTime?,
    ): String {
        this.accessToken = accessToken.value
        this.platformChannelId = platformChannelId
        if (fileSize > MAX_MEMORY_FILE_SIZE) {
            throw IllegalArgumentException(
                "스트리밍 업로드 최대 파일 크기(${MAX_MEMORY_FILE_SIZE / 1024 / 1024}MB)를 초과합니다: ${fileSize / 1024 / 1024}MB"
            )
        }

        val privacyLevel = mapVisibility(meta.visibility.name)
        val settings = meta.customSettingsJson
            ?.let { runCatching { objectMapper.readTree(it) }.getOrNull() }
        val contentPostingMethod = settings?.path("content_posting_method")?.asText(null)
            ?.uppercase() ?: "DIRECT_POST"
        require(contentPostingMethod == "DIRECT_POST" || contentPostingMethod == "UPLOAD") {
            "지원하지 않는 TikTok content_posting_method입니다: $contentPostingMethod"
        }
        if (contentPostingMethod == "DIRECT_POST") {
            val creatorInfo = tikTokApi.queryCreatorPublishInfo("Bearer ${accessToken.value}")
            if (creatorInfo.error != null) {
                throw IllegalStateException("TikTok 게시 권한 조회 실패: ${creatorInfo.error.message}")
            }
            val allowedPrivacyLevels = creatorInfo.data?.privacyLevelOptions.orEmpty()
            require(privacyLevel in allowedPrivacyLevels) {
                "TikTok 계정에서 허용하지 않는 공개 범위입니다: $privacyLevel (허용: ${allowedPrivacyLevels.joinToString()})"
            }
        }
        val totalChunkCount = maxOf(1, ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt())

        require(scheduledAt == null) { "TikTok Content Posting API는 예약 게시를 지원하지 않습니다." }

        val sourceInfo = TikTokInitUploadRequest.SourceInfo(
            source = "FILE_UPLOAD",
            videoSize = fileSize,
            chunkSize = CHUNK_SIZE,
            totalChunkCount = totalChunkCount,
        )

        val response = if (contentPostingMethod == "UPLOAD") {
            tikTokApi.initInboxVideoUpload(
                authorization = "Bearer ${accessToken.value}",
                request = TikTokInboxVideoUploadRequest(sourceInfo),
            )
        } else {
            tikTokApi.initVideoUpload(
                authorization = "Bearer ${accessToken.value}",
                request = TikTokInitUploadRequest(
                    postInfo = TikTokInitUploadRequest.PostInfo(
                        title = buildPostText(meta),
                        privacyLevel = privacyLevel,
                        disableDuet = settings?.booleanSetting("duet")?.not() ?: false,
                        disableComment = settings?.booleanSetting("comment")?.not() ?: false,
                        disableStitch = settings?.booleanSetting("stitch")?.not() ?: false,
                        brandContentToggle = settings?.booleanSetting("brand_content_toggle") ?: false,
                        brandOrganicToggle = settings?.booleanSetting("brand_organic_toggle") ?: false,
                        isAigc = settings?.booleanSetting("video_made_with_ai") ?: false,
                    ),
                    sourceInfo = sourceInfo,
                ),
            )
        }

        if (response.error != null) {
            throw IllegalStateException("TikTok 업로드 초기화 실패: ${response.error.message}")
        }

        publishId = response.data?.publishId
            ?: throw IllegalStateException("TikTok publish_id를 받지 못했습니다")
        uploadUrl = response.data.uploadUrl
            ?: throw IllegalStateException("TikTok upload_url을 받지 못했습니다")

        log.debug("TikTok 스트리밍 세션 초기화: publishId={}", publishId)
        return publishId!!
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        buffer.write(chunk, offset)
    }

    override fun complete(): PlatformUploadResult {
        val file = buffer.finish()
        val url = uploadUrl ?: throw IllegalStateException("initSession() 호출 필요")
        val pid = publishId ?: throw IllegalStateException("initSession() 호출 필요")
        return try {
            fileTransferHelper.uploadChunkedToTikTok(url, file, CHUNK_SIZE)
            log.info("TikTok 스트리밍 업로드 완료: publishId={}", pid)
            awaitPublishStatus(pid)
        } catch (e: Exception) {
            log.error("TikTok 스트리밍 업로드 실패", e)
            uploadFailureResult(e)
        } finally {
            buffer.cleanup()
        }
    }

    override fun abort() = buffer.cleanup()

    /**
     * TikTok의 파일 전송 완료는 게시 완료가 아니다. publish_id 상태를 확인해
     * 실제 게시 완료와 아직 처리 중인 결과를 구분한다.
     */
    private fun awaitPublishStatus(pid: String): PlatformUploadResult {
        val token = accessToken ?: return processingResult(pid, "TikTok access token이 없습니다")
        var lastStatus: String? = null
        var lastFailureReason: String? = null

        repeat(statusPollMaxAttempts.coerceAtLeast(1)) { attempt ->
            val response = try {
                tikTokApi.fetchPublishStatus(
                    authorization = "Bearer $token",
                    request = com.ongo.infrastructure.external.tiktok.dto.TikTokPublishStatusRequest(pid),
                )
            } catch (e: Exception) {
                log.warn("TikTok 게시 상태 조회 실패: publishId={}, {}", pid, e.message)
                return processingResult(pid, "게시 상태를 확인하지 못했습니다: ${e.message}")
            }

            if (response.error != null) {
                return processingResult(pid, "게시 상태를 확인하지 못했습니다: ${response.error.message}")
            }

            lastStatus = response.data?.status
            lastFailureReason = response.data?.failReason
            when (lastStatus) {
                "SEND_TO_USER_INBOX" -> return inboxUploadResult(pid)
                "PUBLISH_COMPLETE" -> {
                    val publicVideoId = response.data?.publicPostId?.firstOrNull()
                    if (publicVideoId.isNullOrBlank()) {
                        return processingResult(pid, "TikTok 게시가 완료되었지만 공개 영상 ID를 아직 확인하지 못했습니다")
                    }
                    return PlatformUploadResult(
                        success = true,
                        platformVideoId = publicVideoId,
                        platformUrl = buildPlatformUrl(publicVideoId),
                        published = true,
                    )
                }
                "FAILED" -> return PlatformUploadResult(
                    success = false,
                    published = false,
                    platformVideoId = pid,
                    platformUrl = buildPlatformUrl(pid),
                    errorMessage = lastFailureReason ?: "TikTok 게시에 실패했습니다",
                )
            }

            if (attempt < statusPollMaxAttempts.coerceAtLeast(1) - 1) {
                Thread.sleep(statusPollIntervalMs.coerceAtLeast(0))
            }
        }

        return processingResult(
            pid,
            "TikTok 게시 처리 중입니다 (status=${lastStatus ?: "unknown"})",
        )
    }

    private fun processingResult(pid: String, message: String) = PlatformUploadResult(
        success = true,
        platformVideoId = pid,
        // pid is TikTok's publish_id until the publicPostId is returned. It is
        // not safe to expose a share URL for it or persist it as a known URL.
        platformUrl = null,
        errorMessage = message,
        published = false,
    )

    private fun inboxUploadResult(pid: String) = PlatformUploadResult(
        success = true,
        platformVideoId = pid,
        platformUrl = null,
        errorMessage = "TikTok 받은편지함으로 전송되었습니다. TikTok 앱에서 편집 후 최종 게시해야 합니다.",
        published = false,
        confirmation = com.ongo.application.video.PublishConfirmation.UNKNOWN,
    )

    private fun buildPlatformUrl(videoId: String): String? = platformChannelId
        ?.takeIf { it.isNotBlank() }
        ?.let { "https://www.tiktok.com/@$it/video/$videoId" }

    private fun mapVisibility(visibility: String) = when (visibility.uppercase()) {
        "PUBLIC" -> "PUBLIC_TO_EVERYONE"
        "PRIVATE" -> "SELF_ONLY"
        "UNLISTED" -> "MUTUAL_FOLLOW_FRIENDS"
        else -> "SELF_ONLY"
    }

    private fun com.fasterxml.jackson.databind.JsonNode.booleanSetting(name: String): Boolean? {
        val node = path(name)
        return node.takeUnless { it.isMissingNode || it.isNull }?.asBoolean()
    }

    private fun buildPostText(meta: VideoPlatformMeta): String {
        val body = listOf(meta.title.orEmpty().trim(), meta.description.orEmpty().trim())
            .filter(String::isNotBlank)
            .joinToString("\n\n")
        val hashtags = meta.tags
            .map { it.removePrefix("#").trim() }
            .filter(String::isNotBlank)
            .joinToString(" ") { "#$it" }
        return listOf(body, hashtags)
            .filter(String::isNotBlank)
            .joinToString("\n\n")
            .take(2200)
    }
}
