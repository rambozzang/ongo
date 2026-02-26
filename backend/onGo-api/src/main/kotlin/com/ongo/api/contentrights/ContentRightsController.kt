package com.ongo.api.contentrights

import com.ongo.application.contentrights.ContentRightsUseCase
import com.ongo.application.contentrights.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 저작권", description = "콘텐츠 저작권 및 라이선싱 관리")
@RestController
@RequestMapping("/api/v1/content-rights")
class ContentRightsController(
    private val contentRightsUseCase: ContentRightsUseCase
) {

    @Operation(summary = "저작권 요약 조회")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<RightsSummaryResponse>> {
        val result = contentRightsUseCase.getSummary(userId)
        return ResData.success(result)
    }

    @Operation(summary = "저작권 목록 조회")
    @GetMapping
    fun getAll(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ContentRightResponse>>> {
        val result = contentRightsUseCase.getAll(userId)
        return ResData.success(result)
    }

    @Operation(summary = "저작권 등록")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateRightRequest,
    ): ResponseEntity<ResData<ContentRightResponse>> {
        val result = contentRightsUseCase.create(userId, request)
        return ResData.success(result, "저작권 정보가 등록되었습니다")
    }

    @Operation(summary = "저작권 수정")
    @PutMapping("/{id}")
    fun update(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateRightRequest,
    ): ResponseEntity<ResData<ContentRightResponse>> {
        val result = contentRightsUseCase.update(userId, id, request)
        return ResData.success(result, "저작권 정보가 수정되었습니다")
    }

    @Operation(summary = "저작권 삭제")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        contentRightsUseCase.delete(userId, id)
        return ResData.success(null, "저작권 정보가 삭제되었습니다")
    }

    @Operation(summary = "저작권 알림 조회")
    @GetMapping("/alerts")
    fun getAlerts(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<RightsAlertResponse>>> {
        val result = contentRightsUseCase.getAlerts(userId)
        return ResData.success(result)
    }

    @Operation(summary = "저작권 알림 읽음 처리")
    @PutMapping("/alerts/{alertId}/read")
    fun markAlertRead(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable alertId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        contentRightsUseCase.markAlertRead(userId, alertId)
        return ResData.success(null)
    }
}
