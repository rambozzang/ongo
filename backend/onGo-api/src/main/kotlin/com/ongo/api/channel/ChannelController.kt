package com.ongo.api.channel

import com.ongo.application.channel.dto.*
import com.ongo.api.config.CurrentUser
import com.ongo.application.channel.ChannelUseCase
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 플랫폼 채널 연동 및 관리 API를 제공하는 컨트롤러
 */
@Tag(name = "채널 관리", description = "플랫폼 채널 연동, 해제, 정보 갱신 관리")
@RestController
@RequestMapping("/api/v1/channels")
class ChannelController(
    private val channelUseCase: ChannelUseCase
) {


    @Operation(
        summary = "채널 목록 조회",
        description = "현재 사용자가 연동한 모든 플랫폼 채널 목록을 조회합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "채널 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping
    fun listChannels(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<ChannelListResponse>> {
        val result = channelUseCase.listChannels(userId)
        return ResData.success(result)
    }

    @Operation(
        summary = "채널 연동",
        description = "지정된 플랫폼의 채널을 OAuth 인가 코드를 사용하여 연동합니다. 지원 플랫폼: youtube, tiktok, instagram, naverclip"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "채널 연동 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (지원하지 않는 플랫폼 또는 유효하지 않은 인가 코드)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/connect/{platform}")
    fun connectChannel(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "연동할 플랫폼 (youtube, tiktok, instagram, naverclip)") @PathVariable platform: String,
        @Valid @RequestBody request: ConnectChannelRequest
    ): ResponseEntity<ResData<ConnectChannelResponse>> {
        val result = channelUseCase.connectChannel(userId, platform, request)
        return ResData.success(result, "채널이 연동되었습니다")
    }

    @Operation(
        summary = "채널 연동 해제",
        description = "지정된 채널의 연동을 해제합니다. 해당 채널의 플랫폼 토큰이 삭제됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "채널 연동 해제 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "채널을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @DeleteMapping("/{id}")
    fun disconnectChannel(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "연동 해제할 채널 ID") @PathVariable id: Long
    ): ResponseEntity<ResData<Nothing?>> {
        channelUseCase.disconnectChannel(userId, id)
        return ResData.success(null, "채널 연동이 해제되었습니다")
    }

    @Operation(
        summary = "채널 정보 갱신",
        description = "지정된 채널의 최신 정보를 플랫폼 API에서 가져와 갱신합니다. (구독자 수, 프로필 이미지 등)"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "채널 정보 갱신 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "채널을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/{id}/sync")
    fun refreshChannel(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "정보를 갱신할 채널 ID") @PathVariable id: Long
    ): ResponseEntity<ResData<ChannelResponse>> {
        val result = channelUseCase.refreshChannelInfo(userId, id)
        return ResData.success(result)
    }
}
