import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { analyticsApi } from '@/api/analytics'
import { scheduleApi } from '@/api/schedule'
import { useRedesignPerformanceStore } from './redesignPerformance'

vi.mock('@/api/analytics', () => ({
  analyticsApi: {
    dashboard: vi.fn(),
    trends: vi.fn(),
    topVideos: vi.fn(),
    avgViewDuration: vi.fn(),
    subscriberConversion: vi.fn(),
    platformComparison: vi.fn(),
    tagPerformance: vi.fn(),
    getOptimalTimes: vi.fn(),
    videoCompare: vi.fn(),
  },
}))

vi.mock('@/api/schedule', () => ({
  scheduleApi: { list: vi.fn() },
}))

describe('redesign performance store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(analyticsApi.platformComparison).mockResolvedValue([])
    vi.mocked(analyticsApi.tagPerformance).mockResolvedValue([])
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({ slots: [] })
    vi.mocked(scheduleApi.list).mockResolvedValue([
      { status: 'PUBLISHED' },
      { status: 'FAILED' },
    ] as never)
  })

  it('loads every server-backed performance panel for the selected range', async () => {
    const kpi = { views: 100 }
    const trends = [{ date: '2026-08-01', views: 10 }]
    const topVideos = [{ id: 1, title: '영상' }]
    const duration = { averageSeconds: 42 }
    const conversion = { rate: 3.2 }
    vi.mocked(analyticsApi.dashboard).mockResolvedValue(kpi as never)
    vi.mocked(analyticsApi.trends).mockResolvedValue(trends as never)
    vi.mocked(analyticsApi.topVideos).mockResolvedValue(topVideos as never)
    vi.mocked(analyticsApi.avgViewDuration).mockResolvedValue(duration as never)
    vi.mocked(analyticsApi.subscriberConversion).mockResolvedValue(conversion as never)

    const store = useRedesignPerformanceStore()
    await store.fetchPerformance('7d')

    expect(analyticsApi.dashboard).toHaveBeenCalledWith('7d')
    expect(analyticsApi.topVideos).toHaveBeenCalledWith('7d', 10)
    expect(analyticsApi.avgViewDuration).toHaveBeenCalledWith(7)
    expect(analyticsApi.subscriberConversion).toHaveBeenCalledWith(7)
    expect(store.range).toBe('7d')
    expect(store.kpi).toEqual(kpi)
    expect(store.trends).toEqual(trends)
    expect(store.topVideos).toEqual(topVideos)
    expect(store.averageViewDuration).toEqual(duration)
    expect(store.subscriberConversion).toEqual(conversion)
    expect(store.publishedCount).toBe(1)
    expect(store.hasError).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('retains confirmed panels and exposes a partial failure for retry', async () => {
    vi.mocked(analyticsApi.dashboard)
      .mockResolvedValueOnce({ views: 100 } as never)
      .mockRejectedValueOnce(new Error('offline'))
    vi.mocked(analyticsApi.trends).mockResolvedValue([{ date: '2026-08-01', views: 10 }] as never)
    vi.mocked(analyticsApi.topVideos).mockResolvedValue([{ id: 1 }] as never)
    vi.mocked(analyticsApi.avgViewDuration).mockResolvedValue({ averageSeconds: 42 } as never)
    vi.mocked(analyticsApi.subscriberConversion).mockResolvedValue({ rate: 3.2 } as never)
    vi.mocked(scheduleApi.list).mockResolvedValue([{ status: 'PUBLISHED' }] as never)
    const store = useRedesignPerformanceStore()

    await store.fetchPerformance('30d')
    await store.fetchPerformance('30d')

    expect(store.kpi).toEqual({ views: 100 })
    expect(store.trends).toHaveLength(1)
    expect(store.hasError).toBe(true)
    expect(store.loading).toBe(false)
  })
})

describe('performance period integrity', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(analyticsApi.dashboard).mockResolvedValue({ totalViews: 100 } as never)
    vi.mocked(analyticsApi.trends).mockResolvedValue([])
    vi.mocked(analyticsApi.topVideos).mockResolvedValue([])
    vi.mocked(analyticsApi.avgViewDuration).mockResolvedValue({} as never)
    vi.mocked(analyticsApi.subscriberConversion).mockResolvedValue({} as never)
    vi.mocked(analyticsApi.platformComparison).mockResolvedValue([])
    vi.mocked(analyticsApi.tagPerformance).mockResolvedValue([])
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({ slots: [] })
    vi.mocked(scheduleApi.list).mockResolvedValue([])
  })

  it('clears old period data when a panel fails for a new period', async () => {
    const store = useRedesignPerformanceStore()
    await store.fetchPerformance('30d')
    vi.mocked(analyticsApi.dashboard).mockRejectedValueOnce(new Error('offline'))
    await store.fetchPerformance('7d')
    expect(store.kpi).toBeNull()
    expect(store.failedPanels).toEqual(['dashboard'])
    expect(store.range).toBe('7d')
  })

  it('ignores a slow response after the user changes the period', async () => {
    let resolveOld!: (value: never) => void
    vi.mocked(analyticsApi.dashboard).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve
        }),
    )
    const store = useRedesignPerformanceStore()
    const old = store.fetchPerformance('30d')
    await store.fetchPerformance('7d')
    resolveOld({ totalViews: 99999 } as never)
    await old
    expect(store.range).toBe('7d')
    expect(store.kpi?.totalViews).toBe(100)
    expect(store.loading).toBe(false)
  })

  it('uses 365 actual days for the annual period', async () => {
    await useRedesignPerformanceStore().fetchPerformance('1y')
    expect(analyticsApi.avgViewDuration).toHaveBeenCalledWith(365)
    expect(analyticsApi.getOptimalTimes).toHaveBeenCalledWith(undefined, 365)
  })
})
