package com.ongo.api.apikey

import com.ongo.api.config.CurrentUser
import com.ongo.application.apikey.ApiKeyUseCase
import com.ongo.application.apikey.dto.ApiKeyResponse
import com.ongo.application.apikey.dto.CreateApiKeyRequest
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "개인 API 키", description = "N8N, Make, Zapier 및 스크립트 자동화용 인증 키")
@RestController
@RequestMapping("/api/v1/settings/api-keys")
class ApiKeyController(
    private val apiKeyUseCase: ApiKeyUseCase,
) {
    @Operation(summary = "개인 API 키 목록")
    @GetMapping
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(hidden = true) authentication: Authentication,
    ): ResponseEntity<ResData<List<ApiKeyResponse>>> {
        requireInteractiveSession(authentication)
        return ResData.success(apiKeyUseCase.list(userId))
    }

    @Operation(summary = "개인 API 키 생성", description = "생성된 token은 이 응답에서만 반환됩니다. 안전한 곳에 즉시 저장하세요.")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(hidden = true) authentication: Authentication,
        @RequestBody request: CreateApiKeyRequest,
    ): ResponseEntity<ResData<ApiKeyResponse>> {
        requireInteractiveSession(authentication)
        return ResData.success(apiKeyUseCase.create(userId, request), "API 키가 생성되었습니다. 원문은 이번에만 표시됩니다.")
    }

    @Operation(summary = "개인 API 키 폐기")
    @DeleteMapping("/{id}")
    fun revoke(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(hidden = true) authentication: Authentication,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        requireInteractiveSession(authentication)
        apiKeyUseCase.revoke(userId, id)
        return ResData.success(null, "API 키가 폐기되었습니다.")
    }

    private fun requireInteractiveSession(authentication: Authentication) {
        if (authentication.authorities.any { it.authority == "AUTH_API_KEY" }) {
            throw com.ongo.common.exception.ForbiddenException("API 키 관리에는 브라우저 로그인 세션이 필요합니다")
        }
    }
}
