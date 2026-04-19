package com.ongo.api.revenue

import com.ongo.application.revenue.RevenueAlertConfigUseCase
import com.ongo.application.revenue.dto.RevenueAlertConfigListResponse
import com.ongo.application.revenue.dto.RevenueAlertConfigResponse
import com.ongo.application.revenue.dto.SaveRevenueAlertConfigRequest
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "수익 알림", description = "수익 알림 설정 CRUD")
@RestController
@RequestMapping("/api/v1/revenue/alerts")
class RevenueAlertController(
    private val revenueAlertConfigUseCase: RevenueAlertConfigUseCase,
) {

    @Operation(summary = "수익 알림 설정 목록 조회")
    @GetMapping("/config")
    fun getConfigs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<RevenueAlertConfigListResponse>> =
        ResData.success(revenueAlertConfigUseCase.getConfigs(userId))

    @Operation(summary = "수익 알림 설정 저장")
    @PostMapping("/config")
    fun saveConfig(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: SaveRevenueAlertConfigRequest,
    ): ResponseEntity<ResData<RevenueAlertConfigResponse>> =
        ResData.success(revenueAlertConfigUseCase.saveConfig(userId, request))

    @Operation(summary = "수익 알림 설정 수정")
    @PutMapping("/config/{id}")
    fun updateConfig(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: SaveRevenueAlertConfigRequest,
    ): ResponseEntity<ResData<RevenueAlertConfigResponse>> =
        ResData.success(revenueAlertConfigUseCase.updateConfig(userId, id, request))

    @Operation(summary = "수익 알림 설정 삭제")
    @DeleteMapping("/config/{id}")
    fun deleteConfig(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Unit>> {
        revenueAlertConfigUseCase.deleteConfig(userId, id)
        return ResData.success(Unit)
    }
}
