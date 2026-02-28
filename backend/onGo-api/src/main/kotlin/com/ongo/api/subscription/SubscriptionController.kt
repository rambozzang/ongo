package com.ongo.api.subscription

import com.ongo.api.config.CurrentUser
import com.ongo.application.subscription.dto.*
import com.ongo.application.subscription.SubscriptionUseCase
import com.ongo.common.ResData
import com.ongo.common.exception.RateLimitExceededException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Tag(name = "구독", description = "구독 현황 조회, 플랜 변경, 취소")
@RestController
@RequestMapping("/api/v1/subscriptions")
class SubscriptionController(
    private val subscriptionUseCase: SubscriptionUseCase
) {

    companion object {
        private val rateLimits = ConcurrentHashMap<String, RateLimitEntry>()
        private const val MAX_PLAN_CHANGES_PER_HOUR = 5
        private const val RATE_LIMIT_WINDOW_MS = 3_600_000L // 1시간
    }

    private data class RateLimitEntry(val count: AtomicInteger = AtomicInteger(0), val windowStart: Long = System.currentTimeMillis())

    private fun checkRateLimit(userId: Long, action: String) {
        val key = "$action:$userId"
        val now = System.currentTimeMillis()
        val entry = rateLimits.compute(key) { _, existing ->
            if (existing == null || now - existing.windowStart > RATE_LIMIT_WINDOW_MS) {
                RateLimitEntry(AtomicInteger(1), now)
            } else {
                existing.count.incrementAndGet()
                existing
            }
        }!!
        if (entry.count.get() > MAX_PLAN_CHANGES_PER_HOUR) {
            throw RateLimitExceededException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    @Operation(
        summary = "현재 구독 조회",
        description = "사용자의 현재 구독 플랜 정보를 조회합니다. 플랜명, 시작일, 종료일, 사용량 등이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패")
    )
    @GetMapping("/current")
    fun getCurrentSubscription(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<SubscriptionResponse>> {
        return ResData.success(subscriptionUseCase.getCurrentSubscription(userId))
    }

    @Operation(
        summary = "플랜 변경",
        description = "구독 플랜을 변경합니다. 업그레이드 시 즉시 적용되며 일할 계산됩니다. 다운그레이드 시 현재 구독 기간 종료 후 적용됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "플랜 변경 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (동일 플랜 변경, 유효하지 않은 플랜 등)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @PostMapping("/change")
    fun changePlan(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: ChangePlanRequest
    ): ResponseEntity<ResData<ChangePlanResponse>> {
        checkRateLimit(userId, "changePlan")
        return ResData.success(subscriptionUseCase.changePlan(userId, request), "플랜이 변경되었습니다")
    }

    @Operation(
        summary = "구독 취소",
        description = "현재 구독을 취소합니다. 현재 구독 기간이 종료된 후 Free 플랜으로 자동 전환됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "구독 취소 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (이미 Free 플랜인 경우)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @PostMapping("/cancel")
    fun cancelSubscription(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<SubscriptionResponse>> {
        checkRateLimit(userId, "cancel")
        return ResData.success(subscriptionUseCase.cancelSubscription(userId), "구독이 취소되었습니다")
    }

    @Operation(
        summary = "플랜 비교",
        description = "전체 플랜(Free/Starter/Pro/Business) 비교 정보를 조회합니다. 각 플랜별 업로드 제한, 저장 용량, 연결 플랫폼 수, 무료 크레딧 등이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패")
    )
    @GetMapping("/plans")
    fun getPlans(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<PlanComparisonResponse>> {
        return ResData.success(subscriptionUseCase.getPlans(userId))
    }

    @Operation(summary = "사용량 조회", description = "현재 월간 업로드 수, 스토리지 사용량을 조회합니다.")
    @GetMapping("/usage")
    fun getUsage(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<UsageResponse>> {
        return ResData.success(subscriptionUseCase.getUsage(userId))
    }

    @Operation(summary = "트라이얼 시작", description = "7일 무료 체험을 시작합니다. 무료 플랜 사용자만 가능하며, 1회만 사용할 수 있습니다.")
    @PostMapping("/trial")
    fun startTrial(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: StartTrialRequest,
    ): ResponseEntity<ResData<SubscriptionResponse>> {
        return ResData.success(subscriptionUseCase.startTrial(userId, request.targetPlan), "7일 무료 체험이 시작되었습니다")
    }

    @Operation(summary = "구독 일시정지", description = "구독을 최대 30일간 일시정지합니다. 활성 구독만 가능합니다.")
    @PostMapping("/pause")
    fun pauseSubscription(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<SubscriptionResponse>> {
        checkRateLimit(userId, "pause")
        return ResData.success(subscriptionUseCase.pauseSubscription(userId), "구독이 일시정지되었습니다")
    }

    @Operation(summary = "구독 재개", description = "일시정지된 구독을 재개합니다.")
    @PostMapping("/resume")
    fun resumeSubscription(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<SubscriptionResponse>> {
        checkRateLimit(userId, "resume")
        return ResData.success(subscriptionUseCase.resumeSubscription(userId), "구독이 재개되었습니다")
    }
}
