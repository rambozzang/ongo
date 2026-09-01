package com.ongo.api.admin

import com.ongo.application.admin.AdminDeadLetterRequeueResult
import com.ongo.application.admin.AdminDeadLetterWebhookItem
import com.ongo.application.admin.AdminWebhookDeadLetterUseCase
import com.ongo.common.ResData
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
 * 재시도를 모두 소진한 웹훅(`DEAD_LETTER`) 운영 화면.
 *
 * [AdminController] 와 같은 `/api/v1/admin` 경로에 같은 `hasRole('ADMIN')` 보호를 건다.
 * `SecurityConfig` 의 URL 매처와 여기 `@PreAuthorize` 가 이중으로 걸려, 어느 한쪽을 실수로
 * 지워도 일반 사용자에게 열리지 않는다.
 *
 * ## 응답에 담지 않는 것
 *
 * 원문 본문(`payload`)과 서명 헤더는 **절대 내보내지 않는다.** 결제 식별자·고객 정보가 그대로
 * 들어 있고, 화면·로그·스크린샷을 통해 퍼지면 되돌릴 수 없다. 멱등 키도 가운데를 가려
 * PG 대시보드와 대조할 만큼만 남긴다. 재큐잉 대상은 `event_id` 가 아니라 대리 키로 지정한다.
 *
 * ## 요청 본문이 없는 이유
 *
 * `requeue` 는 대상만 받는다. 상태를 본문으로 받게 하면 "임의 상태로 바꾸기" 가 가능해지고,
 * 그러면 이 화면으로 결제 이력을 신뢰할 수 있다고 말할 수 없다. 전이는 서버가 정한
 * `DEAD_LETTER → FAILED` 하나뿐이다.
 */
@Tag(
    name = "관리자 - 웹훅 복구",
    description = "재시도를 모두 소진한 결제 웹훅 조회 및 명시적 재큐잉",
)
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminWebhookDeadLetterController(
    private val useCase: AdminWebhookDeadLetterUseCase,
) {

    @Operation(
        summary = "DEAD_LETTER 웹훅 목록",
        description = "재시도를 모두 소진해 아무도 다시 처리하지 않는 웹훅을 최근 순으로 조회합니다. " +
            "원문 본문과 서명은 포함하지 않으며 멱등 키는 마스킹됩니다.",
    )
    @GetMapping("/webhooks/dead-letters")
    fun list(
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<ResData<List<AdminDeadLetterWebhookItem>>> =
        ResData.success(useCase.list(limit))

    @Operation(
        summary = "DEAD_LETTER 웹훅 재큐잉",
        description = "한 건을 재시도 대기열로 되돌립니다(DEAD_LETTER → FAILED). 다시 한 번만 시도하며, " +
            "이미 처리됐거나 DEAD_LETTER 가 아니거나 우리가 재처리할 수 없는 이벤트는 거부합니다. " +
            "여기서 결제를 처리하지 않습니다 — 실제 반영은 재시도 스케줄러가 PG 재조회로 판단합니다.",
    )
    @PostMapping("/webhooks/dead-letters/{id}/requeue")
    fun requeue(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<AdminDeadLetterRequeueResult>> =
        ResData.success(useCase.requeue(id), "웹훅을 재시도 대기열에 넣었습니다")
}
