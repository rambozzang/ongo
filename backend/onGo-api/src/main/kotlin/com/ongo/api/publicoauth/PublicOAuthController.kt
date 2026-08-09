package com.ongo.api.publicoauth

import com.ongo.api.config.CurrentUser
import com.ongo.application.publicoauth.PublicOAuthAppUseCase
import com.ongo.application.publicoauth.PublicOAuthAuthorizationRequest
import com.ongo.application.publicoauth.PublicOAuthTokenRequest
import com.ongo.application.publicoauth.PublicOAuthTokenResponse
import com.ongo.common.ResData
import com.ongo.common.exception.ForbiddenException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** OAuth2 endpoints matching the Postiz developer-app Authorization Code flow. */
@Tag(name = "Developer OAuth2", description = "Postiz 호환 developer app authorization code flow")
@RestController
@RequestMapping("/oauth", "/api/v1/oauth")
class PublicOAuthController(
    private val useCase: PublicOAuthAppUseCase,
) {
    @Operation(summary = "OAuth 동의 화면 정보 조회")
    @GetMapping("/authorize/request")
    fun authorizationRequest(
        @RequestParam clientId: String,
        @RequestParam(defaultValue = "code") responseType: String,
        authentication: Authentication,
    ): ResponseEntity<PublicOAuthAuthorizationRequest> {
        requireInteractiveSession(authentication)
        return ResponseEntity.ok(useCase.authorizationRequest(clientId, responseType))
    }

    @Operation(summary = "OAuth 권한 승인 또는 거부")
    @PostMapping("/authorize/decision")
    fun decideAuthorization(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        authentication: Authentication,
        @RequestBody request: PublicOAuthDecisionRequest,
    ): ResponseEntity<PublicOAuthDecisionResponse> {
        requireInteractiveSession(authentication)
        val redirect = useCase.decideAuthorization(
            userId = userId,
            clientId = request.clientId,
            responseType = request.responseType,
            state = request.state,
            approved = request.approved,
        )
        // The consent page is an authenticated SPA. Return the validated redirect
        // instead of making XHR follow a cross-origin 302; the browser performs
        // the final navigation explicitly after the user clicks approve.
        return ResponseEntity.ok(PublicOAuthDecisionResponse(redirect))
    }

    /** Postiz uses a raw OAuth response rather than the normal onGo ResData wrapper here. */
    @Operation(summary = "OAuth authorization code를 access token으로 교환")
    @PostMapping("/token")
    fun exchangeToken(@RequestBody request: PublicOAuthTokenRequest): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(useCase.exchangeToken(request))
        } catch (error: IllegalArgumentException) {
            val code = error.message?.takeIf { it in OAUTH_ERRORS } ?: "invalid_request"
            val status = if (code == "invalid_client") HttpStatus.UNAUTHORIZED else HttpStatus.BAD_REQUEST
            ResponseEntity.status(status).body(OAuthErrorResponse(code, code))
        }

    private fun requireInteractiveSession(authentication: Authentication) {
        if (authentication.authorities.any {
                it.authority == "AUTH_API_KEY" || it.authority == "AUTH_PUBLIC_OAUTH"
            }) {
            throw ForbiddenException("OAuth 앱 권한 설정에는 브라우저 로그인 세션이 필요합니다")
        }
    }

    companion object {
        private val OAUTH_ERRORS = setOf(
            "invalid_client",
            "invalid_grant",
            "unsupported_grant_type",
            "invalid_request",
        )
    }
}

data class PublicOAuthDecisionRequest(
    val clientId: String,
    val responseType: String = "code",
    val state: String? = null,
    val approved: Boolean,
)

data class PublicOAuthDecisionResponse(
    val redirectUrl: String,
)

data class OAuthErrorResponse(
    val error: String,
    val errorDescription: String? = null,
)
