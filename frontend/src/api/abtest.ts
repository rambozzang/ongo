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
  views: number
  clicks: number
  engagementRate: number
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
  averageImprovement: number
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
      impressions: variant.views,
      clicks: variant.clicks,
      ctr: variant.views > 0 ? (variant.clicks / variant.views) * 100 : 0,
      views: variant.views,
      avgWatchTime: undefined,
      isWinner: test.winnerVariantId === variant.id,
    })),
    startedAt: test.startedAt ?? undefined,
    endedAt: test.endedAt ?? undefined,
    durationHours: test.durationHours ?? undefined,
    totalImpressions: test.variants.reduce((sum, variant) => sum + variant.views, 0),
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
