import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Notification, NotificationCategory, NotificationSetting } from '@/types/notification'
import { notificationApi } from '@/api/notification'

const STORAGE_KEY = 'ongo_notifications'
const SETTINGS_STORAGE_KEY = 'ongo_notification_settings'

export const useNotificationCenterStore = defineStore('notificationCenter', () => {
  const notifications = ref<Notification[]>([])
  const activeCategory = ref<NotificationCategory | null>(null)
  const settings = ref<NotificationSetting[]>([
    { category: 'upload', inApp: true, email: true, kakao: false },
    { category: 'schedule', inApp: true, email: true, kakao: true },
    { category: 'channel', inApp: true, email: true, kakao: false },
    { category: 'ai', inApp: true, email: false, kakao: false },
    { category: 'analytics', inApp: true, email: false, kakao: false },
    { category: 'subscription', inApp: true, email: true, kakao: true },
  ])

  // --- Computed ---

  const unreadCount = computed(() => notifications.value.filter((n) => !n.isRead).length)
  const hasUnread = computed(() => unreadCount.value > 0)

  const unreadNotifications = computed(() => notifications.value.filter((n) => !n.isRead))

  const filteredNotifications = computed(() => {
    if (!activeCategory.value) return notifications.value
    return notifications.value.filter((n) => n.category === activeCategory.value)
  })

  const unreadCountByCategory = computed(() => {
    const counts: Record<string, number> = {}
    const categories: NotificationCategory[] = ['upload', 'schedule', 'channel', 'ai', 'analytics', 'subscription']
    categories.forEach((cat) => {
      counts[cat] = notifications.value.filter((n) => n.category === cat && !n.isRead).length
    })
    return counts
  })

  const groupedByDate = computed(() => {
    const source = filteredNotifications.value
    const now = new Date()
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const yesterdayStart = new Date(todayStart.getTime() - 86400000)
    const weekStart = new Date(todayStart.getTime() - 6 * 86400000)

    const groups: { label: string; notifications: Notification[] }[] = [
      { label: '오늘', notifications: [] },
      { label: '어제', notifications: [] },
      { label: '이번 주', notifications: [] },
      { label: '이전', notifications: [] },
    ]

    source.forEach((n) => {
      const created = new Date(n.createdAt)
      if (created >= todayStart) {
        groups[0].notifications.push(n)
      } else if (created >= yesterdayStart) {
        groups[1].notifications.push(n)
      } else if (created >= weekStart) {
        groups[2].notifications.push(n)
      } else {
        groups[3].notifications.push(n)
      }
    })

    return groups.filter((g) => g.notifications.length > 0)
  })

  // --- Storage ---

  function loadFromStorage() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        notifications.value = JSON.parse(stored)
      }
      const storedSettings = localStorage.getItem(SETTINGS_STORAGE_KEY)
      if (storedSettings) {
        settings.value = JSON.parse(storedSettings)
      }
    } catch {
      // silently ignore localStorage errors
    }
  }

  function saveToStorage() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(notifications.value))
      localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings.value))
    } catch {
      // silently ignore localStorage errors
    }
  }

  // --- Actions ---

  async function markAsRead(id: number) {
    const notification = notifications.value.find((n) => n.id === id)
    if (notification) {
      notification.isRead = true
      saveToStorage()
      try {
        await notificationApi.markAsRead(id)
      } catch {
        // Local state already updated
      }
    }
  }

  async function markAllAsRead() {
    notifications.value.forEach((n) => {
      n.isRead = true
    })
    saveToStorage()
    try {
      await notificationApi.markAllAsRead()
    } catch {
      // Local state already updated
    }
  }

  async function deleteNotification(id: number) {
    notifications.value = notifications.value.filter((n) => n.id !== id)
    saveToStorage()
    try {
      await notificationApi.delete(id)
    } catch {
      // Local state already updated
    }
  }

  function clearAll() {
    notifications.value = []
    saveToStorage()
  }

  function addNotification(notification: Omit<Notification, 'id' | 'createdAt'>) {
    const newNotification: Notification = {
      ...notification,
      id: Date.now(),
      createdAt: new Date().toISOString(),
    }
    notifications.value.unshift(newNotification)
    saveToStorage()
  }

  function filterByCategory(category: NotificationCategory | null) {
    activeCategory.value = category
  }

  function updateSetting(category: NotificationCategory, field: 'inApp' | 'email' | 'kakao', value: boolean) {
    const setting = settings.value.find((s) => s.category === category)
    if (setting) {
      setting[field] = value
      saveToStorage()
    }
  }

  async function fetchNotifications() {
    loadFromStorage()

    try {
      const result = await notificationApi.list({ page: 0, size: 50 })
      if (result.notifications && result.notifications.length > 0) {
        notifications.value = result.notifications.map((n) => ({
          id: n.id,
          type: n.type.toLowerCase() as Notification['type'],
          category: mapTypeToCategory(n.type),
          title: n.title,
          message: n.message,
          isRead: n.isRead,
          referenceType: n.referenceType ?? undefined,
          referenceId: n.referenceId ?? undefined,
          createdAt: n.createdAt ?? new Date().toISOString(),
        }))
        saveToStorage()
        return
      }
    } catch {
      // Fall back to mock data
    }

    if (notifications.value.length === 0) {
      const now = Date.now()
      notifications.value = [
        { id: 1, type: 'upload_success', category: 'upload', title: '영상 업로드 완료', message: '"겨울 여행 브이로그" 영상이 성공적으로 업로드되었습니다.', isRead: false, referenceType: 'video', referenceId: 101, createdAt: new Date(now - 1000 * 60 * 30).toISOString() },
        { id: 2, type: 'platform_published', category: 'upload', title: 'YouTube 게시 완료', message: '"겨울 여행 브이로그"가 YouTube에 게시되었습니다.', isRead: false, referenceType: 'video', referenceId: 101, createdAt: new Date(now - 1000 * 60 * 45).toISOString() },
        { id: 3, type: 'platform_failed', category: 'upload', title: 'TikTok 게시 실패', message: '"새해 인사 영상"의 TikTok 게시가 실패했습니다. 토큰을 확인해주세요.', isRead: false, referenceType: 'video', referenceId: 102, createdAt: new Date(now - 1000 * 60 * 60 * 2).toISOString() },
        { id: 4, type: 'schedule_reminder', category: 'schedule', title: '예약 게시 알림', message: '"주간 먹방 콘텐츠" 30분 후 예약 게시가 시작됩니다.', isRead: false, referenceType: 'schedule', referenceId: 45, createdAt: new Date(now - 1000 * 60 * 15).toISOString() },
        { id: 5, type: 'schedule_completed', category: 'schedule', title: '예약 게시 완료', message: '"일상 브이로그 #23" 예약 게시가 모든 플랫폼에 완료되었습니다.', isRead: false, referenceType: 'schedule', referenceId: 44, createdAt: new Date(now - 1000 * 60 * 60 * 4).toISOString() },
        { id: 6, type: 'upload_failed', category: 'upload', title: '영상 업로드 실패', message: '"카페 투어" 영상 업로드 중 오류가 발생했습니다. 다시 시도해주세요.', isRead: true, referenceType: 'video', referenceId: 103, createdAt: new Date(now - 1000 * 60 * 60 * 8).toISOString() },
        { id: 7, type: 'token_expiring', category: 'channel', title: 'Instagram 토큰 만료 예정', message: 'Instagram 연동 토큰이 7일 후 만료됩니다. 재연동이 필요합니다.', isRead: false, referenceType: 'channel', referenceId: 3, createdAt: new Date(now - 1000 * 60 * 60 * 24).toISOString() },
        { id: 8, type: 'token_expired', category: 'channel', title: 'Naver Clip 토큰 만료', message: 'Naver Clip 연동 토큰이 만료되었습니다. 즉시 재연동해주세요.', isRead: false, referenceType: 'channel', referenceId: 5, createdAt: new Date(now - 1000 * 60 * 60 * 26).toISOString() },
        { id: 9, type: 'credit_low', category: 'ai', title: 'AI 크레딧 부족', message: '남은 AI 크레딧이 15개입니다. 추가 충전을 고려해주세요.', isRead: true, referenceType: 'credit', createdAt: new Date(now - 1000 * 60 * 60 * 30).toISOString() },
        { id: 10, type: 'credit_depleted', category: 'ai', title: 'AI 크레딧 소진', message: 'AI 크레딧이 모두 소진되었습니다. 충전 후 AI 기능을 이용할 수 있습니다.', isRead: false, referenceType: 'credit', createdAt: new Date(now - 1000 * 60 * 60 * 48).toISOString() },
        { id: 11, type: 'milestone', category: 'analytics', title: '조회수 목표 달성!', message: '"코디 추천 영상"이 조회수 10,000회를 돌파했습니다!', isRead: true, referenceType: 'video', referenceId: 98, createdAt: new Date(now - 1000 * 60 * 60 * 72).toISOString() },
        { id: 12, type: 'report_ready', category: 'analytics', title: '주간 리포트 준비 완료', message: '1월 4주차 주간 분석 리포트가 생성되었습니다.', isRead: true, referenceType: 'report', referenceId: 12, createdAt: new Date(now - 1000 * 60 * 60 * 24 * 4).toISOString() },
        { id: 13, type: 'payment_reminder', category: 'subscription', title: '구독 결제 예정', message: 'Pro 플랜 결제가 3일 후 자동 갱신됩니다.', isRead: true, referenceType: 'payment', createdAt: new Date(now - 1000 * 60 * 60 * 24 * 5).toISOString() },
        { id: 14, type: 'payment_failed', category: 'subscription', title: '결제 실패', message: '등록된 카드 결제에 실패했습니다. 결제 수단을 확인해주세요.', isRead: false, referenceType: 'payment', createdAt: new Date(now - 1000 * 60 * 60 * 24 * 6).toISOString() },
        { id: 15, type: 'platform_published', category: 'upload', title: 'Instagram Reels 게시 완료', message: '"봄맞이 코디 하울"이 Instagram Reels에 게시되었습니다.', isRead: true, referenceType: 'video', referenceId: 95, createdAt: new Date(now - 1000 * 60 * 60 * 24 * 7).toISOString() },
        { id: 16, type: 'milestone', category: 'analytics', title: '구독자 목표 달성!', message: 'YouTube 채널 구독자가 5,000명을 돌파했습니다!', isRead: true, referenceType: 'channel', referenceId: 1, createdAt: new Date(now - 1000 * 60 * 60 * 24 * 10).toISOString() },
      ]
      saveToStorage()
    }
  }

  return {
    notifications,
    activeCategory,
    settings,
    unreadCount,
    hasUnread,
    unreadNotifications,
    filteredNotifications,
    unreadCountByCategory,
    groupedByDate,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    clearAll,
    addNotification,
    filterByCategory,
    updateSetting,
    fetchNotifications,
  }
})

function mapTypeToCategory(type: string): NotificationCategory {
  const t = type.toLowerCase()
  if (t.includes('upload') || t.includes('platform')) return 'upload'
  if (t.includes('schedule')) return 'schedule'
  if (t.includes('token') || t.includes('channel')) return 'channel'
  if (t.includes('credit')) return 'ai'
  if (t.includes('milestone') || t.includes('report')) return 'analytics'
  if (t.includes('payment') || t.includes('subscription')) return 'subscription'
  return 'upload'
}
