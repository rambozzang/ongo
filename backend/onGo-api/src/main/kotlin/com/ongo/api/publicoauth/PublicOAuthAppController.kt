package com.ongo.api.publicoauth

import com.ongo.api.config.CurrentUser
import com.ongo.application.publicoauth.CreatePublicOAuthAppRequest
import com.ongo.application.publicoauth.PublicOAuthAppCreatedResponse
import com.ongo.application.publicoauth.PublicOAuthAppResponse
import com.ongo.application.publicoauth.PublicOAuthAppUseCase
import com.ongo.application.publicoauth.PublicOAuthTokenResponseSummary
import com.ongo.common.ResData
import com.ongo.common.exception.ForbiddenException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Developer OAuth apps", description = "Postiz 호환 OAuth 앱과 승인 토큰 관리")
@RestController
@RequestMapping("/api/v1/settings/oauth-apps")
class PublicOAuthAppController(
    private val useCase: PublicOAuthAppUseCase,
) {
    @GetMapping
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResData<List<PublicOAuthAppResponse>> {
        requireInteractiveSession(authentication)
        return ResData(data = useCase.listApps(userId))
    }

    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: CreatePublicOAuthAppRequest,
    ): ResData<PublicOAuthAppCreatedResponse> {
        requireInteractiveSession(authentication)
        return ResData(
            data = useCase.createApp(userId, request),
            message = "OAuth 앱이 생성되었습니다. client secret은 이번에만 표시됩니다.",
        )
    }

    @PostMapping("/{id}/rotate-secret")
    fun rotateSecret(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResData<PublicOAuthAppCreatedResponse> {
        requireInteractiveSession(authentication)
        return ResData(
            data = useCase.rotateSecret(userId, id),
            message = "client secret이 교체되었습니다. 이전 secret은 즉시 무효화되었습니다.",
        )
    }

    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResData<Nothing?> {
        requireInteractiveSession(authentication)
        useCase.deleteApp(userId, id)
        return ResData(message = "OAuth 앱과 승인 토큰이 폐기되었습니다.")
    }

    @GetMapping("/tokens")
    fun tokens(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
    ): ResData<List<PublicOAuthTokenResponseSummary>> {
        requireInteractiveSession(authentication)
        return ResData(data = useCase.listTokens(userId))
    }

    @DeleteMapping("/tokens/{id}")
    fun revokeToken(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResData<Nothing?> {
        requireInteractiveSession(authentication)
        useCase.revokeToken(userId, id)
        return ResData(message = "OAuth 토큰이 폐기되었습니다.")
    }

    private fun requireInteractiveSession(authentication: Authentication) {
        if (authentication.authorities.any {
                it.authority == "AUTH_API_KEY" || it.authority == "AUTH_PUBLIC_OAUTH"
            }) {
            throw ForbiddenException("OAuth 앱 관리에는 브라우저 로그인 세션이 필요합니다")
        }
    }
}
