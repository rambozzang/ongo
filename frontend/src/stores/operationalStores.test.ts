import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAnalyticsStore } from './analytics'
import { useNotificationCenterStore } from './notificationCenter'
import { useSubscriptionStore } from './subscription'
import { analyticsApi } from '@/api/analytics'
import { notificationApi } from '@/api/notification'
import { subscriptionApi } from '@/api/subscription'
import { paymentApi } from '@/api/payment'

vi.mock('@/api/analytics', () => ({
  analyticsApi: {
    dashboard: vi.fn(), trends: vi.fn(), platformComparison: vi.fn(), heatmap: vi.fn(), topVideos: vi.fn(),
    trafficSources: vi.fn(), demographics: vi.fn(), ctr: vi.fn(), avgViewDuration: vi.fn(),
    subscriberConversion: vi.fn(), crossPlatformComparison: vi.fn(),
  },
}))
vi.mock('@/api/notification', () => ({
  notificationApi: {
    list: vi.fn(), markAsRead: vi.fn(), markAllAsRead: vi.fn(), delete: vi.fn(),
  },
}))
vi.mock('@/api/subscription', () => ({
  subscriptionApi: {
    getCurrent: vi.fn(), getPlans: vi.fn(), changePlan: vi.fn(), cancel: vi.fn(), startTrial: vi.fn(),
    pauseSubscription: vi.fn(), resumeSubscription: vi.fn(), validateCoupon: vi.fn(), applyCoupon: vi.fn(),
  },
}))
vi.mock('@/api/payment', () => ({ paymentApi: { getHistory: vi.fn() } }))

