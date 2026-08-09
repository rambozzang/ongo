import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { scheduleApi } from '@/api/schedule'
import { scheduleOptimizerApi } from '@/api/scheduleOptimizer'
import { useRedesignCalendarStore } from './redesignCalendar'

vi.mock('@/api/schedule', () => ({
  scheduleApi: {
    list: vi.fn(),
    update: vi.fn(),
  },
}))

vi.mock('@/api/scheduleOptimizer', () => ({
  scheduleOptimizerApi: {
    getRecommendations: vi.fn(),
    applyRecommendation: vi.fn(),
  },
}))

const schedule = {
  id: 4,
  videoId: 10,
  videoTitle: '예약 영상',
  thumbnailUrl: null,
  scheduledAt: '2026-08-10T09:00',
  platforms: [],
  status: 'SCHEDULED' as const,
  createdAt: '2026-08-01T00:00',
  updatedAt: '2026-08-01T00:00',
}

describe('redesign calendar store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the visible week from the server', async () => {
    vi.mocked(scheduleApi.list).mockResolvedValue([schedule])
    const store = useRedesignCalendarStore()

    await store.fetchWeek()

    expect(store.schedules).toEqual([schedule])
    expect(store.loading).toBe(false)
    expect(store.loadError).toBe(false)
  })

  it('keeps confirmed reservations visible when a refresh fails', async () => {
    vi.mocked(scheduleApi.list).mockResolvedValueOnce([schedule]).mockRejectedValueOnce(new Error('offline'))
    const store = useRedesignCalendarStore()

    await store.fetchWeek()
    await store.shiftWeek(1)

    expect(store.schedules).toEqual([schedule])
    expect(store.loadError).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('replaces a moved reservation only after the server confirms it', async () => {
    const updated = { ...schedule, scheduledAt: '2026-08-11T10:00' }
    vi.mocked(scheduleApi.list).mockResolvedValue([schedule])
    vi.mocked(scheduleApi.update).mockResolvedValue(updated)
    const store = useRedesignCalendarStore()
    await store.fetchWeek()

    await store.moveSchedule(4, new Date(2026, 7, 11, 10, 0))

    expect(scheduleApi.update).toHaveBeenCalledWith(4, { scheduledAt: '2026-08-11T10:00' })
    expect(store.schedules[0]).toEqual(updated)
  })

  it('reports recommendation availability from the server', async () => {
    vi.mocked(scheduleOptimizerApi.getRecommendations).mockResolvedValue([{} as never, {} as never])
    const store = useRedesignCalendarStore()
    await expect(store.fetchOptimalRecommendations()).resolves.toBe(2)
  })

  it('applies a recommendation and refreshes the confirmed calendar', async () => {
    const recommendation = {
      id: 11,
      videoId: 10,
      videoTitle: '예약 영상',
      currentSchedule: '2026-08-10T09:00',
      recommendedSchedule: '2026-08-11T10:00',
      platform: 'YOUTUBE',
      expectedImprovement: 18,
      confidence: 82,
      status: 'PENDING',
      createdAt: '2026-08-01T00:00',
    }
    vi.mocked(scheduleOptimizerApi.getRecommendations).mockResolvedValue([recommendation])
    vi.mocked(scheduleOptimizerApi.applyRecommendation).mockResolvedValue({
      ...recommendation,
      status: 'APPLIED',
    })
    vi.mocked(scheduleApi.list).mockResolvedValue([schedule])
    const store = useRedesignCalendarStore()

    await store.fetchOptimalRecommendations()
    await store.applyRecommendation(11)

    expect(scheduleOptimizerApi.applyRecommendation).toHaveBeenCalledWith(11)
    expect(scheduleApi.list).toHaveBeenCalled()
    expect(store.recommendations[0]?.status).toBe('APPLIED')
  })
})
