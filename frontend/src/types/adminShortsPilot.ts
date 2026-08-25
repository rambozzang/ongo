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

export interface ShortsPilotEnrollmentResponse {
  runId: number
  alreadyEnrolled: boolean
}
