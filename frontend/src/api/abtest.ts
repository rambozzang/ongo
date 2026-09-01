import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AbTest,
  AbTestSummary,
  VideoForAbTest,
  CreateAbTestRequest as UiCreateAbTestRequest,
  CreateAbTestResponse,
} from '@/types/abtest'

interface BackendVariant {
  id: number
  variantName: string
  title?: string | null
  description?: string | null
  thumbnailUrl?: string | null
  views: number | null
  clicks: number | null
  engagementRate: number | null
  metricsUnavailableReason?: string | null
}

interface BackendTest {
  id: number
  videoId?: number | null
  testName: string
  status: AbTest['status']
  metricType: string
  durationHours?: number | null
  winnerVariantId?: number | null
  startedAt?: string | null
  endedAt?: string | null
  createdAt?: string | null
  variants: BackendVariant[]
}

interface BackendTestList {
  tests: BackendTest[]
  totalCount: number
}

interface BackendVideo {
  id: number
  title: string
  thumbnailUrl?: string | null
  duration?: number | null
  currentCtr?: number | null
  views?: number | null
  publishedAt?: string | null
  hasActiveTest?: boolean
}

interface BackendSummary {
  totalTests: number
  activeTests: number
  completedTests: number
  /** 측정된 실험이 하나도 없으면 `null`. 0 은 "개선이 없었다" 는 관측 결과다. */
  averageImprovement: number | null
}

function variantLabel(name: string, index: number): 'A' | 'B' | 'C' | 'D' {
  const normalized = name.trim().toUpperCase()
  return (['A', 'B', 'C', 'D'] as const).includes(normalized as 'A' | 'B' | 'C' | 'D')
    ? normalized as 'A' | 'B' | 'C' | 'D'
    : (['A', 'B', 'C', 'D'] as const)[index] ?? 'A'
}

function mapTest(test: BackendTest): AbTest {
  return {
    id: test.id,
    videoId: test.videoId ?? 0,
    videoTitle: test.testName,
    type: (['THUMBNAIL', 'TITLE', 'DESCRIPTION', 'TAGS'] as const).includes(test.metricType as never)
      ? test.metricType as AbTest['type']
      : 'THUMBNAIL',
    status: test.status,
    variants: test.variants.map((variant, index) => ({
      id: String(variant.id),
      label: variantLabel(variant.variantName, index),
      value: variant.title ?? variant.description ?? '',
      thumbnailUrl: variant.thumbnailUrl ?? undefined,
      /*
       * 서버가 `null` 로 준 것은 **측정하지 않았다** 는 뜻이다. 0 으로 바꾸면 결과 차트가
       * "0.0% · 노출 0 · 클릭 0" 을 정상 측정값처럼 그린다.
       *
       * 예전에는 `ctr: variant.views > 0 ? ... : 0` 이라 미측정 변형이 전부 CTR 0% 로
       * 내려갔다. 노출을 수집하는 경로가 없으므로 **모든 변형이 항상 0.0%** 였다.
       */
      impressions: variant.views,
      clicks: variant.clicks,
      ctr: variant.views != null && variant.clicks != null && variant.views > 0
        ? (variant.clicks / variant.views) * 100
        : null,
      views: variant.views,
      metricsUnavailableReason: variant.metricsUnavailableReason ?? undefined,
      avgWatchTime: undefined,
      isWinner: test.winnerVariantId === variant.id,
    })),
    startedAt: test.startedAt ?? undefined,
    endedAt: test.endedAt ?? undefined,
    durationHours: test.durationHours ?? undefined,
    // 측정된 변형만 더한다. 하나도 없으면 합계는 null 이다 — "총 노출 0" 은 관측 주장이다.
    totalImpressions: test.variants.some(v => v.views != null)
      ? test.variants.reduce((sum, variant) => sum + (variant.views ?? 0), 0)
      : null,
    confidenceLevel: undefined,
    winnerId: test.winnerVariantId == null ? undefined : String(test.winnerVariantId),
    createdAt: test.createdAt ?? new Date().toISOString(),
  }
}

export const abTestApi = {
  getVideos() {
    return apiClient
      .get<ResData<BackendVideo[]>>('/ab-tests/videos')
      .then(unwrapResponse)
      .then((videos): VideoForAbTest[] => videos.map(video => ({
        id: video.id,
        title: video.title,
        thumbnailUrl: video.thumbnailUrl ?? '',
        currentCtr: video.currentCtr ?? undefined,
        views: video.views ?? undefined,
        publishedAt: video.publishedAt ?? undefined,
        hasActiveTest: video.hasActiveTest ?? false,
      })))
  },

  getTests() {
    return apiClient
      .get<ResData<BackendTestList>>('/ab-tests')
      .then(unwrapResponse)
      .then(result => result.tests.map(mapTest))
  },

  getTest(testId: number) {
    return apiClient
      .get<ResData<BackendTest>>(`/ab-tests/${testId}`)
      .then(unwrapResponse)
      .then(mapTest)
  },

  createTest(request: UiCreateAbTestRequest) {
    return apiClient
      .post<ResData<BackendTest>>('/ab-tests', {
        videoId: request.videoId,
        testName: `${request.type} A/B 테스트`,
        metricType: request.type,
        type: request.type,
        durationHours: request.durationHours,
        variants: request.variants.map(variant => ({
          variantName: variant.label,
          title: variant.value,
        })),
      })
      .then(unwrapResponse)
      .then(test => ({ test: mapTest(test) } satisfies CreateAbTestResponse))
  },

  startTest(testId: number) {
    return apiClient
      .post<ResData<BackendTest>>(`/ab-tests/${testId}/start`)
      .then(unwrapResponse)
      .then(mapTest)
  },

  pauseTest(testId: number) {
    return apiClient
      .post<ResData<BackendTest>>(`/ab-tests/${testId}/pause`)
      .then(unwrapResponse)
      .then(mapTest)
  },

  completeTest(testId: number) {
    return apiClient
      .post<ResData<BackendTest>>(`/ab-tests/${testId}/complete`)
      .then(unwrapResponse)
      .then(mapTest)
  },

  applyWinner(testId: number) {
    return apiClient
      .post<ResData<void>>(`/ab-tests/${testId}/apply-winner`)
      .then(unwrapResponse)
  },

  deleteTest(testId: number) {
    return apiClient
      .delete<ResData<void>>(`/ab-tests/${testId}`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<BackendSummary>>('/ab-tests/summary')
      .then(unwrapResponse)
      .then((summary): AbTestSummary => ({
        totalTests: summary.totalTests,
        activeTests: summary.activeTests,
        completedTests: summary.completedTests,
        avgCtrImprovement: summary.averageImprovement,
        bestPerformingType: undefined,
      }))
  },
}
