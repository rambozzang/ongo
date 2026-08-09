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
) {

    @Operation(summary = "연결된 integrations 조회")
    @GetMapping("/integrations")
    fun integrations(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResponseEntity<ResData<List<PublicIntegrationResponse>>> {
        requireApiKey(authentication)
        return ResData.success(useCase.integrations(userId))
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
    ): ResponseEntity<ResData<List<PublicPostResponse>>> {
        requireApiKey(authentication)
        return ResData.success(useCase.list(userId, limit))
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
