import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { competitorApi } from '@/api/competitor'
import { useCompetitorStore } from './competitor'

vi.mock('@/api/competitor', () => ({
  competitorApi: {
    list: vi.fn(),
    add: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    lookup: vi.fn(),
    trends: vi.fn(),
    benchmark: vi.fn(),
    sync: vi.fn(),
    insight: vi.fn(),
  },
}))

const listResult = { competitors: [], totalCount: 0 }
const benchmarkResult = {
  myStats: {
    subscriberCount: null,
    totalViews: null,
    videoCount: 0,
    avgViews: null,
    engagementRate: null,
    growthRate: null,
  },
  competitors: [],
}

describe('competitor store real operation states', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(competitorApi.list).mockResolvedValue(listResult as never)
    vi.mocked(competitorApi.benchmark).mockResolvedValue(benchmarkResult as never)
  })

  it('keeps the server sync result and refreshes data after a successful sync', async () => {
    const result = {
      requested: 2,
      synced: 1,
      unsupported: 1,
      failed: 0,
      results: [],
      competitors: [],
      totalCount: 0,
    }
    vi.mocked(competitorApi.sync).mockResolvedValue(result as never)
    const store = useCompetitorStore()

    await expect(store.syncCompetitors()).resolves.toEqual(result)

    expect(store.lastSync).toEqual(result)
    expect(store.syncError).toBeNull()
    expect(competitorApi.list).toHaveBeenCalledOnce()
    expect(competitorApi.benchmark).toHaveBeenCalledOnce()
  })

  it('does not create a successful sync state when the server request fails', async () => {
    vi.mocked(competitorApi.sync).mockRejectedValue(new Error('동기화 서버 오류'))
    const store = useCompetitorStore()

    await expect(store.syncCompetitors()).resolves.toBeNull()

    expect(store.lastSync).toBeNull()
    expect(store.syncError).toBe('동기화 서버 오류')
    expect(competitorApi.list).not.toHaveBeenCalled()
  })

  it('does not report sync success when the post-sync refresh fails', async () => {
    const result = {
      requested: 1,
      synced: 1,
      unsupported: 0,
      failed: 0,
      results: [],
      competitors: [],
      totalCount: 1,
    }
    vi.mocked(competitorApi.sync).mockResolvedValue(result as never)
    vi.mocked(competitorApi.list).mockRejectedValue(new Error('최신 목록 조회 실패'))
    const store = useCompetitorStore()

    await expect(store.syncCompetitors()).resolves.toBeNull()

    expect(store.lastSync).toBeNull()
    expect(store.syncError).toBe('최신 목록 조회 실패')
  })

  it('exposes a credit CTA state only for the stable credit error code', async () => {
    vi.mocked(competitorApi.insight).mockRejectedValue({ response: { data: { error: 'CREDIT_INSUFFICIENT' } } })
    const store = useCompetitorStore()

    await store.fetchInsight()

    expect(store.creditBlocked).toBe(true)
    expect(store.insightError).toBeNull()
    expect(store.aiInsight).toBeNull()
  })

  it.each(['PLAN_LIMIT_EXCEEDED', 'FORBIDDEN'])('keeps non-credit error %s out of the credit CTA state', async (code) => {
    const error = Object.assign(new Error(code), { response: { data: { error: code } } })
    vi.mocked(competitorApi.insight).mockRejectedValue(error)
    const store = useCompetitorStore()

    await store.fetchInsight()

    expect(store.creditBlocked).toBe(false)
    expect(store.insightError).toContain(code)
  })

  it('keeps a successful insight response from the server', async () => {
    const result = { summary: '실제 요약', strengths: ['강점'], weaknesses: [], opportunities: [], recommendations: [] }
    vi.mocked(competitorApi.insight).mockResolvedValue(result as never)
    const store = useCompetitorStore()

    await store.fetchInsight()

    expect(store.aiInsight).toEqual(result)
    expect(store.creditBlocked).toBe(false)
    expect(store.insightError).toBeNull()
  })
})
