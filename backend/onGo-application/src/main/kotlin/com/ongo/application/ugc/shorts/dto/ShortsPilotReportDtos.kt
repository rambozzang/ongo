package com.ongo.application.ugc.shorts.dto

import java.time.Instant

/**
 * 파일럿 측정 보고. **수집한 사실만** 담는다.
 *
 * ## 무엇이 들어 있나
 *
 * 시스템이 자동으로 남긴 것(리드타임·재실행·렌더 실패)과, **운영자가 손으로 적은 것**
 * (투입 시간·매출·외부 인프라 원가)이다. 후자는 청구·계측 시스템과 대조되지 않았고,
 * 그 사실이 필드 이름(`operatorReported…`)과 [limitations] 에 항상 드러난다.
 *
 * ## 계산 범위
 *
 * 기여이익은 **매출 − 외부원가**이며 인건비를 빼기 전 값이다. 사람 투입은 분 단위로만
 * 따로 남는다 — 시급을 가정해 빼면 그 가정이 숫자에 숨는다.
 *
 * ## 여전히 계산하지 않는 것
 *
 * 전환율, 재구매, 결제 자동 귀속, 인프라 실측 원가. 운영자가 매출을 적었다고 해서 그것이
 * 결제 시스템과 맞춰졌다는 뜻은 아니다. 이 값들은 0 이나 추정치로 채우지 않고
 * [limitations] 에 코드로만 남기며 숫자를 붙이지 않는다.
 *
 * 기록이 없는 값은 전부 null 이다. 0 으로 내리면 "무상 제공"이나 "원가 0" 으로 읽힌다.
 */
data class ShortsPilotReport(
    val state: ShortsPilotReportState,
    /** [ShortsPilotReportState.NO_DATA] 이면 null. 0 으로 채우지 않는다. */
    val summary: ShortsPilotReportSummary?,
    val runs: List<ShortsPilotRunRow>,
    val limitations: List<ShortsPilotLimitation>,
)

enum class ShortsPilotReportState {
    /** 파일럿에 등록된 실행이 하나도 없다. 집계는 존재하지 않는다. */
    NO_DATA,
    OK,
}

/**
 * 실행 한 건.
 *
 * 고객 ID·영상·자막·후킹 문구는 담지 않는다. 운영자가 판단에 쓰는 것은 시각과 횟수이며,
 * 콘텐츠를 실어 나르면 보고서가 곧 유출 경로가 된다.
 */
data class ShortsPilotRunRow(
    val runId: Long,
    val createdAt: Instant,
    /** 아직 시작 전이면 null. */
    val startedAt: Instant?,
    /** 아직 첫 납품 전이면 null. */
    val deliveredAt: Instant?,
    /** 시작·납품 둘 다 있을 때만 값이 있다. 한쪽이 없으면 null 이며 0 이 아니다. */
    val leadTimeMs: Long?,
    val stageRerunCount: Int,
    val renderAttemptFailureCount: Int,
    /** 운영자가 입력한 시간의 합. 입력이 없으면 null 이다 — 0 분과 미입력은 다르다. */
    val operatorMinutes: Int?,
    /**
     * 운영자가 **확인해 입력한** 매출(원). 결제 시스템 연동값이 아니다.
     * 기록이 없으면 null 이며 0 원이 아니다.
     */
    val operatorReportedRevenueKrw: Long?,
    /**
     * 운영자가 **확인해 입력한** 외부 인프라 원가(원). 사용량 계측값이 아니다.
     * 인건비는 포함하지 않는다 — 그쪽은 [operatorMinutes] 로 따로 본다.
     */
    val operatorReportedExternalCostKrw: Long?,
    /**
     * 매출 − 외부원가. **인건비를 빼기 전** 값이다.
     *
     * 둘 중 하나라도 미기록이면 null 이다. 한쪽만으로 계산하면 매출만 적힌 실행이
     * 이익률 100% 로 보이고, 그게 가장 위험한 오독이다.
     */
    val contributionExcludingExternalCostKrw: Long?,
    /**
     * 기여이익 ÷ 운영자 투입 시간(시간).
     *
     * 기여이익이 null 이거나 투입 시간 기록이 없으면 null 이다. 사람 시간이 얼마나
     * 들었는지 모르는 채 시간당 수치를 내면 그건 계산이 아니라 창작이다.
     */
    val contributionPerOperatorHourKrw: Long?,
)

