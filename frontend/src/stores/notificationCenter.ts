import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Notification, NotificationCategory, NotificationSetting } from '@/types/notification'
import { notificationApi } from '@/api/notification'

export const useNotificationCenterStore = defineStore('notificationCenter', () => {
  const notifications = ref<Notification[]>([])
  const activeCategory = ref<NotificationCategory | null>(null)
  const page = ref(0)
  const pageSize = ref(20)
  const totalCount = ref(0)
  const loadError = ref<string | null>(null)
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

  // --- Actions ---

  async function markAsRead(id: number) {
    const notification = notifications.value.find((n) => n.id === id)
    if (notification) {
      try {
        await notificationApi.markAsRead(id)
      } catch {
        throw new Error('알림 읽음 처리에 실패했습니다')
      }
      notification.isRead = true
    }
  }

  async function markAllAsRead() {
    try {
      await notificationApi.markAllAsRead()
    } catch {
      throw new Error('알림 읽음 처리에 실패했습니다')
    }
    notifications.value.forEach((n) => { n.isRead = true })
  }

  async function deleteNotification(id: number) {
    try {
      await notificationApi.delete(id)
    } catch {
      throw new Error('알림 삭제에 실패했습니다')
    }
    notifications.value = notifications.value.filter((n) => n.id !== id)
  }

  function clearAll() {
    notifications.value = []
  }

  function addNotification(notification: Omit<Notification, 'id' | 'createdAt'>) {
    const newNotification: Notification = {
      ...notification,
      id: Date.now(),
      createdAt: new Date().toISOString(),
    }
    notifications.value.unshift(newNotification)
  }

  function filterByCategory(category: NotificationCategory | null) {
    activeCategory.value = category
  }

  function updateSetting(category: NotificationCategory, field: 'inApp' | 'email' | 'kakao', value: boolean) {
    const setting = settings.value.find((s) => s.category === category)
    if (setting) {
      setting[field] = value
    }
  }

  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))
  const hasNextPage = computed(() => (page.value + 1) * pageSize.value < totalCount.value)
  const hasPrevPage = computed(() => page.value > 0)

  async function fetchNotifications() {
    loadError.value = null
    try {
      const result = await notificationApi.list({ page: page.value, size: pageSize.value })
      totalCount.value = result.totalElements ?? 0
      if (result.notifications) {
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
      }
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '알림을 불러오지 못했습니다'
      throw error
    }
  }

  function nextPage() {
    if (hasNextPage.value) {
      page.value++
      fetchNotifications()
    }
  }

  function prevPage() {
    if (hasPrevPage.value) {
      page.value--
      fetchNotifications()
    }
  }

  async function syncUnreadCount() {
    try {
      await fetchNotifications()
    } catch {
      // 무시
    }
  }

  return {
    notifications,
    activeCategory,
    page,
    pageSize,
    totalCount,
    settings,
    unreadCount,
    hasUnread,
    unreadNotifications,
    filteredNotifications,
    unreadCountByCategory,
    groupedByDate,
    totalPages,
    loadError,
    hasNextPage,
    hasPrevPage,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    clearAll,
    addNotification,
    filterByCategory,
    updateSetting,
    fetchNotifications,
    nextPage,
    prevPage,
    syncUnreadCount,
  }
})

const TYPE_CATEGORY_MAP: Record<string, NotificationCategory> = {
  'UPLOAD_COMPLETE': 'upload',
  'UPLOAD_FAILED': 'upload',
  'CREDIT_LOW': 'ai',
  'SCHEDULE_REMINDER': 'schedule',
  'COMMENT': 'upload',
  'SYSTEM': 'upload',
  'CHANNEL_TOKEN_EXPIRED': 'channel',
}

function mapTypeToCategory(type: string): NotificationCategory {
  return TYPE_CATEGORY_MAP[type.toUpperCase()] ?? 'upload'
}
