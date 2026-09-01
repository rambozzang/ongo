package com.ongo.api.admin

import com.ongo.application.admin.AdminSubscriptionReviewUseCase
import com.ongo.application.admin.dto.AdminRenewalReviewItem
import com.ongo.application.admin.dto.AdminRenewalReviewRecheckResult
import com.ongo.common.ResData
import com.ongo.common.config.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 자동 갱신 확인 대상(`NEEDS_REVIEW`) 운영 화면.
 *
 * [AdminController] 와 같은 `/api/v1/admin` 경로에 같은 `hasRole('ADMIN')` 보호를 건다.
 * 별도 클래스로 둔 것은 결제 복구가 사용자 관리와 수명 주기가 다르기 때문이지 보호가
 * 다르기 때문이 아니다.
 *
 * ## 요청 본문이 없는 이유
 *
 * `recheck` 는 대상만 받는다. 확정 결과를 본문으로 받을 수 있게 하는 순간 "확인했다고 치고
 * 성공 처리" 가 가능해지고, 그러면 이 원장으로 이중 청구를 막았다고 말할 수 없다.
 * 결과는 PortOne 재조회가 정한다.
 */
@Tag(name = "관리자 - 구독 갱신 확인", description = "사람이 확인해야 하는 자동 갱신 주기 조회 및 PG 재조회")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminSubscriptionReviewController(
    private val useCase: AdminSubscriptionReviewUseCase,
) {

    @Operation(
        summary = "갱신 확인 대상 목록",
        description = "자동으로 확정할 수 없어 사람이 확인해야 하는 갱신 주기를 오래된 순으로 조회합니다.",
    )
    @GetMapping("/subscriptions/renewal-reviews")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<PageResponse<AdminRenewalReviewItem>>> =
        ResData.success(useCase.list(page, size))

    @Operation(
        summary = "갱신 확인 대상 PG 재조회",
        description = "PortOne에 결제 상태를 다시 물어 확정합니다. 새로 청구하지 않으며, " +
            "PG가 승인했고 금액·통화가 내부 결제와 정확히 일치할 때만 성공으로, " +
            "결제가 없거나 실패·취소일 때만 실패로 확정합니다. 그 밖에는 상태를 바꾸지 않습니다.",
    )
    @PostMapping("/subscriptions/renewal-reviews/{attemptId}/recheck")
    fun recheck(
        @PathVariable attemptId: Long,
    ): ResponseEntity<ResData<AdminRenewalReviewRecheckResult>> =
        ResData.success(useCase.recheck(attemptId))
}
