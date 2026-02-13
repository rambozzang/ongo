import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  ActivityLog,
  ActivityAction,
  ActivityLogFilter,
  ActivityDateRange,
  ActivityDateCustomRange,
} from '@/types/activitylog'
import { ACTION_LABELS } from '@/types/activitylog'
import { exportToCSV, formatDateForExport } from '@/utils/export'
import type { ColumnDefinition } from '@/utils/export'
import { activityLogApi } from '@/api/activityLog'

export const useActivityLogsStore = defineStore('activityLogs', () => {
  // State
  const logs = ref<ActivityLog[]>([])
  const isLoading = ref(false)
  const filter = ref<ActivityLogFilter>({
    action: null,
    dateRange: null,
    userId: null,
    searchQuery: '',
  })
  const customDateRange = ref<ActivityDateCustomRange>({
    startDate: '',
    endDate: '',
  })

  // Actions
  async function fetchLogs() {
    isLoading.value = true
    try {
      const result = await activityLogApi.list({ page: 0, size: 100 })
      if (result.logs && result.logs.length > 0) {
        logs.value = result.logs.map((log) => ({
          id: String(log.id),
          userId: String(log.userId),
          userName: String(log.userId),
          action: log.action as ActivityAction,
          entityType: (log.entityType ?? 'video') as ActivityLog['entityType'],
          entityId: log.entityId ? String(log.entityId) : undefined,
          details: log.details ? JSON.parse(log.details) : undefined,
          ipAddress: log.ipAddress ?? undefined,
          createdAt: log.createdAt ?? new Date().toISOString(),
        }))
      } else {
        logs.value = []
      }
    } catch {
      // API failed — show empty list
      logs.value = []
    } finally {
      isLoading.value = false
    }
  }

  function filterByAction(action: ActivityAction | null) {
    filter.value.action = action
  }

  function filterByDate(dateRange: ActivityDateRange | null, custom?: ActivityDateCustomRange) {
    filter.value.dateRange = dateRange
    if (dateRange === 'custom' && custom) {
      customDateRange.value = custom
    }
  }

  function filterByUser(userId: string | null) {
    filter.value.userId = userId
  }

  function setSearchQuery(query: string) {
    filter.value.searchQuery = query
  }

  function resetFilters() {
    filter.value = {
      action: null,
      dateRange: null,
      userId: null,
      searchQuery: '',
    }
    customDateRange.value = { startDate: '', endDate: '' }
  }

  function exportLogs() {
    const dataToExport = filteredLogs.value

    const columns: ColumnDefinition<ActivityLog>[] = [
      { key: 'createdAt', header: '일시', formatter: (val: string) => formatDateForExport(val) },
      { key: 'userName', header: '사용자' },
      { key: 'action', header: '활동 유형', formatter: (val: ActivityAction) => ACTION_LABELS[val] ?? val },
      { key: 'entityType', header: '대상 유형' },
      { key: 'entityName', header: '대상 이름', formatter: (val: string | undefined) => val ?? '-' },
      { key: 'ipAddress', header: 'IP 주소', formatter: (val: string | undefined) => val ?? '-' },
    ]

    const today = new Date()
    const dateStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    exportToCSV(dataToExport, columns, `onGo_activity_log_${dateStr}.csv`)
  }

  // Getters
  const filteredLogs = computed<ActivityLog[]>(() => {
    let result = [...logs.value]

    if (filter.value.action) {
      result = result.filter((log) => log.action === filter.value.action)
    }

    if (filter.value.userId) {
      result = result.filter((log) => log.userId === filter.value.userId)
    }

    if (filter.value.searchQuery && filter.value.searchQuery.trim() !== '') {
      const query = filter.value.searchQuery.toLowerCase()
      result = result.filter(
        (log) =>
          (log.entityName && log.entityName.toLowerCase().includes(query)) ||
          log.userName.toLowerCase().includes(query),
      )
    }

    if (filter.value.dateRange) {
      const now = new Date()
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      let startDate: Date
      let endDate: Date = new Date(today.getTime() + 24 * 60 * 60 * 1000)

      switch (filter.value.dateRange) {
        case 'today':
          startDate = today
          break
        case 'yesterday': {
          startDate = new Date(today)
          startDate.setDate(startDate.getDate() - 1)
          endDate = new Date(today)
          break
        }
        case 'this_week': {
          startDate = new Date(today)
          const dayOfWeek = startDate.getDay()
          const diff = dayOfWeek === 0 ? 6 : dayOfWeek - 1
          startDate.setDate(startDate.getDate() - diff)
          break
        }
        case 'this_month': {
          startDate = new Date(today.getFullYear(), today.getMonth(), 1)
          break
        }
        case 'custom': {
          if (customDateRange.value.startDate) {
            startDate = new Date(customDateRange.value.startDate)
          } else {
            startDate = new Date(0)
          }
          if (customDateRange.value.endDate) {
            endDate = new Date(customDateRange.value.endDate)
            endDate.setDate(endDate.getDate() + 1)
          }
          break
        }
        default:
          startDate = new Date(0)
      }

      result = result.filter((log) => {
        const logDate = new Date(log.createdAt)
        return logDate >= startDate && logDate < endDate
      })
    }

    result.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())

    return result
  })

  const groupedByDate = computed<[string, ActivityLog[]][]>(() => {
    const grouped = new Map<string, ActivityLog[]>()
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)

    filteredLogs.value.forEach((log) => {
      const logDate = new Date(log.createdAt)
      const logDateOnly = new Date(logDate.getFullYear(), logDate.getMonth(), logDate.getDate())

      let dateKey: string
      if (logDateOnly.getTime() === today.getTime()) {
        dateKey = '오늘'
      } else if (logDateOnly.getTime() === yesterday.getTime()) {
        dateKey = '어제'
      } else {
        const month = logDate.getMonth() + 1
        const date = logDate.getDate()
        const dayOfWeek = ['일', '월', '화', '수', '목', '금', '토'][logDate.getDay()]
        dateKey = `${month}월 ${date}일 ${dayOfWeek}요일`
      }

      if (!grouped.has(dateKey)) {
        grouped.set(dateKey, [])
      }
      grouped.get(dateKey)!.push(log)
    })

    return Array.from(grouped.entries())
  })

  const actionCounts = computed<Record<ActivityAction | 'all', number>>(() => {
    const todayStart = new Date()
    todayStart.setHours(0, 0, 0, 0)

    const todayLogs = logs.value.filter(
      (log) => new Date(log.createdAt) >= todayStart,
    )

    const counts: Record<string, number> = { all: todayLogs.length }

    const actions: ActivityAction[] = [
      'upload', 'publish', 'edit', 'delete', 'schedule',
      'ai_generate', 'channel_connect', 'channel_disconnect',
      'settings_change', 'team_invite', 'team_remove',
      'login', 'credit_purchase',
    ]

    actions.forEach((action) => {
      counts[action] = todayLogs.filter((log) => log.action === action).length
    })

    return counts as Record<ActivityAction | 'all', number>
  })

  const uniqueUsers = computed(() => {
    const usersMap = new Map<string, { id: string; name: string }>()
    logs.value.forEach((log) => {
      if (!usersMap.has(log.userId)) {
        usersMap.set(log.userId, { id: log.userId, name: log.userName })
      }
    })
    return Array.from(usersMap.values())
  })

  return {
    // State
    logs,
    isLoading,
    filter,
    customDateRange,
    // Actions
    fetchLogs,
    filterByAction,
    filterByDate,
    filterByUser,
    setSearchQuery,
    resetFilters,
    exportLogs,
    // Getters
    filteredLogs,
    groupedByDate,
    actionCounts,
    uniqueUsers,
  }
})