describe('operational stores', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('retains successful analytics panels while surfacing partial fanout failures', async () => {
    vi.mocked(analyticsApi.dashboard).mockResolvedValue({ totalViews: 10 } as never)
    vi.mocked(analyticsApi.trends).mockRejectedValue(new Error('trend unavailable'))
    vi.mocked(analyticsApi.platformComparison).mockResolvedValue([{ platform: 'YOUTUBE' }] as never)
    vi.mocked(analyticsApi.heatmap).mockResolvedValue([{ dayOfWeek: 1, hour: 9, value: 3 }] as never)
    vi.mocked(analyticsApi.topVideos).mockResolvedValue([] as never)
    vi.mocked(analyticsApi.crossPlatformComparison).mockResolvedValue({ videos: [], platformRankings: {} } as never)
    const store = useAnalyticsStore()

    await store.fetchAnalytics()
    expect(store.kpi).toMatchObject({ totalViews: 10 })
    expect(store.platformComparison).toHaveLength(1)
    expect(store.heatmapData).toEqual(store.postingHeatmapData)
    expect(store.loadError).toContain('일부')
    expect(store.loading).toBe(false)

    await store.fetchCrossPlatform(14)
    expect(analyticsApi.crossPlatformComparison).toHaveBeenCalledWith(14)
    expect(store.crossPlatformData?.videos).toEqual([])
  })

  it('loads deep analytics independently and keeps a useful error state', async () => {
    vi.mocked(analyticsApi.trafficSources).mockResolvedValue({ period: '30d', sources: {}, total: 0 } as never)
    vi.mocked(analyticsApi.demographics).mockRejectedValue(new Error('demographics unavailable'))
    vi.mocked(analyticsApi.ctr).mockResolvedValue({ period: '30d', avgCTR: 4, totalImpressions: 10, data: [] } as never)
    vi.mocked(analyticsApi.avgViewDuration).mockResolvedValue({ period: '30d', avgDurationSeconds: 20, data: [] } as never)
    vi.mocked(analyticsApi.subscriberConversion).mockResolvedValue({ period: '30d', totalGained: 2, data: [] } as never)
    const store = useAnalyticsStore()

    await store.fetchDeepAnalytics(30)
    expect(store.trafficSources?.total).toBe(0)
    expect(store.demographics).toBeNull()
    expect(store.ctrData?.avgCTR).toBe(4)
    expect(store.deepAnalyticsError).toContain('일부')
    expect(store.deepAnalyticsLoading).toBe(false)

    vi.mocked(analyticsApi.crossPlatformComparison).mockRejectedValue(new Error('comparison unavailable'))
    await store.fetchCrossPlatform()
    expect(store.crossPlatformError).toBe('comparison unavailable')
    expect(store.crossPlatformLoading).toBe(false)
  })

  it('supports notification filtering, pagination, settings, and server-backed actions', async () => {
    const now = new Date()
    vi.mocked(notificationApi.list).mockResolvedValue({
      notifications: [
        { id: 1, type: 'UPLOAD_COMPLETE', title: '게시 완료', message: '완료', isRead: false, referenceType: 'video', referenceId: 7, createdAt: now.toISOString() },
        { id: 2, type: 'CHANNEL_TOKEN_EXPIRED', title: '재연결 필요', message: '토큰 만료', isRead: true, referenceType: null, referenceId: null, createdAt: new Date(now.getTime() - 8 * 86400000).toISOString() },
      ], totalElements: 21, page: 0, size: 20,
    })
    const store = useNotificationCenterStore()
    await store.fetchNotifications()

    expect(store.unreadCount).toBe(1)
    expect(store.unreadCountByCategory.upload).toBe(1)
    expect(store.totalPages).toBe(2)
    expect(store.groupedByDate).toHaveLength(2)
    store.filterByCategory('channel')
    expect(store.filteredNotifications).toHaveLength(1)
    store.updateSetting('channel', 'email', true)
    expect(store.settings.find((item) => item.category === 'channel')?.email).toBe(true)

    vi.mocked(notificationApi.markAsRead).mockResolvedValue(undefined)
    await store.markAsRead(1)
    expect(store.unreadCount).toBe(0)
    vi.mocked(notificationApi.markAllAsRead).mockResolvedValue(undefined)
    await store.markAllAsRead()
    vi.mocked(notificationApi.delete).mockResolvedValue(undefined)
    await store.deleteNotification(2)
    expect(store.notifications).toHaveLength(1)
    store.addNotification({ type: 'upload_success', category: 'upload', title: '새 알림', message: '새 알림', isRead: false })
    expect(store.notifications[0].title).toBe('새 알림')
    store.clearAll()
    expect(store.notifications).toEqual([])
  })

  it('keeps notification errors visible and prevents failed destructive actions', async () => {
    const store = useNotificationCenterStore()
    vi.mocked(notificationApi.list).mockRejectedValue(new Error('알림 서버 장애'))
    await expect(store.fetchNotifications()).rejects.toThrow('알림 서버 장애')
    expect(store.loadError).toBe('알림 서버 장애')
    vi.mocked(notificationApi.markAsRead).mockRejectedValue(new Error('read failed'))
    store.notifications.push({ id: 1, type: 'upload_success', category: 'upload', title: 't', message: 'm', isRead: false, createdAt: new Date().toISOString() })
    await expect(store.markAsRead(1)).rejects.toThrow('알림 읽음 처리에 실패했습니다')
    expect(store.notifications[0].isRead).toBe(false)
  })

  it('maps subscription plans, billing actions, coupon actions, and payment history', async () => {
    const subscription = { planType: 'PRO', status: 'ACTIVE' } as never
    vi.mocked(subscriptionApi.getCurrent).mockResolvedValue(subscription)
    vi.mocked(subscriptionApi.getPlans).mockResolvedValue({
      currentPlan: 'PRO',
      plans: [{ planType: 'PRO', price: 19900, yearlyPrice: 199000, recommended: true, features: { maxPlatforms: 7, monthlyUploads: 100, scheduleDays: 30, analyticsDays: 365, storageGB: 50, freeCredits: 300, maxTeamMembers: 2 } }],
    } as never)
    vi.mocked(paymentApi.getHistory).mockResolvedValue({ content: [], totalElements: 0 } as never)
    vi.mocked(subscriptionApi.changePlan).mockResolvedValue(subscription)
    vi.mocked(subscriptionApi.cancel).mockResolvedValue(subscription)
    vi.mocked(subscriptionApi.startTrial).mockResolvedValue(subscription)
    vi.mocked(subscriptionApi.pauseSubscription).mockResolvedValue(subscription)
    vi.mocked(subscriptionApi.resumeSubscription).mockResolvedValue(subscription)
    vi.mocked(subscriptionApi.validateCoupon).mockResolvedValue({ valid: true } as never)
    vi.mocked(subscriptionApi.applyCoupon).mockResolvedValue({ valid: true } as never)
    const store = useSubscriptionStore()

    await store.fetchSubscription()
    await store.fetchPlans()
    await store.fetchPayments()
    await store.changePlan('PRO', 'YEARLY')
    await store.cancelSubscription()
    await store.startTrial('PRO')
    await store.pauseSubscription()
    await store.resumeSubscription()
    expect(await store.validateCoupon('WELCOME')).toMatchObject({ valid: true })
    expect(await store.applyCoupon('WELCOME')).toMatchObject({ valid: true })
    expect(store.plans[0]).toMatchObject({ storageMb: 51200, support: '우선 이메일' })
    expect(store.currentPlan).toBe('PRO')
    expect(store.loading).toBe(false)
  })

  it('does not hide subscription failures behind stale loading state', async () => {
    const store = useSubscriptionStore()
    vi.mocked(subscriptionApi.getCurrent).mockRejectedValue(new Error('구독 장애'))
    await expect(store.fetchSubscription()).rejects.toThrow('구독 장애')
    expect(store.error).toBe('구독 장애')
    expect(store.loading).toBe(false)
    vi.mocked(subscriptionApi.getPlans).mockRejectedValue(new Error('플랜 장애'))
    await expect(store.fetchPlans()).rejects.toThrow('플랜 장애')
    expect(store.plans).toEqual([])
  })
})
