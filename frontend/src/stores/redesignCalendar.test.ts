import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { scheduleApi } from '@/api/schedule'
import { scheduleOptimizerApi } from '@/api/scheduleOptimizer'
import { useRedesignCalendarStore } from './redesignCalendar'

vi.mock('@/api/schedule', () => ({
  scheduleApi: {
    list: vi.fn(),
    update: vi.fn(),
    cancel: vi.fn(),
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
    vi.mocked(scheduleApi.list)
      .mockResolvedValueOnce([schedule])
      .mockRejectedValueOnce(new Error('offline'))
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

describe('professional calendar operations', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('does not allow an older week request to overwrite the current week', async () => {
    let resolveOld!: (value: (typeof schedule)[]) => void
    vi.mocked(scheduleApi.list)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve
          }),
      )
      .mockResolvedValueOnce([{ ...schedule, id: 99 }])
    const store = useRedesignCalendarStore()
    const first = store.fetchWeek()
    await store.shiftWeek(1)
    resolveOld([schedule])
    await first
    expect(store.schedules.map((item) => item.id)).toEqual([99])
    expect(store.loading).toBe(false)
  })

  it('requests all six visible weeks for a monthly calendar', async () => {
    vi.mocked(scheduleApi.list).mockResolvedValue([])
    const store = useRedesignCalendarStore()
    store.weekStart = new Date(2026, 9, 1)
    await store.setView('month')
    expect(scheduleApi.list).toHaveBeenLastCalledWith({
      startDate: '2026-09-28',
      endDate: '2026-11-08',
    })
    expect(store.monthDays).toHaveLength(42)
  })

  it('retains only failed reservations after a partially successful batch cancel', async () => {
    vi.mocked(scheduleApi.cancel)
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new Error('Job already started'))
    const store = useRedesignCalendarStore()
    store.schedules = [schedule, { ...schedule, id: 5 }]
    const result = await store.batchSchedules([4, 5, 4], 'cancel')
    expect(result.succeeded).toEqual([4])
    expect(result.failed).toEqual([
      { id: 5, title: schedule.videoTitle, message: 'Job already started' },
    ])
    expect(store.schedules.map((item) => item.id)).toEqual([5])
    expect(scheduleApi.cancel).toHaveBeenCalledTimes(2)
    expect(store.moving).toBe(false)
  })

  it('sends wall-clock shifts without changing platform target identities', async () => {
    const store = useRedesignCalendarStore()
    store.schedules = [schedule]
    vi.mocked(scheduleApi.update).mockResolvedValue({
      ...schedule,
      scheduledAt: '2026-08-12T09:00',
    })
    await store.batchSchedules([4], 'shift', 2)
    expect(scheduleApi.update).toHaveBeenCalledWith(4, { scheduledAt: '2026-08-12T09:00' })
  })

  it('does not send a second concurrent batch or mutate a failed refresh', async () => {
    const store = useRedesignCalendarStore()
    store.schedules = [schedule]
    store.moving = true
    await store.batchSchedules([4], 'cancel')
    store.moving = false
    store.loadError = true
    await store.batchSchedules([4], 'cancel')
    expect(scheduleApi.cancel).not.toHaveBeenCalled()
  })
})

describe('calendar timezone and month boundary', () => {
  it('opens the current month when its first day belongs to the previous weekly anchor', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-01T12:00:00+09:00'))
    try {
      setActivePinia(createPinia())
      vi.mocked(scheduleApi.list).mockResolvedValue([])
      const store = useRedesignCalendarStore()
      expect(store.weekStart.getMonth()).toBe(9)
      await store.setView('month')
      expect(store.weekStart.getMonth()).toBe(10)
      expect(store.weekStart.getDate()).toBe(1)
    } finally {
      vi.useRealTimers()
    }
  })
  it('shifts a Korean wall-clock without interpreting browser daylight saving time', async () => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    const store = useRedesignCalendarStore()
    store.schedules = [{ ...schedule, scheduledAt: '2026-03-08T02:30' }]
    vi.mocked(scheduleApi.update).mockResolvedValue({
      ...schedule,
      scheduledAt: '2026-03-09T02:30',
    })
    await store.batchSchedules([4], 'shift', 1)
    expect(scheduleApi.update).toHaveBeenCalledWith(4, { scheduledAt: '2026-03-09T02:30' })
  })
})
