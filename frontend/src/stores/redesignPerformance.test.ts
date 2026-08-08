import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { analyticsApi } from '@/api/analytics'
import { useRedesignPerformanceStore } from './redesignPerformance'

vi.mock('@/api/analytics', () => ({
  analyticsApi: {
    dashboard: vi.fn(),
    trends: vi.fn(),
    topVideos: vi.fn(),
    avgViewDuration: vi.fn(),
    subscriberConversion: vi.fn(),
  },
}))

describe('redesign performance store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
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
    expect(store.hasError).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('retains confirmed panels and exposes a partial failure for retry', async () => {
    vi.mocked(analyticsApi.dashboard).mockResolvedValueOnce({ views: 100 } as never).mockRejectedValueOnce(new Error('offline'))
    vi.mocked(analyticsApi.trends).mockResolvedValue([{ date: '2026-08-01', views: 10 }] as never)
    vi.mocked(analyticsApi.topVideos).mockResolvedValue([{ id: 1 }] as never)
    vi.mocked(analyticsApi.avgViewDuration).mockResolvedValue({ averageSeconds: 42 } as never)
    vi.mocked(analyticsApi.subscriberConversion).mockResolvedValue({ rate: 3.2 } as never)
    const store = useRedesignPerformanceStore()

    await store.fetchPerformance('30d')
    await store.fetchPerformance('30d')

    expect(store.kpi).toEqual({ views: 100 })
    expect(store.trends).toHaveLength(1)
    expect(store.hasError).toBe(true)
    expect(store.loading).toBe(false)
  })
})
