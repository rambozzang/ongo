package com.ongo.application.publicapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import com.ongo.application.channel.dto.ChannelResponse
import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.PlatformUploadCapabilities
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.application.video.UploadVideoUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.TokenEncryptionPort
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
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

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
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val integrationToolPort: PlatformIntegrationToolPort,
    private val workspaceRepository: WorkspaceRepository,
) {

    private companion object {
        private val DOMAIN_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private const val MAX_TARGETS = 50
        private val TERMINAL_FAILURES = setOf(UploadStatus.FAILED, UploadStatus.REJECTED, UploadStatus.CANCELLED)
    }

    fun integrations(userId: Long, group: String? = null): List<PublicIntegrationResponse> =
        channelsForScope(userId, group).map { channel ->
            val workspace = channel.workspaceId?.let(workspaceRepository::findById)
            PublicIntegrationResponse(
                id = requireNotNull(channel.id).toString(),
                name = channel.channelName,
                identifier = postizIdentifier(channel.platform),
                provider = channel.platform.name.lowercase(),
                picture = channel.profileImageUrl,
                disabled = channel.status != com.ongo.domain.channel.ChannelStatus.ACTIVE,
                profile = channel.platformChannelId,
                status = channel.status.name,
                customer = workspace?.let { PublicCustomerResponse(it.id.toString(), it.name) },
            )
        }

    fun integrationSettings(userId: Long, integrationId: String): PublicIntegrationSettingsResponse {
        val channelId = integrationId.toLongOrNull()
            ?: throw IllegalArgumentException("integration id는 onGo 채널 ID여야 합니다")
        val channel = channelRepository.findById(channelId)
            ?.takeIf { it.userId == userId }
            ?: throw NotFoundException("integration", integrationId)
        val capability = PlatformUploadCapabilities.get(channel.platform)
            ?: throw IllegalArgumentException("${channel.platform} 게시 capability가 등록되지 않았습니다")
        val tools = integrationToolPort.definitions(channel.platform).map { definition ->
            PublicIntegrationToolResponse(
                methodName = definition.methodName,
                description = definition.description,
                dataSchema = objectMapper.createArrayNode().apply {
                    definition.dataSchema.forEach { field ->
                        add(objectMapper.valueToTree<JsonNode>(field))
                    }
                },
            )
        }
        return PublicIntegrationSettingsResponse(
            id = integrationId,
            provider = channel.platform.name.lowercase(),
            title = PublicFieldLimit(maxLength = capability.maxTitleLength),
            description = PublicFieldLimit(maxLength = capability.maxDescriptionLength),
            tags = PublicFieldLimit(maxCount = capability.maxTagCount),
            scheduling = capability.scheduling,
            directVideoUpload = capability.directVideoUpload,
            cloudVideoUpload = capability.cloudVideoUpload,
            maxFileSizeBytes = capability.maxFileSizeBytes,
            acceptedExtensions = capability.acceptedExtensions,
            unavailableReason = capability.unavailableReason,
            output = PublicIntegrationSettingsOutput(
                rules = buildSettingsRules(capability.scheduling, capability.directVideoUpload, capability.cloudVideoUpload),
                maxLength = capability.maxTitleLength,
                settings = objectMapper.createObjectNode().put("type", "object"),
                tools = tools,
            ),
        )
    }

    /**
     * Execute one of the operations advertised by integration-settings.
     * Discovery and invocation share the same allow-list so arbitrary provider
     * methods can never be reached through the public API.
     */
    fun triggerIntegrationTool(
        userId: Long,
        integrationId: String,
        request: PublicIntegrationToolRequest,
    ): PublicIntegrationToolResult {
        val channelId = integrationId.toLongOrNull()
            ?: throw IllegalArgumentException("integration id는 onGo 채널 ID여야 합니다")
        val channel = channelRepository.findById(channelId)
            ?.takeIf { it.userId == userId }
            ?: throw NotFoundException("integration", integrationId)
        val methodName = request.methodName.trim()
        require(methodName.isNotBlank() && methodName.length <= 100) {
            "methodName은 1~100자여야 합니다"
        }
        if (integrationToolPort.definitions(channel.platform).none { it.methodName == methodName }) {
            throw BusinessException(
                "INTEGRATION_TOOL_UNAVAILABLE",
                "${channel.platform} integration에서 지원하지 않는 tool입니다: $methodName",
            )
        }
        val dataNode = request.data ?: objectMapper.createObjectNode()
        require(dataNode.isObject) { "data는 JSON object여야 합니다" }
        val data: Map<String, Any?> = objectMapper.convertValue(
            dataNode,
            object : TypeReference<Map<String, Any?>>() {},
        )
        val output = integrationToolPort.invoke(
            platform = channel.platform,
            accessToken = tokenEncryptionPort.decrypt(channel.accessToken),
            platformChannelId = channel.platformChannelId,
            methodName = methodName,
            data = data,
        )
        return PublicIntegrationToolResult(objectMapper.valueToTree(output))
    }

    private fun buildSettingsRules(
        scheduling: Boolean,
        directVideoUpload: Boolean,
        cloudVideoUpload: Boolean,
    ): String = buildList {
        if (!directVideoUpload && !cloudVideoUpload) add("video upload is not supported")
        if (!scheduling) add("native scheduling is not supported; onGo durable scheduling is used")
        if (directVideoUpload || cloudVideoUpload) add("video attachment is supported")
    }.joinToString(". ").ifBlank { "No additional provider rules" }

    private fun postizIdentifier(platform: Platform): String = when (platform) {
        Platform.TWITTER -> "x"
        else -> platform.name.lowercase().replace("naver_clip", "naver-clip")
    }

    /**
     * Postiz의 find-slot 어댑터. onGo가 저장한 예약 큐를 기준으로 해당 계정의
     * 다음 비어 있는 15분 슬롯을 계산한다. 예약 데이터가 없는 경우에도 임의의
     * 과거 시간이 아니라 현재 이후의 확정 가능한 시각만 반환한다.
     */
    fun findAvailableSlot(userId: Long, integrationId: String): PublicAvailableSlotResponse {
        val channelId = integrationId.toLongOrNull()
            ?: throw IllegalArgumentException("integration id는 onGo 채널 ID여야 합니다")
        val channel = channelRepository.findById(channelId)
            ?.takeIf { it.userId == userId }
            ?: throw NotFoundException("integration", integrationId)

        val platformKey = channel.platform.name
        val busyTimes = scheduleRepository.findByUserId(userId)
            .asSequence()
            .filter { it.status == ScheduleStatus.SCHEDULED || it.status == ScheduleStatus.PROCESSING }
            .filter { schedule ->
                schedule.platforms.isEmpty() || schedule.platforms.keys.any { key ->
                    key == platformKey || key == "$platformKey#$channelId"
                }
            }
            .flatMap { schedule ->
                if (schedule.platforms.isEmpty()) {
                    sequenceOf(schedule.scheduledAt)
                } else {
                    schedule.platforms.asSequence()
                        .filter { (key, _) -> key == platformKey || key == "$platformKey#$channelId" }
                        .map { (_, raw) -> platformScheduleTime(raw) ?: schedule.scheduledAt }
                }
            }
            .toSet()

        val now = LocalDateTime.now(DOMAIN_ZONE).plusMinutes(5).withSecond(0).withNano(0)
        var candidate = roundUpToQuarter(now)
        while (candidate in busyTimes) candidate = candidate.plusMinutes(15)
        return PublicAvailableSlotResponse(
            candidate.atZone(DOMAIN_ZONE).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString(),
        )
    }

    @Transactional
    fun create(userId: Long, request: CreatePublicPostRequest): PublicPostResponse {
        val type = parseType(request.type)
        require(type == PublicApiPostType.DRAFT || request.posts.isNotEmpty()) {
            "now 또는 schedule 게시에는 posts가 하나 이상이어야 합니다"
        }
        require(request.posts.size <= MAX_TARGETS) { "게시 대상은 최대 ${MAX_TARGETS}개까지 지정할 수 있습니다" }
        val scheduledAt = request.date?.let(::parseDate)
        if (type == PublicApiPostType.SCHEDULE) {
            require(scheduledAt != null) { "schedule 게시에는 date가 필요합니다" }
        }
        val workspace = resolveRequestedWorkspace(userId, request.posts.mapNotNull { it.group }.distinct())

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
                workspaceId = workspace?.id,
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

    fun list(
        userId: Long,
        limit: Int,
        startDate: String? = null,
        endDate: String? = null,
        customer: String? = null,
    ): List<PublicPostResponse> {
        val workspace = resolveWorkspace(userId, customer)
        val start = startDate?.let(::parseDate)
        val end = endDate?.let(::parseDate)
        require((start == null) == (end == null)) { "startDate와 endDate는 함께 지정해야 합니다" }
        require(start == null || !end!!.isBefore(start)) { "endDate는 startDate보다 빠를 수 없습니다" }
        val posts = if (start != null && end != null) {
            if (workspace == null) postRepository.findByUserIdAndDateRange(userId, start, end, limit)
            else postRepository.findByUserIdAndWorkspaceIdAndDateRange(userId, workspace.id!!, start, end, limit)
        } else {
            if (workspace == null) postRepository.findByUserId(userId, limit)
            else postRepository.findByUserIdAndWorkspaceId(userId, workspace.id!!, limit)
        }
        return posts.map(::toResponse)
    }

    fun get(userId: Long, id: Long): PublicPostResponse =
        toResponse(load(userId, id))

    fun missingContent(userId: Long, id: Long): List<PublicMissingContentResponse> {
        val post = load(userId, id)
        val payload = runCatching {
            objectMapper.readValue(post.payloadJson, CreatePublicPostRequest::class.java)
        }.getOrNull() ?: return emptyList()

        // Postiz's missing endpoint is a provider lookup for posts whose release ID
        // was not confirmed. Providers without a list operation intentionally return
        // an empty list, matching Postiz's optional `missing` contract.
        return payload.posts.asSequence()
            .mapNotNull { target ->
                val channelId = target.integration.id.toLongOrNull() ?: return@mapNotNull null
                val channel = runCatching { channelRepository.findById(channelId) }
                    .getOrNull()
                    ?.takeIf { it.userId == userId }
                    ?: return@mapNotNull null
                val definition = integrationToolPort.definitions(channel.platform)
                    .firstOrNull { it.methodName == "listVideos" }
                    ?: return@mapNotNull null
                val output = runCatching {
                    integrationToolPort.invoke(
                        platform = channel.platform,
                        accessToken = tokenEncryptionPort.decrypt(channel.accessToken),
                        platformChannelId = channel.platformChannelId,
                        methodName = definition.methodName,
                        data = mapOf("maxResults" to 100),
                    )
                }.getOrNull() ?: return@mapNotNull null
                objectMapper.valueToTree<JsonNode>(output).path("items")
                    .mapNotNull { item ->
                        val platformId = item.path("platformVideoId").asText(null) ?: return@mapNotNull null
                        PublicMissingContentResponse(
                            id = platformId,
                            url = item.path("platformUrl").asText(null)
                                ?: item.path("thumbnailUrl").asText(null),
                        )
                    }
            }
            .flatten()
            .distinctBy { it.id }
            .take(100)
            .toList()
    }

    /**
     * 외부 플랫폼에서 이미 생성된 게시물 ID를 durable upload에 연결한다.
     * 분석 API가 release id만 반환하는 경우에도 새 업로드를 시작하지 않고,
     * 해당 게시물의 상태 조회 큐만 깨워 중복 게시를 방지한다.
     */
    @Transactional
    fun connectReleaseId(userId: Long, id: Long, request: PublicReleaseIdRequest): PublicPostResponse {
        val post = load(userId, id)
        val releaseId = request.releaseId.trim()
        require(releaseId.isNotBlank() && releaseId.length <= 255) { "releaseId가 유효하지 않습니다" }
        val requestedChannelId = request.integrationId?.toLongOrNull()
            ?: request.integrationId?.let { throw IllegalArgumentException("integrationId는 onGo 채널 ID여야 합니다") }
        val candidates = videoUploadRepository.findByVideoId(post.videoId)
            .filter { it.platformVideoId.isNullOrBlank() }
            .filter { requestedChannelId == null || it.channelId == requestedChannelId }
        require(candidates.size == 1) {
            if (candidates.isEmpty()) "releaseId를 연결할 미확정 게시 대상이 없습니다"
            else "여러 게시 대상이 있어 integrationId가 필요합니다"
        }
        val upload = candidates.single()
        if (requestedChannelId != null) {
            val channel = channelRepository.findById(requestedChannelId)
            require(channel?.userId == userId && channel?.id == upload.channelId) {
                "해당 integration에 대한 권한이 없습니다"
            }
        }
        videoUploadRepository.update(
            upload.copy(
                platformVideoId = releaseId,
                pollToken = releaseId,
                status = UploadStatus.PROCESSING,
                nextRetryAt = LocalDateTime.now(),
                errorMessage = null,
                lastError = null,
            ),
        )
        return toResponse(post)
    }

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
                cancelScheduledWorkForPost(userId, current)
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

    /**
     * Postiz의 delete post 계약을 안전한 durable 상태 전이로 매핑한다.
     * 외부 게시가 이미 접수된 뒤 DB 행만 지우면 재시작 시 같은 게시를 다시
     * 만들거나 외부 게시물을 추적하지 못하므로, 전송 전 초안만 실제 삭제한다.
     */
    @Transactional
    fun delete(userId: Long, id: Long) {
        val current = load(userId, id)
        val uploads = videoUploadRepository.findByVideoId(current.videoId)
        if (uploads.any { it.status == UploadStatus.PUBLISHED || it.status == UploadStatus.UNCONFIRMED || it.status == UploadStatus.PROCESSING }) {
            throw BusinessException(
                "PUBLIC_POST_DELETE_UNSAFE",
                "외부 게시가 이미 접수되었거나 완료된 게시물은 삭제할 수 없습니다. 플랫폼에서 직접 삭제한 뒤 onGo 기록을 보존해 주세요.",
            )
        }

        if (current.status == PublicApiPostStatus.DRAFT) {
            if (!postRepository.deleteDraft(id, userId)) {
                throw BusinessException("PUBLIC_POST_DELETE_CONFLICT", "게시물 상태가 변경되어 삭제할 수 없습니다")
            }
            return
        }

        if (current.status == PublicApiPostStatus.SCHEDULED) {
            cancelScheduledWorkForPost(userId, current)
        }
        postRepository.update(current.copy(status = PublicApiPostStatus.CANCELLED, errorMessage = null))
    }

    /**
     * A video can intentionally be reused by several Postiz posts. Cancelling by
     * videoId alone would therefore cancel unrelated accounts or occurrences.
     * The public payload is the durable association between a post and its
     * integrations; use it to select only matching scheduled rows. The update
     * itself remains atomic in the repository and only changes UPLOADING rows,
     * so a worker that won the lease cannot be cancelled after external work
     * has started.
     */
    private fun cancelScheduledWorkForPost(userId: Long, post: PublicApiPost) {
        val targets = publicTargets(post)
        val targetChannelIds = targets.mapNotNull { it.channelId }.toSet()
        val uploads = videoUploadRepository.findByVideoId(post.videoId)
        val matchingUploadIds = uploads.asSequence()
            .filter { it.status == UploadStatus.UPLOADING && it.scheduledAt != null }
            .filter { upload ->
                val sameOccurrence = post.scheduledAt == null || upload.scheduledAt == post.scheduledAt
                val targetMatches = if (targetChannelIds.isNotEmpty()) {
                    upload.channelId != null && upload.channelId in targetChannelIds
                } else {
                    // Legacy rows may not have integration IDs in their payload.
                    // Exact occurrence matching is the narrowest safe fallback.
                    sameOccurrence
                }
                sameOccurrence && targetMatches
            }
            .mapNotNull { it.id }
            .toSet()

        videoUploadRepository.cancelScheduledUploadsByIds(matchingUploadIds, LocalDateTime.now())

        scheduleRepository.findByUserId(userId)
            .asSequence()
            .filter { it.videoId == post.videoId && it.status == ScheduleStatus.SCHEDULED }
            .filter { post.scheduledAt == null || it.scheduledAt == post.scheduledAt }
            .filter { schedule ->
                if (targets.isEmpty()) return@filter true
                schedule.platforms.keys.any { key ->
                    targets.any { target -> key.endsWith("#${target.channelId}") }
                }
            }
            .forEach { scheduleRepository.update(it.copy(status = ScheduleStatus.CANCELLED)) }
    }

    private fun publicTargets(post: PublicApiPost): List<PublicTarget> {
        val payload = runCatching {
            objectMapper.readValue(post.payloadJson, CreatePublicPostRequest::class.java)
        }.getOrNull()
        return payload?.posts.orEmpty().mapNotNull { item ->
            val channelId = item.integration.id.toLongOrNull() ?: return@mapNotNull null
            PublicTarget(channelId)
        }
    }

    private data class PublicTarget(val channelId: Long)

    /** Postiz의 group 삭제. onGo의 한 번의 다중 채널 요청은 하나의 post 행으로 저장된다. */
    @Transactional
    fun deleteGroup(userId: Long, group: String) {
        val id = group.toLongOrNull()
            ?: throw IllegalArgumentException("group은 onGo 공개 API postId여야 합니다")
        delete(userId, id)
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

        val value = request.posts.firstOrNull()?.value?.firstOrNull()
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
        val requestedWorkspace = resolveRequestedWorkspace(userId, listOfNotNull(item.group))
        require(requestedWorkspace == null || channel.workspaceId == requestedWorkspace.id) {
            "integration은 요청한 group에 속하지 않습니다"
        }
        val value = item.value.firstOrNull()
        val settings = item.settings
        val title = settings?.path("title")?.asText(null)
            ?.takeIf(String::isNotBlank)
            ?: value?.title
            ?: value?.content
            ?: "영상"
        val tags = settingsTags(settings).ifEmpty { value?.tags.orEmpty() }
        PlatformUploadConfig(
            platform = channel.platform,
            videoUploadId = 0,
            channelId = channelId,
            // Keep the caller's exact metadata. PublishVideoUseCase validates it
            // against the real platform capability; silently truncating here
            // would make the public API publish different content than requested.
            title = title,
            description = settings?.path("description")?.asText(null)
                ?.takeIf(String::isNotBlank)
                ?: value?.description
                ?: value?.content,
            tags = tags,
            visibility = settingsVisibility(settings),
            thumbnailUrl = firstText(value?.image),
            customSettingsJson = settings?.takeIf { it.isObject }?.toString(),
            scheduledAt = scheduledAt,
        )
    }

    private fun settingsTags(settings: JsonNode?): List<String> {
        val tags = settings?.path("tags")
        if (tags == null || !tags.isArray) return emptyList()
        return tags.mapNotNull { tag ->
            when {
                tag.isTextual -> tag.asText()
                tag.isObject -> tag.path("value").asText(null) ?: tag.path("label").asText(null)
                else -> null
            }
        }.filter(String::isNotBlank)
    }

    private fun settingsVisibility(settings: JsonNode?): Visibility {
        val raw = settings?.path("visibility")?.asText(null)
            ?: settings?.path("type")?.asText(null)
            ?: settings?.path("privacy_level")?.asText(null)
            ?: return Visibility.PUBLIC
        return when (raw.trim().uppercase()) {
            "PRIVATE", "SELF_ONLY" -> Visibility.PRIVATE
            "UNLISTED", "MUTUAL_FOLLOW_FRIENDS" -> Visibility.UNLISTED
            else -> Visibility.PUBLIC
        }
    }

    private fun toResponse(post: PublicApiPost): PublicPostResponse {
        val uploads = videoUploadRepository.findByVideoId(post.videoId)
        val status = aggregateStatus(post, uploads)
        val payload = runCatching { objectMapper.readValue(post.payloadJson, CreatePublicPostRequest::class.java) }.getOrNull()
        val targets = payload?.posts.orEmpty().map { target ->
            val channelId = target.integration.id.toLongOrNull()
            val upload = uploads.firstOrNull { it.channelId == channelId }
            val channel = channelId?.let { channelRepository.findById(it) }
                ?.takeIf { it.userId == post.userId }
            PublicPostTargetResponse(
                integrationId = target.integration.id,
                status = upload?.status?.name ?: status.name,
                platformUrl = upload?.platformUrl,
                error = upload?.errorMessage,
                providerIdentifier = channel?.let { postizIdentifier(it.platform) },
                name = channel?.channelName,
                picture = channel?.profileImageUrl,
            )
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
            date = post.scheduledAt?.let(::formatDate),
            videoId = post.videoId,
            error = post.errorMessage,
            posts = targets,
            content = payload?.posts?.firstOrNull()?.value?.firstOrNull()?.content,
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

    private fun channelsForScope(userId: Long, group: String?): List<Channel> {
        val workspace = resolveWorkspace(userId, group)
        return if (workspace == null) channelRepository.findByUserId(userId)
        else channelRepository.findByUserIdAndWorkspaceId(userId, workspace.id!!)
    }

    private fun resolveRequestedWorkspace(userId: Long, groups: List<String>): Workspace? {
        require(groups.size <= 1) { "한 번의 Postiz 요청에는 하나의 group만 사용할 수 있습니다" }
        return resolveWorkspace(userId, groups.singleOrNull())
    }

    private fun resolveWorkspace(userId: Long, group: String?): Workspace? {
        if (group.isNullOrBlank()) return null
        val normalized = group.trim()
        val workspace = workspaceRepository.findAccessibleByUserId(userId).firstOrNull {
            it.id?.toString() == normalized || it.slug == normalized
        }
        if (workspace == null) throw NotFoundException("group", group)
        return workspace
    }

    private fun parseType(value: String): PublicApiPostType = when (value.trim().lowercase()) {
        "now" -> PublicApiPostType.NOW
        "schedule", "scheduled" -> PublicApiPostType.SCHEDULE
        "draft" -> PublicApiPostType.DRAFT
        else -> throw IllegalArgumentException("type은 now, schedule 또는 draft여야 합니다")
    }

    private fun parseDate(value: String): LocalDateTime = runCatching {
        LocalDateTime.parse(value)
    }.recoverCatching {
        OffsetDateTime.parse(value).toInstant().atZone(DOMAIN_ZONE).toLocalDateTime()
    }.recoverCatching {
        Instant.parse(value).atZone(DOMAIN_ZONE).toLocalDateTime()
    }.getOrElse { throw IllegalArgumentException("date는 ISO-8601 형식이어야 합니다") }

    /** Postiz dates are UTC ISO-8601 values; the domain stores the server wall clock. */
    private fun formatDate(value: LocalDateTime): String =
        value.atZone(DOMAIN_ZONE).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString()

    private fun roundUpToQuarter(value: LocalDateTime): LocalDateTime {
        val remainder = value.minute % 15
        return if (remainder == 0) value else value.plusMinutes((15 - remainder).toLong())
    }

    private fun platformScheduleTime(raw: Any?): LocalDateTime? {
        val text = (raw as? Map<*, *>)?.get("scheduledAt")?.toString() ?: return null
        return runCatching { parseDate(text) }.getOrNull()
    }

    private fun firstText(node: JsonNode?): String? = when {
        node == null || node.isNull -> null
        node.isArray -> node.asSequence().mapNotNull(::firstText).firstOrNull()
        node.isTextual -> node.asText()
        node.isObject -> listOf("path", "url", "src").asSequence()
            .mapNotNull { key -> node.path(key).asText(null) }
            .firstOrNull()
        else -> null
    }

    private fun validateMediaUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("media URL이 올바르지 않습니다") }
        require(uri.scheme == "https" || uri.scheme == "http") { "media URL은 http 또는 https여야 합니다" }
        require(!uri.host.isNullOrBlank()) { "media URL의 호스트가 필요합니다" }
    }

    private fun safeError(error: RuntimeException): String =
        (error.message ?: "공개 API 게시에 실패했습니다").take(2_000)

}
