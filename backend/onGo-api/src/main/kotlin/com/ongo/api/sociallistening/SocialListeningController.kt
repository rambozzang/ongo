package com.ongo.api.sociallistening

import com.ongo.application.sociallistening.SocialListeningUseCase
import com.ongo.application.sociallistening.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "소셜 리스닝", description = "브랜드 멘션 모니터링 및 키워드 알림 관리")
@RestController
@RequestMapping("/api/v1/social-listening")
class SocialListeningController(
    private val socialListeningUseCase: SocialListeningUseCase
) {

    @Operation(summary = "리스닝 리포트 조회")
    @GetMapping("/report")
    fun getReport(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "14d") period: String,
    ): ResponseEntity<ResData<ListeningReportResponse>> {
        val result = socialListeningUseCase.getReport(userId, period)
        return ResData.success(result)
    }

    @Operation(summary = "키워드 알림 추가")
    @PostMapping("/alerts")
    fun createAlert(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateKeywordAlertRequest,
    ): ResponseEntity<ResData<KeywordAlertResponse>> {
        val result = socialListeningUseCase.createAlert(userId, request)
        return ResData.success(result, "키워드 알림이 추가되었습니다")
    }

    @Operation(summary = "키워드 알림 토글")
    @PutMapping("/alerts/{id}/toggle")
    fun toggleAlert(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<KeywordAlertResponse>> {
        val result = socialListeningUseCase.toggleAlert(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "키워드 알림 삭제")
    @DeleteMapping("/alerts/{id}")
    fun deleteAlert(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        socialListeningUseCase.deleteAlert(userId, id)
        return ResData.success(null, "키워드 알림이 삭제되었습니다")
    }
}
