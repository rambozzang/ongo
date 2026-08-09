package com.ongo.api.publicapi

import com.ongo.api.config.CurrentUser
import com.ongo.application.publicapi.ChangePublicPostStatusRequest
import com.ongo.application.publicapi.CreatePublicPostRequest
import com.ongo.application.publicapi.PublicApiUseCase
import com.ongo.application.publicapi.PublicApiAnalyticsUseCase
import com.ongo.application.publicapi.PublicApiMediaUseCase
import com.ongo.application.publicapi.PublicIntegrationResponse
import com.ongo.application.publicapi.PublicPostCreatedResponse
import com.ongo.application.publicapi.PublicPostResponse
import com.ongo.application.publicapi.PublicIntegrationSettingsResponse
import com.ongo.application.publicapi.PublicConnectionResponse
import com.ongo.application.publicapi.PublicRemoteMediaUploadRequest
import com.ongo.application.publicapi.PublicMissingContentResponse
import com.ongo.application.publicapi.PublicReleaseIdRequest
import com.ongo.application.publicapi.PublicGroupResponse
import com.ongo.application.publicapi.PublicNotificationListResponse
import com.ongo.application.channel.ChannelUseCase
import com.ongo.application.notification.NotificationUseCase
import com.ongo.application.workspace.WorkspaceUseCase
import com.ongo.common.ResData
import com.ongo.common.exception.ForbiddenException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** Postiz public API의 핵심 integrations/posts 계약. API 키만 허용한다. */
@Tag(name = "Public API", description = "Postiz 호환 자동화 API. Authorization: Bearer og_live_... 또는 X-API-Key")
@RestController
@RequestMapping("/public/v1", "/api/v1/public/v1")
class PublicApiController(
    private val useCase: PublicApiUseCase,
    private val mediaUseCase: PublicApiMediaUseCase,
    private val analyticsUseCase: PublicApiAnalyticsUseCase,
    private val channelUseCase: ChannelUseCase,
    private val notificationUseCase: NotificationUseCase,
    private val workspaceUseCase: WorkspaceUseCase,
) {

    @Operation(summary = "organization groups 조회")
    @GetMapping("/groups")
    fun groups(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResponseEntity<ResData<List<PublicGroupResponse>>> {
        requireApiKey(authentication)
        val groups = workspaceUseCase.listWorkspaces(userId)
            .map { PublicGroupResponse(id = it.id.toString(), name = it.name) }
        return ResData.success(groups)
    }

    @Operation(summary = "연결된 integrations 조회")
    @GetMapping("/integrations")
    fun integrations(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResponseEntity<ResData<List<PublicIntegrationResponse>>> {
        requireApiKey(authentication)
        return ResData.success(useCase.integrations(userId))
    }

    @Operation(summary = "integration 연결 상태 조회")
    @GetMapping("/is-connected")
    fun isConnected(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResponseEntity<ResData<PublicConnectionResponse>> {
        requireApiKey(authentication)
        // API key가 사용자에게 발급되어 이 요청까지 도달했다는 것은 onGo 공개 API
        // 연결이 유효하다는 뜻이다. 플랫폼 계정 연결 여부는 integrations에서 확인한다.
        return ResData.success(PublicConnectionResponse(connected = true))
    }

    @Operation(summary = "알림 목록 조회")
    @GetMapping("/notifications")
    fun notifications(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
    ): ResponseEntity<ResData<PublicNotificationListResponse>> {
        requireApiKey(authentication)
        require(page >= 0) { "page는 0 이상이어야 합니다" }
        val limit = 100
        val result = notificationUseCase.listNotifications(userId, page, limit)
        val response = PublicNotificationListResponse(
            notifications = result.notifications.map { notification ->
                com.ongo.application.publicapi.PublicNotificationResponse(
                    id = notification.id.toString(),
                    content = listOf(notification.title, notification.message)
                        .filter(String::isNotBlank)
                        .joinToString("\n"),
                    link = notification.referenceType?.let { type ->
                        notification.referenceId?.let { id -> "/$type/$id" }
                    },
                    createdAt = notification.createdAt,
                )
            },
            total = result.totalElements,
            page = result.page,
            limit = limit,
            hasMore = (result.page + 1L) * limit < result.totalElements,
        )
        return ResData.success(response)
    }

    @Operation(summary = "integration 게시 capability 조회")
    @GetMapping("/integration-settings/{integrationId}")
    fun integrationSettings(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
    ): ResponseEntity<ResData<PublicIntegrationSettingsResponse>> {
        requireApiKey(authentication)
        return ResData.success(useCase.integrationSettings(userId, integrationId))
    }

    @Operation(summary = "integration 연결 해제")
    @DeleteMapping("/integrations/{integrationId}")
    fun deleteIntegration(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
    ): ResponseEntity<ResData<Nothing?>> {
        requireApiKey(authentication)
        val channelId = integrationId.toLongOrNull()
            ?: throw IllegalArgumentException("integration id는 onGo 채널 ID여야 합니다")
        channelUseCase.disconnectChannel(userId, channelId)
        return ResData.success(null)
    }

    @Operation(summary = "미디어 업로드")
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<ResData<com.ongo.application.publicapi.PublicMediaUploadResponse>> {
        requireApiKey(authentication)
        return ResData.success(mediaUseCase.upload(userId, file))
    }

    @Operation(summary = "URL에서 미디어 업로드")
    @PostMapping("/upload-from-url")
    fun uploadFromUrl(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: PublicRemoteMediaUploadRequest,
    ): ResponseEntity<ResData<com.ongo.application.publicapi.PublicMediaUploadResponse>> {
        requireApiKey(authentication)
        return ResData.success(mediaUseCase.uploadFromUrl(userId, request.url, request.filename))
    }

    @Operation(summary = "integration의 다음 예약 가능 시간 조회")
    @GetMapping("/find-slot/{integrationId}")
    fun findSlot(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
    ): ResponseEntity<ResData<com.ongo.application.publicapi.PublicAvailableSlotResponse>> {
        requireApiKey(authentication)
        return ResData.success(useCase.findAvailableSlot(userId, integrationId))
    }

    @Operation(summary = "게시물 생성", description = "type=now|schedule|draft. posts[].integration.id는 연결된 onGo 채널 ID입니다.")
    @PostMapping("/posts")
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: CreatePublicPostRequest,
    ): ResponseEntity<ResData<List<PublicPostCreatedResponse>>> {
        requireApiKey(authentication)
        val result = useCase.create(userId, request)
        return ResData.success(result.posts.map { target ->
            PublicPostCreatedResponse(postId = result.id, integration = target.integrationId)
        })
    }

    @Operation(summary = "게시물 목록 조회")
    @GetMapping("/posts")
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
    ): ResponseEntity<ResData<List<PublicPostResponse>>> {
        requireApiKey(authentication)
        return ResData.success(useCase.list(userId, limit, startDate, endDate))
    }

    @Operation(summary = "게시물 상세 조회")
    @GetMapping("/posts/{id}")
    fun get(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<PublicPostResponse>> {
        requireApiKey(authentication)
        return ResData.success(useCase.get(userId, id))
    }

    @Operation(summary = "게시물 상태 변경", description = "draft 또는 예약 중인 게시물을 draft/schedule 상태로 전환합니다.")
    @PutMapping("/posts/{id}/status")
    fun changeStatus(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody request: ChangePublicPostStatusRequest,
    ): ResponseEntity<ResData<PublicPostResponse>> {
        requireApiKey(authentication)
        return ResData.success(useCase.changeStatus(userId, id, request))
    }

    @Operation(summary = "게시물 상태 변경(Postiz 호환 경로)")
    @PostMapping("/posts/{id}/change-status")
    fun changeStatusCompat(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody request: ChangePublicPostStatusRequest,
    ): ResponseEntity<ResData<PublicPostResponse>> {
        requireApiKey(authentication)
        return ResData.success(useCase.changeStatus(userId, id, request))
    }

    @Operation(summary = "게시물에 필요한 콘텐츠 조회")
    @GetMapping("/posts/{id}/missing")
    fun missingContent(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<List<PublicMissingContentResponse>>> {
        requireApiKey(authentication)
        return ResData.success(useCase.missingContent(userId, id))
    }

    @Operation(summary = "외부 release id 연결")
    @PutMapping("/posts/{id}/release-id")
    fun connectReleaseId(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody request: PublicReleaseIdRequest,
    ): ResponseEntity<ResData<PublicPostResponse>> {
        requireApiKey(authentication)
        return ResData.success(useCase.connectReleaseId(userId, id, request))
    }

    @Operation(summary = "초안 게시물 삭제")
    @DeleteMapping("/posts/{id}")
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        requireApiKey(authentication)
        useCase.deleteDraft(userId, id)
        return ResData.success(null)
    }

    @Operation(summary = "게시물 분석 조회")
    @GetMapping("/analytics/post/{postId}")
    fun postAnalytics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable postId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<List<com.ongo.application.publicapi.PublicAnalyticsMetric>>> {
        requireApiKey(authentication)
        return ResData.success(analyticsUseCase.post(userId, postId, days))
    }

    @Operation(summary = "integration 분석 조회")
    @GetMapping("/analytics/{integrationId}")
    fun integrationAnalytics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<List<com.ongo.application.publicapi.PublicAnalyticsMetric>>> {
        requireApiKey(authentication)
        return ResData.success(analyticsUseCase.platform(userId, integrationId, days))
    }

    private fun requireApiKey(authentication: Authentication) {
        if (!authentication.authorities.any { it.authority == "AUTH_API_KEY" }) {
            throw ForbiddenException("Public API는 개인 API 키 인증만 지원합니다")
        }
    }
}