data class ShortsPilotReportSummary(
    val enrolledRunCount: Int,
    val startedRunCount: Int,
    val deliveredRunCount: Int,
    val totalStageReruns: Int,
    val totalRenderAttemptFailures: Int,
    /** 입력이 하나도 없으면 null. */
    val totalOperatorMinutes: Int?,
    /** 운영자가 확인해 입력한 매출의 합. 기록한 실행이 하나도 없으면 null. */
    val totalOperatorReportedRevenueKrw: Long?,
    /** 운영자가 확인해 입력한 외부원가의 합. 기록한 실행이 하나도 없으면 null. */
    val totalOperatorReportedExternalCostKrw: Long?,
    /**
     * 매출·외부원가가 **둘 다** 기록된 실행들만의 기여이익 합.
     *
     * 한쪽만 적힌 실행을 섞으면 모수가 달라져 합계가 의미를 잃는다. 그래서 몇 건으로
     * 계산했는지를 [contributionObservedRunCount] 로 함께 낸다.
     */
    val totalContributionExcludingExternalCostKrw: Long?,
    /** 위 합계가 몇 개 실행에서 나왔는지. 전체 등록 수와 다를 수 있다. */
    val contributionObservedRunCount: Int,
    /** 리드타임을 관측한 실행이 하나도 없으면 null. */
    val leadTime: ShortsPilotLeadTimeSummary?,
)

/** 시작·납품이 모두 기록된 실행만으로 계산한다. */
data class ShortsPilotLeadTimeSummary(
    val observedRunCount: Int,
    val minMs: Long,
    val maxMs: Long,
    /** 관측된 실행들의 산술 평균. 미관측 실행을 0 으로 세지 않는다. */
    val averageMs: Long,
)

/**
 * 이 보고가 **답하지 못하는** 것들.
 *
 * 코드만 노출하고 숫자를 붙이지 않는다. 여기에 0 을 넣는 순간 "결제 귀속 0건"처럼
 * 읽혀서, 측정하지 않았다는 사실이 측정 결과로 둔갑한다.
 */
enum class ShortsPilotLimitation {
    /** payments 와 파이프라인 실행 사이에 연결이 없다. 어떤 실행이 유료인지 DB 로는 모른다. */
    PAYMENT_NOT_ATTRIBUTED,

    /** 재구매를 식별할 신호가 없다. 구독 갱신과 쇼츠 재의뢰를 구분할 수 없다. */
    REPEAT_PURCHASE_NOT_MEASURED,

    /** 렌더 CPU·저장소 전송량 실측이 없다. 크레딧은 크레딧이지 원가가 아니다. */
    ACTUAL_INFRASTRUCTURE_COST_NOT_AVAILABLE,

    /**
     * 매출·외부원가는 **운영자가 손으로 적은 값**이다.
     *
     * PortOne 결제 내역·AI 제공자 청구서·R2 사용량과 대조되지 않았다. 이 코드가 응답에
     * 항상 실려 있어야, 리포트를 보는 사람이 숫자의 출처를 오해하지 않는다.
     */
    REVENUE_AND_COST_ARE_OPERATOR_REPORTED,

    /**
     * 기여이익에서 **인건비를 빼지 않았다.**
     *
     * 사람 투입은 분 단위로만 따로 남는다. 시급을 가정해 빼면 그 가정이 숫자에 숨고,
     * 나중에 가정을 바꿔도 과거 수치는 그대로 남는다.
     */
    LABOR_COST_NOT_INCLUDED_IN_CONTRIBUTION,
}
