import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/competitor', () => ({
  competitorApi: {
    list: vi.fn(),
    benchmark: vi.fn(),
    insight: vi.fn(),
    sync: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('@/composables/usePlanLimit', () => ({
  CREDIT_INSUFFICIENT: 'CREDIT_INSUFFICIENT',
  matchesCode: (e: any, code: string) => !!e && e?.response?.data?.errorCode === code,
}))

import { competitorApi } from '@/api/competitor'
import { useCompetitorStore } from '@/stores/competitor'

function creditError(code: string) {
  return { response: { data: { errorCode: code } } }
}

describe('competitor store sync/insight', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('syncCompetitors: 서버 수치(CompetitorSyncResponse)를 lastSync 에 보존한다', async () => {
    const store = useCompetitorStore()
    const syncResult = {
      requested: 2,
      synced: 2,
      unsupported: 1,
      failed: 0,
      results: [],
      competitors: [],
      totalCount: 2,
    }
    ;(competitorApi.sync as any).mockResolvedValue(syncResult)
    ;(competitorApi.list as any).mockResolvedValue({ competitors: [], totalCount: 0 })
    ;(competitorApi.benchmark as any).mockResolvedValue({
      myStats: { subscriberCount: 10, avgViews: 1, avgEngagement: 1, growthRate: 1 },
      competitors: [],
    })

    const result = await store.syncCompetitors()

    expect(competitorApi.sync).toHaveBeenCalledWith()
    expect(store.syncing).toBe(false)
    expect(store.syncError).toBeNull()
    expect(store.lastSync?.synced).toBe(2)
    expect(store.lastSync?.unsupported).toBe(1)
    expect(result?.synced).toBe(2)
  })

  it('syncCompetitors: 실패 시 syncError 에 실제 메시지를 노출하고 lastSync 는 비운다', async () => {
    const store = useCompetitorStore()
    ;(competitorApi.sync as any).mockRejectedValue(new Error('sync boom'))

    const result = await store.syncCompetitors()

    expect(store.syncing).toBe(false)
    expect(store.syncError).toBe('sync boom')
    expect(store.lastSync).toBeNull()
    expect(result).toBeNull()
  })

  it('fetchInsight: CREDIT_INSUFFICIENT 만 creditBlocked 로 판단하고 안내는 지운다', async () => {
    const store = useCompetitorStore()
    ;(competitorApi.insight as any).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    await store.fetchInsight()

    expect(store.creditBlocked).toBe(true)
    expect(store.insightError).toBeNull()
    expect(competitorApi.insight).toHaveBeenCalledTimes(1)
  })

  it('fetchInsight: 일반 오류(403/PLAN)는 creditBlocked 가 아니고 insightError 에 담는다', async () => {
    const store = useCompetitorStore()

    ;(competitorApi.insight as any).mockRejectedValue(creditError('PLAN_LIMIT_EXCEEDED'))
    await store.fetchInsight()
    expect(store.creditBlocked).toBe(false)
    expect(store.insightError).toBeTruthy()

    store.insightError = null
    ;(competitorApi.insight as any).mockRejectedValue({ response: { status: 403 } })
    await store.fetchInsight()
    expect(store.creditBlocked).toBe(false)
    expect(store.insightError).toBeTruthy()
  })

  it('fetchInsight: 성공 시 creditBlocked/insightError 를 초기화하고 aiInsight 를 채운다', async () => {
    const store = useCompetitorStore()
    const insight = {
      summary: '요약',
      strengths: ['s'],
      weaknesses: ['w'],
      opportunities: ['o'],
      recommendations: ['r'],
    }
    ;(competitorApi.insight as any).mockResolvedValue(insight)

    await store.fetchInsight()

    expect(store.creditBlocked).toBe(false)
    expect(store.insightError).toBeNull()
    expect(store.aiInsight).toEqual(insight)
  })
})
