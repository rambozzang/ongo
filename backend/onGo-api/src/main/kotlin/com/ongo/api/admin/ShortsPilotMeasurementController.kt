package com.ongo.api.admin

import com.ongo.application.ugc.shorts.ShortsPilotFinanceUseCase
import com.ongo.application.ugc.shorts.ShortsPilotOperatorTimeUseCase
import com.ongo.application.ugc.shorts.ShortsPilotReportUseCase
import com.ongo.application.ugc.shorts.dto.ShortsPilotReport
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 파일럿 측정 보고와 사람 투입 시간 입력. **운영자 전용**이다.
 *
 * ## 왜 admin 하위인가
 *
 * `SecurityConfig` 에 admin 하위 전체를 `hasRole("ADMIN")` 으로 막는 매처가 이미 있다.
 * 여기에 두면 URL 매처와 `@PreAuthorize` 가 모두 걸려, 어느 한쪽을 실수로 지워도 일반
 * 사용자에게 열리지 않는다.
 *
 * ## 무엇을 주고받지 않는가
 *
 * 요청과 응답 어디에도 고객 이메일·이름·영상·자막·결제수단·거래 식별자가 없다. 보고는
 * 실행 ID 와 시각·횟수·수기 입력 금액만 담고, 입력 본문은 **분 하나 또는 금액 하나**뿐이다
 * (투입 시간은 `minutes`, 매출·외부원가는 `amountKrw`).
 */
@Tag(name = "관리자", description = "쇼츠 유료 파일럿 측정 보고")
@RestController
@RequestMapping("/api/v1/admin/shorts-pilot")
@PreAuthorize("hasRole('ADMIN')")
class ShortsPilotMeasurementController(
    private val reportUseCase: ShortsPilotReportUseCase,
    private val operatorTimeUseCase: ShortsPilotOperatorTimeUseCase,
    private val financeUseCase: ShortsPilotFinanceUseCase,
) {

    /**
     * @param minutes 분 단위 투입 시간. 상한 1440(24시간)은 오타 방어이고,
     *   하한 1 은 0 분 기록을 막는다 — 0 은 "안 썼다"와 "깜빡했다"를 구분하지 못한다.
     */
    data class OperatorTimeRequest(
        @field:Min(1)
        @field:Max(1440)
        val minutes: Int,
    )

    @Operation(
        summary = "파일럿 측정 보고 조회",
        description = "등록된 실행의 리드타임·재실행·렌더 실패와, 운영자가 직접 입력한 투입 시간·매출·" +
            "외부 인프라 원가를 돌려준다. 매출과 원가는 결제·계측 시스템과 대조되지 않은 수기 입력값이며, " +
            "기여이익은 매출에서 외부원가만 뺀 값으로 인건비를 포함하지 않는다. " +
            "전환율·재구매·결제 자동 귀속·인프라 실측 원가는 여전히 계산하지 않으며 limitations 코드로만 표시한다. " +
            "기록이 없는 값은 0 이 아니라 null 이다.",
    )
    @GetMapping("/report")
    fun report(): ResponseEntity<ResData<ShortsPilotReport>> =
        ResData.success(reportUseCase.report())

    /**
     * @param amountKrw 원 단위. 하한 1 은 0 원 기록을 막는다 — 0 은 "무상 제공"과 "깜빡했다"를
     *   구분하지 못한다. 상한 1억은 자릿수 오타 방어이며, 원장이 append-only 라 잘못 들어간
     *   값은 지울 수 없다.
     */
    data class AmountRequest(
        @field:Min(1)
        @field:Max(100_000_000)
        val amountKrw: Long,
    )

    @Operation(
        summary = "운영자가 확인한 매출 기록",
        description = "운영자가 청구서를 보고 직접 입력한다. PortOne 결제 내역과 연동된 값이 아니다. " +
            "한 실행에 여러 번 쌓이며 리포트에서 합산한다.",
    )
    @PostMapping("/runs/{runId}/revenue")
    fun logRevenue(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable runId: Long,
        @Valid @RequestBody request: AmountRequest,
    ): ResponseEntity<ResData<Unit>> {
        financeUseCase.logRevenue(actorUserId = userId, runId = runId, amountKrw = request.amountKrw)
        return ResData.success(Unit)
    }

    @Operation(
        summary = "운영자가 확인한 외부 인프라 원가 기록",
        description = "운영자가 AI·저장소 청구서를 보고 직접 입력한다. 사용량 계측값이 아니며 " +
            "인건비는 포함하지 않는다(투입 시간은 별도 기록).",
    )
    @PostMapping("/runs/{runId}/external-cost")
    fun logExternalCost(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable runId: Long,
        @Valid @RequestBody request: AmountRequest,
    ): ResponseEntity<ResData<Unit>> {
        financeUseCase.logExternalCost(actorUserId = userId, runId = runId, amountKrw = request.amountKrw)
        return ResData.success(Unit)
    }

    @Operation(
        summary = "운영자 투입 시간 기록",
        description = "파일럿에 등록된 실행에만 기록한다. 한 실행에 여러 번 쌓이며 보고에서 합산한다.",
    )
    @PostMapping("/runs/{runId}/operator-time")
    fun logOperatorTime(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable runId: Long,
        @Valid @RequestBody request: OperatorTimeRequest,
    ): ResponseEntity<ResData<Unit>> {
        operatorTimeUseCase.log(actorUserId = userId, runId = runId, minutes = request.minutes)
        return ResData.success(Unit)
    }
}
