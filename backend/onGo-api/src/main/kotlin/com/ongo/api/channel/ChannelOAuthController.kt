package com.ongo.api.channel

import com.ongo.api.config.CurrentUser
import com.ongo.application.channel.ChannelOAuthAuthorizationUseCase
import com.ongo.application.channel.dto.ChannelOAuthAuthorizationResponse
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "채널 OAuth", description = "서버 설정을 사용하는 채널 연결 URL")
@RestController
@RequestMapping("/api/v1/channels/oauth")
class ChannelOAuthController(
    private val authorizationUseCase: ChannelOAuthAuthorizationUseCase,
) {
    @Operation(summary = "채널 OAuth URL 생성")
    @GetMapping("/{platform}/authorization-url")
    fun authorizationUrl(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable platform: String,
        @RequestParam redirectUri: String,
        @RequestParam state: String,
        @RequestParam(required = false) codeChallenge: String?,
    ): ResponseEntity<ResData<ChannelOAuthAuthorizationResponse>> = ResData.success(
        authorizationUseCase.authorizationUrl(userId, platform, redirectUri, state, codeChallenge),
    )
}
