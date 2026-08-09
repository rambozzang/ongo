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
import com.ongo.application.publicapi.PublicIntegrationToolRequest
import com.ongo.application.publicapi.PublicIntegrationToolResult
import com.ongo.application.publicapi.PublicVideoFunctionRequest
import com.ongo.application.publicapi.VideoFunctionUseCase
import com.ongo.application.publicapi.PublicOAuthUseCase
import com.ongo.application.publicapi.PublicOAuthUrlResponse
import com.ongo.application.publicapi.PublicConnectionResponse
import com.ongo.application.publicapi.PublicRemoteMediaUploadRequest
import com.ongo.application.publicapi.PublicGenerateVideoRequest
import com.ongo.application.publicapi.GeneratedVideoUseCase
import com.ongo.application.publicapi.PublicMissingContentResponse
import com.ongo.application.publicapi.PublicPostListItem
import com.ongo.application.publicapi.PublicReleaseIdRequest
import com.ongo.application.publicapi.PublicGroupResponse
import com.ongo.application.publicapi.PublicNotificationListResponse
import com.ongo.application.channel.ChannelUseCase
import com.ongo.application.notification.NotificationUseCase
import com.ongo.application.workspace.WorkspaceUseCase
import com.ongo.common.exception.ForbiddenException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
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
import java.net.URI

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
    private val generatedVideoUseCase: GeneratedVideoUseCase,
    private val videoFunctionUseCase: VideoFunctionUseCase,
    private val publicOAuthUseCase: PublicOAuthUseCase,
) {

    @Operation(summary = "organization groups 조회")
    @GetMapping("/groups")
    fun groups(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        val groups = workspaceUseCase.listWorkspaces(userId)
            .map { PublicGroupResponse(id = it.id.toString(), name = it.name) }
        return raw(groups)
    }

    @Operation(summary = "연결된 integrations 조회")
    @GetMapping("/integrations")
    fun integrations(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(useCase.integrations(userId))
    }

    @Operation(summary = "integration 연결 상태 조회")
    @GetMapping("/is-connected")
    fun isConnected(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        // API key가 사용자에게 발급되어 이 요청까지 도달했다는 것은 onGo 공개 API
        // 연결이 유효하다는 뜻이다. 플랫폼 계정 연결 여부는 integrations에서 확인한다.
        return raw(PublicConnectionResponse(connected = true))
    }

    @Operation(summary = "OAuth 채널 연결 URL 생성")
    @GetMapping("/social/{integration}")
    fun social(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integration: String,
        @RequestParam(required = false) refresh: String?,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(publicOAuthUseCase.authorizationUrl(userId, integration, refresh))
    }

    /** Provider callback; the signed state restores the API-key owner's user context. */
    @Operation(hidden = true)
    @GetMapping("/social/callback")
    fun socialCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        val redirect = if (!error.isNullOrBlank()) {
            publicOAuthUseCase.failure(state, error)
        } else {
            require(!code.isNullOrBlank() && !state.isNullOrBlank()) { "OAuth callback이 올바르지 않습니다" }
            publicOAuthUseCase.complete(code, state)
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect ?: "/channels")).build()
    }

    @Operation(summary = "알림 목록 조회")
    @GetMapping("/notifications")
    fun notifications(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
    ): ResponseEntity<Any> {
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
        return raw(response)
    }

    @Operation(summary = "integration 게시 capability 조회")
    @GetMapping("/integration-settings/{integrationId}")
    fun integrationSettings(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        // Postiz exposes capability data under one top-level `output` property.
        return raw(mapOf("output" to useCase.integrationSettings(userId, integrationId).output))
    }

    @Operation(summary = "integration provider tool 실행", description = "integration-settings에서 반환된 methodName만 실행합니다.")
    @PostMapping("/integration-trigger/{integrationId}")
    fun integrationTrigger(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
        @RequestBody request: PublicIntegrationToolRequest,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(useCase.triggerIntegrationTool(userId, integrationId, request))
    }

    @Operation(summary = "integration 연결 해제")
    @DeleteMapping("/integrations/{integrationId}")
    fun deleteIntegration(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        val channelId = integrationId.toLongOrNull()
            ?: throw IllegalArgumentException("integration id는 onGo 채널 ID여야 합니다")
        channelUseCase.disconnectChannel(userId, channelId)
        return raw(mapOf("id" to integrationId))
    }

    @Operation(summary = "미디어 업로드")
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(mediaUseCase.upload(userId, file))
    }

    @Operation(summary = "URL에서 미디어 업로드")
    @PostMapping("/upload-from-url")
    fun uploadFromUrl(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: PublicRemoteMediaUploadRequest,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(mediaUseCase.uploadFromUrl(userId, request.url, request.filename))
    }

    @Operation(summary = "텍스트 슬라이드 영상 생성", description = "생성된 영상을 영상 목록에 저장하고 즉시 게시 대상에 사용할 수 있습니다.")
    @PostMapping("/generate-video")
    fun generateVideo(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: PublicGenerateVideoRequest,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(generatedVideoUseCase.generate(userId, request))
    }

    @Operation(summary = "영상 생성 함수 실행", description = "현재 지원하는 image-text-slides 음성 목록을 조회합니다.")
    @PostMapping("/video/function")
    fun videoFunction(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: PublicVideoFunctionRequest,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(videoFunctionUseCase.execute(request))
    }

    @Operation(summary = "integration의 다음 예약 가능 시간 조회")
    @GetMapping("/find-slot/{integrationId}")
    fun findSlot(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(useCase.findAvailableSlot(userId, integrationId))
    }

    @Operation(summary = "게시물 생성", description = "type=now|schedule|draft. posts[].integration.id는 연결된 onGo 채널 ID입니다.")
    @PostMapping("/posts")
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: CreatePublicPostRequest,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        val result = useCase.create(userId, request)
        return raw(result.posts.map { target ->
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
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        require(limit in 1..100) { "limit은 1~100 사이여야 합니다" }
        val posts = useCase.list(userId, limit, startDate, endDate)
            .flatMap { post ->
                post.posts.ifEmpty { listOf(null) }.map { target ->
                    PublicPostListItem(
                        id = post.id,
                        content = post.content.orEmpty(),
                        publishDate = post.date,
                        releaseURL = target?.platformUrl,
                        state = postizState(post.state),
                        integration = target?.let {
                            buildMap {
                                put("id", it.integrationId)
                                it.providerIdentifier?.let { value -> put("providerIdentifier", value) }
                                it.name?.let { value -> put("name", value) }
                                it.picture?.let { value -> put("picture", value) }
                            }
                        },
                    )
                }
            }
        return raw(mapOf("posts" to posts))
    }

    @Operation(summary = "게시물 상세 조회")
    @GetMapping("/posts/{id}")
    fun get(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(useCase.get(userId, id))
    }

    @Operation(summary = "게시물 상태 변경", description = "draft 또는 예약 중인 게시물을 draft/schedule 상태로 전환합니다.")
    @PutMapping("/posts/{id}/status")
    fun changeStatus(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody request: ChangePublicPostStatusRequest,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        val post = useCase.changeStatus(userId, id, request)
        return raw(mapOf("id" to post.id, "state" to post.state))
    }

    @Operation(summary = "게시물 상태 변경(Postiz 호환 경로)")
    @PostMapping("/posts/{id}/change-status")
    fun changeStatusCompat(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody request: ChangePublicPostStatusRequest,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        val post = useCase.changeStatus(userId, id, request)
        return raw(mapOf("id" to post.id, "state" to post.state))
    }

    @Operation(summary = "게시물에 필요한 콘텐츠 조회")
    @GetMapping("/posts/{id}/missing")
    fun missingContent(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(useCase.missingContent(userId, id))
    }

    @Operation(summary = "외부 release id 연결")
    @PutMapping("/posts/{id}/release-id")
    fun connectReleaseId(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody request: PublicReleaseIdRequest,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        useCase.connectReleaseId(userId, id, request)
        return raw(mapOf("id" to id.toString(), "releaseId" to request.releaseId))
    }

    @Operation(summary = "게시물 삭제", description = "초안은 삭제하고, 아직 전송되지 않은 예약 게시물은 취소한다. 외부 게시가 진행 중이거나 완료된 게시물은 중복 게시 방지를 위해 거부한다.")
    @DeleteMapping("/posts/{id}")
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        useCase.delete(userId, id)
        return raw(mapOf("id" to id.toString()))
    }

    @Operation(summary = "그룹 게시물 삭제", description = "한 번의 다중 채널 생성 요청으로 묶인 게시물을 삭제/취소한다. onGo에서는 생성 응답의 postId가 그룹 ID다.")
    @DeleteMapping("/posts/group/{group}")
    fun deleteGroup(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable group: String,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        useCase.deleteGroup(userId, group)
        return raw(mapOf("id" to group))
    }

    @Operation(summary = "게시물 분석 조회")
    @GetMapping("/analytics/post/{postId}")
    fun postAnalytics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable postId: Long,
        @RequestParam(defaultValue = "30") days: Int,
        @RequestParam(required = false) date: Int?,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(analyticsUseCase.post(userId, postId, date ?: days))
    }

    @Operation(summary = "integration 분석 조회")
    @GetMapping("/analytics/{integrationId}")
    fun integrationAnalytics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable integrationId: String,
        @RequestParam(defaultValue = "30") days: Int,
        @RequestParam(required = false) date: Int?,
    ): ResponseEntity<Any> {
        requireApiKey(authentication)
        return raw(analyticsUseCase.platform(userId, integrationId, date ?: days))
    }

    private fun requireApiKey(authentication: Authentication) {
        if (!authentication.authorities.any { it.authority == "AUTH_API_KEY" }) {
            throw ForbiddenException("Public API는 개인 API 키 인증만 지원합니다")
        }
    }

    /** Public API responses intentionally follow Postiz's raw JSON contract. */
    private fun raw(value: Any): ResponseEntity<Any> = ResponseEntity.ok(value)

    private fun postizState(state: String): String = when (state.uppercase()) {
        "DRAFT" -> "DRAFT"
        "PUBLISHED" -> "PUBLISHED"
        "FAILED", "REJECTED", "CANCELLED", "PARTIALLY_PUBLISHED", "UNCONFIRMED" -> "ERROR"
        else -> "QUEUE"
    }
}
