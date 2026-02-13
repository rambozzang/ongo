package com.ongo.api.settings

import com.ongo.api.config.CurrentUser
import com.ongo.application.settings.SettingsUseCase
import com.ongo.application.settings.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "설정", description = "사용자 설정 조회 및 수정")
@RestController
@RequestMapping("/api/v1/settings")
class SettingsController(
    private val settingsUseCase: SettingsUseCase,
) {

    @Operation(summary = "설정 조회", description = "사용자의 전체 설정을 조회합니다.")
    @GetMapping
    fun getSettings(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<SettingsResponse>> {
        val result = settingsUseCase.getSettings(userId)
        return ResData.success(result)
    }

    @Operation(summary = "설정 수정", description = "사용자의 전체 설정을 수정합니다.")
    @PutMapping
    fun updateSettings(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdateSettingsRequest,
    ): ResponseEntity<ResData<SettingsResponse>> {
        val result = settingsUseCase.updateSettings(userId, request)
        return ResData.success(result, "설정이 저장되었습니다")
    }

    @Operation(summary = "알림 설정 수정", description = "알림 관련 설정만 수정합니다.")
    @PutMapping("/notifications")
    fun updateNotifications(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdateNotificationsRequest,
    ): ResponseEntity<ResData<SettingsResponse>> {
        val result = settingsUseCase.updateNotifications(userId, request)
        return ResData.success(result, "알림 설정이 저장되었습니다")
    }

    @Operation(summary = "기본 설정 수정", description = "기본 업로드 설정을 수정합니다.")
    @PutMapping("/defaults")
    fun updateDefaults(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdateDefaultsRequest,
    ): ResponseEntity<ResData<SettingsResponse>> {
        val result = settingsUseCase.updateDefaults(userId, request)
        return ResData.success(result, "기본 설정이 저장되었습니다")
    }
}
