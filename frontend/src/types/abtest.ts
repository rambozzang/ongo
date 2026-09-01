export type AbTestStatus = 'DRAFT' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED'
export type AbTestType = 'THUMBNAIL' | 'TITLE' | 'DESCRIPTION' | 'TAGS'
export type VariantLabel = 'A' | 'B' | 'C' | 'D'

export interface AbTestVariant {
  id: string
  label: VariantLabel
  value: string
  thumbnailUrl?: string
  /**
   * 노출 수. **`null` 은 측정되지 않았다는 뜻**이며 0 이 아니다.
   *
   * 노출이 없으면 클릭·CTR 도 존재할 수 없다. 세 값은 함께 `null` 이 된다.
   */
  impressions: number | null
  clicks: number | null
  ctr: number | null
  views: number | null
  avgWatchTime?: number
  /** 세 지표가 `null` 인 이유. 서버가 내려주는 문장. */
  metricsUnavailableReason?: string
  isWinner: boolean
}

export interface AbTest {
  id: number
  videoId: number
  videoTitle: string
  type: AbTestType
  status: AbTestStatus
  variants: AbTestVariant[]
  startedAt?: string
  endedAt?: string
  durationHours?: number
  /** 측정된 변형들의 노출 합. **측정된 변형이 없으면 `null`** — 0 이 아니다. */
  totalImpressions: number | null
  confidenceLevel?: number
  winnerId?: string
  createdAt: string
}

export interface CreateAbTestRequest {
  videoId: number
  type: AbTestType
  variants: {
    label: VariantLabel
    value: string
    thumbnailFile?: File
  }[]
  durationHours: number
}

export interface CreateAbTestResponse {
  test: AbTest
  creditsUsed?: number
  creditsRemaining?: number
}

export interface AbTestSummary {
  totalTests: number
  activeTests: number
  completedTests: number
  /**
   * 평균 CTR 개선율(%). **`null` 은 측정된 실험이 없다는 뜻**이며 0 이 아니다.
   *
   * 노출·클릭이 수집되지 않은 실험을 0% 로 세면 화면에 "+0.0%" 라는 성과가 생긴다.
   */
  avgCtrImprovement: number | null
  bestPerformingType?: AbTestType
}

export interface VideoForAbTest {
  id: number
  title: string
  thumbnailUrl: string
  currentCtr?: number
  views?: number
  publishedAt?: string
  hasActiveTest: boolean
}
