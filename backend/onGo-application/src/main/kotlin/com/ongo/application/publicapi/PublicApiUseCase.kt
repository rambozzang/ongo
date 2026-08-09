package com.ongo.application.publicapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.channel.dto.ChannelResponse
import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.application.video.UploadVideoUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.publicapi.PublicApiPost
import com.ongo.domain.publicapi.PublicApiPostRepository
import com.ongo.domain.publicapi.PublicApiPostStatus
import com.ongo.domain.publicapi.PublicApiPostType
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.common.enums.ScheduleStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.contentsource.VideoSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class PublicApiUseCase(
    private val channelRepository: ChannelRepository,
    private val postRepository: PublicApiPostRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val scheduleRepository: ScheduleRepository,
    private val uploadVideoUseCase: UploadVideoUseCase,
    private val publishVideoUseCase: PublishVideoUseCase,
    private val objectMapper: ObjectMapper,
) {

    fun integrations(userId: Long): List<PublicIntegrationResponse> =
        channelRepository.findByUserId(userId).map { channel ->
            PublicIntegrationResponse(
                id = requireNotNull(channel.id).toString(),
                name = channel.channelName,
                identifier = channel.platformChannelId,
                provider = channel.platform.name.lowercase(),
                picture = channel.profileImageUrl,
                status = channel.status.name,
            )
        }

    @Transactional
    fun create(userId: Long, request: CreatePublicPostRequest): PublicPostResponse {
        require(request.posts.isNotEmpty()) { "posts는 하나 이상이어야 합니다" }
        require(request.posts.size <= MAX_TARGETS) { "게시 대상은 최대 ${MAX_TARGETS}개까지 지정할 수 있습니다" }
        val type = parseType(request.type)
        val scheduledAt = request.date?.let(::parseDate)
        if (type == PublicApiPostType.SCHEDULE) {
            require(scheduledAt != null) { "schedule 게시에는 date가 필요합니다" }
        }

        val video = resolveVideo(userId, request)
        val normalized = request.copy(
            type = type.name.lowercase(),
            date = scheduledAt?.toString(),
            videoId = video.id,
        )
        val configs = if (type == PublicApiPostType.DRAFT) {
            emptyList()
        } else {
            buildConfigs(userId, normalized, requireNotNull(video.id), scheduledAt)
        }
        val payloadJson = objectMapper.writeValueAsString(normalized)
        var post = postRepository.save(
            PublicApiPost(
                userId = userId,
                videoId = requireNotNull(video.id),
                type = type,
                status = if (type == PublicApiPostType.DRAFT) PublicApiPostStatus.DRAFT else PublicApiPostStatus.PROCESSING,
                scheduledAt = scheduledAt,
                payloadJson = payloadJson,
            ),
        )

        if (type != PublicApiPostType.DRAFT) {
            try {
                publishVideoUseCase.publishVideo(
                    userId = userId,
                    videoId = requireNotNull(video.id),
                    configs = configs,
                )
                post = postRepository.update(
                    post.copy(status = if (type == PublicApiPostType.SCHEDULE) PublicApiPostStatus.SCHEDULED else PublicApiPostStatus.PROCESSING),
                )
            } catch (e: RuntimeException) {
                post = postRepository.update(post.copy(status = PublicApiPostStatus.FAILED, errorMessage = safeError(e)))
            }
        }
        return toResponse(post)
    }

    fun list(userId: Long, limit: Int): List<PublicPostResponse> =
        postRepository.findByUserId(userId, limit).map(::toResponse)

    fun get(userId: Long, id: Long): PublicPostResponse =
        toResponse(load(userId, id))

    @Transactional
    fun changeStatus(userId: Long, id: Long, request: ChangePublicPostStatusRequest): PublicPostResponse {
        val current = load(userId, id)
        val target = request.status.trim().lowercase()
        require(target == "draft" || target == "schedule") {
            "status는 draft 또는 schedule만 지원합니다"
        }
        if (target == "draft") {
            require(current.status == PublicApiPostStatus.DRAFT || current.status == PublicApiPostStatus.SCHEDULED) {
                "예약 중이거나 초안 상태의 게시만 draft로 바꿀 수 있습니다"
            }
            if (current.status == PublicApiPostStatus.SCHEDULED) {
                videoUploadRepository.cancelScheduledUploads(current.videoId, LocalDateTime.now())
                scheduleRepository.findByUserId(userId)
                    .filter { it.videoId == current.videoId && it.status == ScheduleStatus.SCHEDULED }
                    .forEach { scheduleRepository.update(it.copy(status = ScheduleStatus.CANCELLED)) }
            }
            return toResponse(postRepository.update(current.copy(status = PublicApiPostStatus.DRAFT)))
        }
        require(current.status == PublicApiPostStatus.DRAFT) { "draft 상태의 게시만 다시 예약할 수 있습니다" }
        val type = PublicApiPostType.SCHEDULE
        val date = request.date?.let(::parseDate) ?: current.scheduledAt
        require(date != null) { "schedule 게시에는 저장된 date가 필요합니다" }
        val payload = objectMapper.readValue(current.payloadJson, CreatePublicPostRequest::class.java)
            .copy(type = type.name.lowercase(), date = date?.toString(), videoId = current.videoId)
        val updated = postRepository.update(
            current.copy(type = type, status = PublicApiPostStatus.PROCESSING, scheduledAt = date,
                payloadJson = objectMapper.writeValueAsString(payload)),
        )
        return try {
            publishVideoUseCase.publishVideo(
                userId, current.videoId, buildConfigs(userId, payload, current.videoId, date),
            )
            toResponse(postRepository.update(updated.copy(
                status = if (type == PublicApiPostType.SCHEDULE) PublicApiPostStatus.SCHEDULED else PublicApiPostStatus.PROCESSING,
            )))
        } catch (e: RuntimeException) {
            toResponse(postRepository.update(updated.copy(status = PublicApiPostStatus.FAILED, errorMessage = safeError(e))))
        }
    }

    @Transactional
    fun deleteDraft(userId: Long, id: Long) {
        if (!postRepository.deleteDraft(id, userId)) {
            throw IllegalStateException("draft 상태의 공개 API 게시만 삭제할 수 있습니다")
        }
    }

    private fun resolveVideo(userId: Long, request: CreatePublicPostRequest): Video {
        val requestedIds = buildList {
            request.videoId?.let(::add)
            request.posts.mapNotNull { item -> item.videoId ?: item.value.firstOrNull()?.videoId }.forEach(::add)
        }.distinct()
        require(requestedIds.size <= 1) { "한 번의 Postiz 게시에는 영상 하나만 사용할 수 있습니다" }
        val existingId = requestedIds.singleOrNull()
        if (existingId != null) {
            val video = videoRepository.findById(existingId) ?: throw NotFoundException("영상", existingId)
            if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 권한이 없습니다")
            return video
        }

        val value = request.posts.first().value.firstOrNull()
        val mediaUrl = firstText(value?.video) ?: firstText(value?.image)
        if (mediaUrl == null) {
            require(parseType(request.type) == PublicApiPostType.DRAFT) {
                "게시하려면 videoId 또는 value.video/value.image URL이 필요합니다"
            }
            return uploadVideoUseCase.createVideo(
                userId = userId,
                title = value?.title ?: value?.content?.lineSequence()?.firstOrNull()?.take(100) ?: "공개 API 초안",
                description = value?.description ?: value?.content,
                tags = value?.tags ?: emptyList(),
            )
        }
        validateMediaUrl(mediaUrl)
        val draft = uploadVideoUseCase.createVideo(
            userId = userId,
            title = value?.title ?: value?.content?.lineSequence()?.firstOrNull()?.take(100) ?: "공개 API 영상",
            description = value?.description ?: value?.content,
            tags = value?.tags ?: emptyList(),
        )
        return videoRepository.update(
            draft.copy(fileUrl = mediaUrl, mediaType = MediaType.VIDEO, source = VideoSource.URL_IMPORT),
        )
    }

    private fun buildConfigs(
        userId: Long,
        request: CreatePublicPostRequest,
        videoId: Long,
        scheduledAt: LocalDateTime?,
    ): List<PlatformUploadConfig> = request.posts.map { item ->
        val channelId = item.integration.id.toLongOrNull()
            ?: throw IllegalArgumentException("integration.id는 onGo 채널 ID여야 합니다")
        val channel = channelRepository.findById(channelId)
            ?.takeIf { it.userId == userId }
            ?: throw NotFoundException("integration", item.integration.id)
        val value = item.value.firstOrNull()
        PlatformUploadConfig(
            platform = channel.platform,
            videoUploadId = 0,
            channelId = channelId,
            title = (value?.title ?: value?.content ?: "영상").take(PlatformUploadLimits.title(channel.platform)),
            description = value?.description ?: value?.content,
            tags = value?.tags.orEmpty(),
            visibility = Visibility.PUBLIC,
            thumbnailUrl = firstText(value?.image),
            scheduledAt = scheduledAt,
        )
    }

    private fun toResponse(post: PublicApiPost): PublicPostResponse {
        val uploads = videoUploadRepository.findByVideoId(post.videoId)
        val status = aggregateStatus(post, uploads)
        val payload = runCatching { objectMapper.readValue(post.payloadJson, CreatePublicPostRequest::class.java) }.getOrNull()
        val targets = payload?.posts.orEmpty().map { target ->
            val channelId = target.integration.id.toLongOrNull()
            val upload = uploads.firstOrNull { it.channelId == channelId }
            PublicPostTargetResponse(target.integration.id, upload?.status?.name ?: status.name, upload?.platformUrl, upload?.errorMessage)
        }
        return PublicPostResponse(
            id = post.id.toString(),
            type = post.type.name.lowercase(),
            status = status.name.lowercase(),
            state = when (status) {
                PublicApiPostStatus.DRAFT -> "DRAFT"
                PublicApiPostStatus.SCHEDULED -> "QUEUE"
                else -> status.name
            },
            date = post.scheduledAt?.toString(),
            videoId = post.videoId,
            error = post.errorMessage,
            posts = targets,
        )
    }

    private fun aggregateStatus(post: PublicApiPost, uploads: List<VideoUpload>): PublicApiPostStatus {
        if (post.status == PublicApiPostStatus.DRAFT) return post.status
        if (uploads.isEmpty()) return post.status
        return when {
            uploads.all { it.status == UploadStatus.PUBLISHED } -> PublicApiPostStatus.PUBLISHED
            uploads.any { it.status == UploadStatus.PUBLISHED } && uploads.any { it.status in TERMINAL_FAILURES } -> PublicApiPostStatus.PARTIALLY_PUBLISHED
            uploads.any { it.status == UploadStatus.UNCONFIRMED } -> PublicApiPostStatus.UNCONFIRMED
            uploads.all { it.status in TERMINAL_FAILURES } -> PublicApiPostStatus.FAILED
            post.type == PublicApiPostType.SCHEDULE && uploads.all { it.status == UploadStatus.UPLOADING } -> PublicApiPostStatus.SCHEDULED
            else -> PublicApiPostStatus.PROCESSING
        }
    }

    private fun load(userId: Long, id: Long): PublicApiPost =
        postRepository.findByIdAndUserId(id, userId) ?: throw NotFoundException("공개 API 게시", id)

    private fun parseType(value: String): PublicApiPostType = when (value.trim().lowercase()) {
        "now" -> PublicApiPostType.NOW
        "schedule", "scheduled" -> PublicApiPostType.SCHEDULE
        "draft" -> PublicApiPostType.DRAFT
        else -> throw IllegalArgumentException("type은 now, schedule 또는 draft여야 합니다")
    }

    private fun parseDate(value: String): LocalDateTime = runCatching {
        LocalDateTime.parse(value)
    }.recoverCatching {
        OffsetDateTime.parse(value).toLocalDateTime()
    }.recoverCatching {
        Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }.getOrElse { throw IllegalArgumentException("date는 ISO-8601 형식이어야 합니다") }

    private fun firstText(node: JsonNode?): String? = when {
        node == null || node.isNull -> null
        node.isArray -> node.firstOrNull { it.isTextual }?.asText()
        node.isTextual -> node.asText()
        else -> null
    }

    private fun validateMediaUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("media URL이 올바르지 않습니다") }
        require(uri.scheme == "https" || uri.scheme == "http") { "media URL은 http 또는 https여야 합니다" }
        require(!uri.host.isNullOrBlank()) { "media URL의 호스트가 필요합니다" }
    }

    private fun safeError(error: RuntimeException): String =
        (error.message ?: "공개 API 게시에 실패했습니다").take(2_000)

    companion object {
        private const val MAX_TARGETS = 50
        private val TERMINAL_FAILURES = setOf(UploadStatus.FAILED, UploadStatus.REJECTED, UploadStatus.CANCELLED)
    }
}

private object PlatformUploadLimits {
    fun title(platform: Platform): Int = when (platform) {
        Platform.TIKTOK, Platform.INSTAGRAM, Platform.THREADS -> 2200
        else -> 100
    }
}
