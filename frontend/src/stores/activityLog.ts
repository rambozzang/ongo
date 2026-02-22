import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { activityLogApi } from '@/api/activityLog'
import { useNotificationStore } from '@/stores/notification'

export type ActivityEventType =
  | 'upload'
  | 'publish'
  | 'schedule'
  | 'edit'
  | 'delete'
  | 'ai_use'
  | 'login'
  | 'channel_connect'
  | 'recycle'

export interface ActivityEvent {
  id: string
  type: ActivityEventType
  description: string
  metadata?: {
    videoTitle?: string
    platform?: string
    creditsUsed?: number
    [key: string]: string | number | null | undefined
  }
  createdAt: string
  icon?: string
}

const ACTION_TYPE_MAP: Record<string, ActivityEventType> = {
  UPLOAD: 'upload',
  PUBLISH: 'publish',
  SCHEDULE: 'schedule',
  EDIT: 'edit',
  DELETE: 'delete',
  AI_USE: 'ai_use',
  LOGIN: 'login',
  CHANNEL_CONNECT: 'channel_connect',
  RECYCLE: 'recycle',
}

export const useActivityLogStore = defineStore('activityLog', () => {
  const events = ref<ActivityEvent[]>([])
  const loading = ref(false)

  // Load from API
  async function loadEvents() {
    loading.value = true
    try {
      const response = await activityLogApi.list({ page: 0, size: 100 })
      events.value = response.logs.map((log) => ({
        id: String(log.id),
        type: ACTION_TYPE_MAP[log.action] ?? 'edit',
        description: log.details,
        metadata: log.entityType ? { entityType: log.entityType, entityId: log.entityId } : undefined,
        createdAt: log.createdAt ?? new Date().toISOString(),
      }))
    } catch (error) {
      useNotificationStore().error('활동 로그를 불러오는 중 오류가 발생했습니다')
      events.value = []
    } finally {
      loading.value = false
    }
  }

  // Clear all events (client side only)
  function clearEvents() {
    events.value = []
  }

  // Get events grouped by date
  function getEventsByDate() {
    const grouped = new Map<string, ActivityEvent[]>()
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)

    events.value.forEach((event) => {
      const eventDate = new Date(event.createdAt)
      const eventDateOnly = new Date(eventDate.getFullYear(), eventDate.getMonth(), eventDate.getDate())

      let dateKey: string
      if (eventDateOnly.getTime() === today.getTime()) {
        dateKey = '오늘'
      } else if (eventDateOnly.getTime() === yesterday.getTime()) {
        dateKey = '어제'
      } else {
        // Format: "2월 7일 금요일"
        const month = eventDate.getMonth() + 1
        const date = eventDate.getDate()
        const dayOfWeek = ['일', '월', '화', '수', '목', '금', '토'][eventDate.getDay()]
        dateKey = `${month}월 ${date}일 ${dayOfWeek}요일`
      }

      if (!grouped.has(dateKey)) {
        grouped.set(dateKey, [])
      }
      grouped.get(dateKey)!.push(event)
    })

    return Array.from(grouped.entries())
  }

  // Count events by type
  const eventCounts = computed(() => {
    const counts: Record<ActivityEventType | 'all', number> = {
      all: events.value.length,
      upload: 0,
      publish: 0,
      schedule: 0,
      edit: 0,
      delete: 0,
      ai_use: 0,
      login: 0,
      channel_connect: 0,
      recycle: 0,
    }

    events.value.forEach((event) => {
      counts[event.type]++
    })

    return counts
  })

  // Initialize on store creation
  loadEvents()

  return {
    events,
    loading,
    loadEvents,
    clearEvents,
    getEventsByDate,
    eventCounts,
  }
})
