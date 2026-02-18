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
    try {
      const result = await notificationApi.list({ page: 0, size: 50 })
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
        saveToStorage()
      }
    } catch {
      // On API failure, try loading cached data from localStorage
      loadFromStorage()
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
