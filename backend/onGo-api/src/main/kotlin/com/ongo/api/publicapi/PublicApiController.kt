package com.ongo.api.publicapi

import com.ongo.api.config.CurrentUser
import com.ongo.application.publicapi.ChangePublicPostStatusRequest
import com.ongo.application.publicapi.CreatePublicPostRequest
import com.ongo.application.publicapi.PublicApiUseCase
import com.ongo.application.publicapi.PublicIntegrationResponse
import com.ongo.application.publicapi.PublicPostResponse
import com.ongo.common.ResData
import com.ongo.common.exception.ForbiddenException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Postiz public API의 핵심 integrations/posts 계약. API 키만 허용한다. */
@Tag(name = "Public API", description = "Postiz 호환 자동화 API. Authorization: Bearer og_live_... 또는 X-API-Key")
@RestController
@RequestMapping("/public/v1", "/api/v1/public/v1")
class PublicApiController(
    private val useCase: PublicApiUseCase,
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

    @Operation(summary = "게시물 생성", description = "type=now|schedule|draft. posts[].integration.id는 연결된 onGo 채널 ID입니다.")
    @PostMapping("/posts")
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: CreatePublicPostRequest,
    ): ResponseEntity<ResData<PublicPostResponse>> {
        requireApiKey(authentication)
        return ResData.success(useCase.create(userId, request))
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

    @Operation(summary = "게시물 상태 변경", description = "draft 게시물을 schedule 또는 now로 전환합니다.")
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

    private fun requireApiKey(authentication: Authentication) {
        if (!authentication.authorities.any { it.authority == "AUTH_API_KEY" }) {
            throw ForbiddenException("Public API는 개인 API 키 인증만 지원합니다")
        }
    }
}
