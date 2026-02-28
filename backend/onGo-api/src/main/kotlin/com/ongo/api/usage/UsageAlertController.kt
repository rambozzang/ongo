package com.ongo.api.usage

import com.ongo.api.config.CurrentUser
import com.ongo.application.usage.UpsertAlertConfigRequest
import com.ongo.application.usage.UsageAlertConfigResponse
import com.ongo.application.usage.UsageAlertService
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "사용량 알림", description = "사용량 알림 설정 관리")
@RestController
@RequestMapping("/api/v1/usage/alerts")
class UsageAlertController(
    private val usageAlertService: UsageAlertService,
) {

    @Operation(summary = "알림 설정 조회")
    @GetMapping
    fun getAlerts(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<List<UsageAlertConfigResponse>>> =
        ResData.success(usageAlertService.getAlertConfigs(userId))

    @Operation(summary = "알림 설정 변경")
    @PutMapping
    fun upsertAlert(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: UpsertAlertConfigRequest,
    ): ResponseEntity<ResData<UsageAlertConfigResponse>> =
        ResData.success(usageAlertService.upsertAlertConfig(userId, request))

    @Operation(summary = "알림 비활성화")
    @DeleteMapping("/{alertType}")
    fun deleteAlert(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable alertType: String,
    ): ResponseEntity<ResData<Unit>> {
        usageAlertService.deleteAlertConfig(userId, alertType)
        return ResData.success(Unit, "알림이 비활성화되었습니다")
    }
}
