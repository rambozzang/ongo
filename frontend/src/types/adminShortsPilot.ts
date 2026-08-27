export type ShortsPilotReportState = 'NO_DATA' | 'OK'

export type ShortsPilotLimitation =
  | 'PAYMENT_NOT_ATTRIBUTED'
  | 'REPEAT_PURCHASE_NOT_MEASURED'
  | 'ACTUAL_INFRASTRUCTURE_COST_NOT_AVAILABLE'
  | 'REVENUE_AND_COST_ARE_OPERATOR_REPORTED'
  | 'LABOR_COST_NOT_INCLUDED_IN_CONTRIBUTION'

export interface ShortsPilotLeadTimeSummary {
  observedRunCount: number
  minMs: number
  maxMs: number
  averageMs: number
}

export interface ShortsPilotReportSummary {
  enrolledRunCount: number
  /**
   * 등록된 실행을 가진 고유 고객 수. 실행 수와 이 값이 갈라지는 순간이
   * 단위경제 판단이 바뀌는 지점이다.
   */
  enrolledCustomerCount: number
  /** 등록 실행이 2건 이상인 고유 고객 수. */
  repeatCustomerCount: number
  startedRunCount: number
  deliveredRunCount: number
  totalStageReruns: number
  totalRenderAttemptFailures: number
  totalOperatorMinutes: number | null
  totalOperatorReportedRevenueKrw: number | null
  totalOperatorReportedExternalCostKrw: number | null
  totalContributionExcludingExternalCostKrw: number | null
  contributionObservedRunCount: number
  leadTime: ShortsPilotLeadTimeSummary | null
}

export interface ShortsPilotRunRow {
  runId: number
  /**
   * 이 실행의 고객이 파일럿에 2건 이상 등록돼 있으면 true.
   *
   * 식별자가 아니다 — true 인 행이 여럿이어도 그것이 한 고객인지 여러 고객인지는
   * 알 수 없다. 고객을 세는 일은 summary 가 맡는다.
   */
  isRepeatCustomer: boolean
  createdAt: string
  startedAt: string | null
  deliveredAt: string | null
  leadTimeMs: number | null
  stageRerunCount: number
  renderAttemptFailureCount: number
  operatorMinutes: number | null
  operatorReportedRevenueKrw: number | null
  operatorReportedExternalCostKrw: number | null
  contributionExcludingExternalCostKrw: number | null
  contributionPerOperatorHourKrw: number | null
}

export interface ShortsPilotReport {
  state: ShortsPilotReportState
  summary: ShortsPilotReportSummary | null
  runs: ShortsPilotRunRow[]
  limitations: ShortsPilotLimitation[]
}

/**
 * 운영자가 손으로 적은 기록 한 건.
 *
 * 누가 적었는지(actorId)는 담기지 않는다. 취소 판단에 필요한 것은 무엇이 언제 얼마로
 * 적혔는가이지 누가 적었는가가 아니다.
 */
export interface ShortsPilotEntry {
  entryId: number
  /** `OPERATOR_REVENUE_LOGGED` | `OPERATOR_EXTERNAL_COST_LOGGED` | `OPERATOR_TIME_LOGGED` */
  type: string
  amountKrw: number | null
  operatorMinutes: number | null
  recordedAt: string
  /** 취소된 기록. 행은 그대로 남고 보고서 합계에서만 빠진다. */
  isReversed: boolean
}

export interface ShortsPilotEntryListResponse {
  entries: ShortsPilotEntry[]
}

export interface ShortsPilotReversalResponse {
  entryId: number
  /** 이미 취소돼 있었으면 true. 두 경우 모두 성공이다. */
  alreadyReversed: boolean
}

export interface ShortsPilotEnrollmentResponse {
  runId: number
  alreadyEnrolled: boolean
}

/**
 * 아직 코호트에 없는 실행 한 건.
 *
 * 서버가 등록된 실행을 이미 걸러 보내므로 화면에서 다시 거를 필요가 없다.
 * 고객 정보·영상 URL·자막은 이 응답에 없다.
 */
export interface ShortsPilotCandidate {
  runId: number
  status: string
  createdAt: string
  /** 영상이 지워졌거나 제목이 비어 있으면 null. 빈 문자열로 오지 않는다. */
  sourceVideoTitle: string | null
}

export interface ShortsPilotCandidatePage {
  candidates: ShortsPilotCandidate[]
  /** 제외를 적용한 뒤의 총수. 전체 실행 수가 아니다. */
  total: number
  page: number
  size: number
}
