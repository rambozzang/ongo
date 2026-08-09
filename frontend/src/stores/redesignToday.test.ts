import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { channelApi } from '@/api/channel'
import { scheduleApi } from '@/api/schedule'
import { inboxApi } from '@/api/inbox'
import { analyticsApi } from '@/api/analytics'
import { useRedesignShellStore } from './redesignShell'
import { useRedesignTodayStore } from './redesignToday'

vi.mock('@/api/channel', () => ({
  channelApi: { list: vi.fn() },
}))

vi.mock('@/api/schedule', () => ({
  scheduleApi: { list: vi.fn() },
}))

vi.mock('@/api/inbox', () => ({
  inboxApi: { getUnreadCount: vi.fn() },
}))

vi.mock('@/api/analytics', () => ({
  analyticsApi: { dashboard: vi.fn() },
}))

const makeSchedule = (
  id: number,
  status: 'SCHEDULED' | 'PROCESSING' | 'PUBLISHED' | 'FAILED' | 'UNCONFIRMED' | 'PARTIALLY_PUBLISHED',
  scheduledAt: string,
) => ({
  id,
  videoId: id * 10,
  videoTitle: `영상 ${id}`,
  thumbnailUrl: null,
  scheduledAt,
  platforms: [{ platform: id === 1 ? 'YOUTUBE' : 'TWITTER' }],
  status,
  createdAt: '2026-08-01T00:00',
  updatedAt: '2026-08-01T00:00',
})

const channel = {
  id: 7,
  platform: 'INSTAGRAM',
  channelName: '온고 채널',
  subscriberCount: 1234,
  tokenStatus: 'EXPIRED',
} as never

describe('redesign today store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(analyticsApi.dashboard).mockResolvedValue({
      totalViews: 12345,
      viewsChangePercent: 12.3,
    } as never)
  })

  it('maps server queue, attention, KPI, and rail counts together', async () => {
    const todaySchedules = [
      makeSchedule(2, 'PROCESSING', '2026-08-09T18:30'),
      makeSchedule(1, 'SCHEDULED', '2026-08-09T09:00'),
      makeSchedule(3, 'FAILED', '2026-08-09T20:00'),
    ]
    vi.mocked(scheduleApi.list)
      .mockResolvedValueOnce(todaySchedules as never)
      .mockResolvedValueOnce([...todaySchedules, makeSchedule(4, 'PUBLISHED', '2026-08-08T10:00')] as never)
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [channel] } as never)
    vi.mocked(inboxApi.getUnreadCount).mockResolvedValue({ count: 4 } as never)

    const store = useRedesignTodayStore()
    await store.load()

    expect(store.queue.map((row) => row.id)).toEqual([1, 2, 3])
    expect(store.queue[1].platforms).toEqual(['TW'])
    expect(store.queue[2].statusLabel).toBe('발행 실패')
    expect(store.attention.map((item) => item.id)).toEqual(['token-7', 'failed'])
    expect(store.channels[0]).toMatchObject({ platform: 'IG', name: '온고 채널', statusLabel: '토큰 만료' })
    expect(store.kpi).toMatchObject({
      scheduled: 1,
      pending: 1,
      failed: 1,
      unanswered: 4,
      viewsLabel: '1.2만',
      viewsDelta: '+12.3%',
      weeklyPublished: 1,
    })
    expect(useRedesignShellStore().badges).toEqual({ today: '3', inbox: '4', calendar: '1', channels: '!' })
    expect(store.loadError).toBeNull()
  })

  it('surfaces unconfirmed and partial publication as actionable detail items', async () => {
    vi.mocked(scheduleApi.list).mockResolvedValue([
      makeSchedule(10, 'UNCONFIRMED', '2026-08-09T09:00'),
      makeSchedule(11, 'PARTIALLY_PUBLISHED', '2026-08-09T10:00'),
    ] as never)
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [] } as never)
    vi.mocked(inboxApi.getUnreadCount).mockResolvedValue({ count: 0 } as never)

    const store = useRedesignTodayStore()
    await store.load()

    expect(store.attention).toEqual([
      expect.objectContaining({
        id: 'publish-10',
        severity: 'warning',
        to: '/videos/100',
        cta: '상세 확인',
      }),
      expect.objectContaining({
        id: 'publish-11',
        severity: 'warning',
        to: '/videos/110',
      }),
    ])
  })

  it('keeps the last confirmed queue when a later refresh partially fails', async () => {
    const confirmed = makeSchedule(1, 'SCHEDULED', '2026-08-09T09:00')
    vi.mocked(scheduleApi.list)
      .mockResolvedValueOnce([confirmed] as never)
      .mockResolvedValueOnce([confirmed] as never)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([confirmed] as never)
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [] } as never)
    vi.mocked(inboxApi.getUnreadCount).mockResolvedValue({ count: 2 } as never)
    const store = useRedesignTodayStore()

    await store.load()
    await store.load()

    expect(store.queue).toHaveLength(1)
    expect(store.queue[0].id).toBe(1)
    expect(store.kpi.scheduled).toBe(1)
    expect(store.loadError).toBe('loadPartial')
    expect(store.loading).toBe(false)
  })

  it('marks a complete outage without erasing confirmed data', async () => {
    const confirmed = makeSchedule(1, 'SCHEDULED', '2026-08-09T09:00')
    vi.mocked(scheduleApi.list)
      .mockResolvedValueOnce([confirmed] as never)
      .mockResolvedValueOnce([confirmed] as never)
      .mockRejectedValueOnce(new Error('offline'))
      .mockRejectedValueOnce(new Error('offline'))
    vi.mocked(channelApi.list).mockResolvedValueOnce({ channels: [channel] } as never).mockRejectedValueOnce(new Error('offline'))
    vi.mocked(inboxApi.getUnreadCount).mockResolvedValueOnce({ count: 2 } as never).mockRejectedValueOnce(new Error('offline'))
    vi.mocked(analyticsApi.dashboard).mockResolvedValueOnce({ totalViews: 100, viewsChangePercent: 0 } as never).mockRejectedValueOnce(new Error('offline'))
    const store = useRedesignTodayStore()

    await store.load()
    await store.load()

    expect(store.queue[0].id).toBe(1)
    expect(store.channels[0].id).toBe(7)
    expect(store.loadError).toBe('loadFailed')
  })
})
