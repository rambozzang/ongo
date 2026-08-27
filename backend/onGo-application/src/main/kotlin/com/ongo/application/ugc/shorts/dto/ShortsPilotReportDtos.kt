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
/**
 * 아직 파일럿에 등록되지 않은 실행 한 건. **후보 목록 전용**이다.
 *
 * 운영자가 등록 대상을 고르는 데 필요한 것은 "어느 실행인지"뿐이다. 고객 이메일·영상
 * URL·자막·후킹 문구는 담지 않는다 — 등록 화면은 콘텐츠 열람 화면이 아니고, 여기에
 * 실어 보내는 순간 운영자 화면이 곧 유출 경로가 된다.
 */
data class ShortsPilotCandidateRow(
    val runId: Long,
    val status: String,
    val createdAt: Instant,
    /** 원본 영상이 지워졌거나 제목이 비어 있으면 null. 빈 문자열로 바꾸지 않는다. */
    val sourceVideoTitle: String?,
)

/**
 * 후보 목록 한 페이지.
 *
 * [total] 은 **제외를 적용한 뒤의** 총수다. 전체 실행 수를 내려보내면 마지막 페이지가
 * 비어 보이고 운영자는 "왜 안 나오지"를 확인하느라 시간을 쓴다.
 */
data class ShortsPilotCandidatePage(
    val candidates: List<ShortsPilotCandidateRow>,
    val total: Long,
    val page: Int,
    val size: Int,
)

/**
 * 운영자가 손으로 적은 기록 한 건.
 *
 * 오입력을 되돌리려면 **어느 행이 잘못됐는지**를 먼저 봐야 한다. 지금까지 화면에는
 * 실행별 합계만 있어서, 3,000,000 이 한 번 잘못 들어간 것인지 300,000 이 열 번 쌓인
 * 것인지 구분할 수 없었다.
 *
 * `actorId`·`userId`·이메일은 담지 않는다. 취소 판단에 필요한 것은 무엇이 언제 얼마로
 * 적혔는가이지 누가 적었는가가 아니며, 그건 이벤트 행에만 남는다.
 */
data class ShortsPilotEntryRow(
    val entryId: Long,
    /** `OPERATOR_REVENUE_LOGGED` / `OPERATOR_EXTERNAL_COST_LOGGED` / `OPERATOR_TIME_LOGGED`. */
    val type: String,
    /** 금액 기록이면 원 단위, 시간 기록이면 null. */
    val amountKrw: Long?,
    /** 시간 기록이면 분 단위, 금액 기록이면 null. */
    val operatorMinutes: Int?,
    val recordedAt: Instant,
    /**
     * 취소된 기록이면 true. **행은 그대로 남는다** — 무엇을 잘못 적었었는지가 사라지면
     * 감사가 불가능하다. 보고서 합계에서만 빠진다.
     */
    val isReversed: Boolean,
)

/** 실행 하나의 수기 기록 목록. 자동 이벤트(재실행·렌더 실패)는 담지 않는다. */
data class ShortsPilotEntryListResponse(
    val entries: List<ShortsPilotEntryRow>,
)

/** 취소 결과. 이미 취소돼 있었는지를 호출자가 응답에 반영한다. */
data class ShortsPilotReversalResponse(
    val entryId: Long,
    /** 이미 취소돼 있었으면 true. 두 경우 모두 성공이다. */
    val alreadyReversed: Boolean,
)

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
 * 고객 ID·이메일·영상 URL·자막·후킹 문구는 담지 않는다. 운영자가 판단에 쓰는 것은 시각과
 * 횟수이며, 콘텐츠를 실어 나르면 보고서가 곧 유출 경로가 된다.
 *
 * **[isRepeatCustomer] 는 이 원칙의 예외가 아니다.** 식별자가 아니라 참·거짓 하나이며,
 * 이 값만으로는 어느 실행들이 같은 고객인지 되짚을 수 없다 — true 인 행이 여럿이어도
 * 그것이 한 고객인지 여러 고객인지 알 수 없다. 고객을 세는 일은 [ShortsPilotReportSummary]
 * 의 집계 수치가 맡는다.
 */
data class ShortsPilotRunRow(
    val runId: Long,
    /**
     * 이 실행의 고객이 파일럿에 **2건 이상** 등록돼 있으면 true.
     *
     * 표본 왜곡을 드러내려고 둔다. 10건이 고객 1명에게서 나온 표본과 10명에게서 나온
     * 표본은 단위경제 근거로서 값이 전혀 다른데, 행만 봐서는 구분되지 않았다.
     *
     * 실행 행이 사라진 등록은 세지 않는다 — 없는 실행으로 반복 여부를 판정할 수 없다.
     */
    val isRepeatCustomer: Boolean,
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
    /**
     * 등록된 실행을 가진 **고유 고객 수**.
     *
     * [enrolledRunCount] 와 이 값이 갈라지는 순간이 단위경제 판단이 바뀌는 지점이다.
     * 실행 10건이 고객 1명에게서 나왔다면 "건당 기여이익"은 한 사람의 사정일 뿐이다.
     *
     * 수만 낸다. 어느 고객인지는 담지 않으며, 실행 행이 사라진 등록은 세지 않는다.
     */
    val enrolledCustomerCount: Int,
    /** 등록된 실행이 2건 이상인 고유 고객 수. 0 이면 아직 아무도 다시 쓰지 않았다는 뜻이다. */
    val repeatCustomerCount: Int,
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
