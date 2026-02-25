import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  LiveDashboardState,
} from '@/types/liveDashboard'
import { liveDashboardApi } from '@/api/liveDashboard'

function generateMockState(): LiveDashboardState {
  const now = new Date()
  const makeHistory = (base: number, variance: number) => {
    return Array.from({ length: 24 }, (_, i) => ({
      timestamp: new Date(now.getTime() - (23 - i) * 3600000).toISOString(),
      value: Math.round(base + (Math.random() - 0.5) * variance),
    }))
  }

  return {
    metrics: [
      { type: 'VIEWS', currentValue: 12450, previousValue: 11200, changePercent: 11.2, trend: 'UP', history: makeHistory(500, 200) },
      { type: 'SUBSCRIBERS', currentValue: 8234, previousValue: 8180, changePercent: 0.66, trend: 'UP', history: makeHistory(3, 2) },
      { type: 'LIKES', currentValue: 3820, previousValue: 3650, changePercent: 4.7, trend: 'UP', history: makeHistory(150, 60) },
      { type: 'COMMENTS', currentValue: 428, previousValue: 395, changePercent: 8.4, trend: 'UP', history: makeHistory(18, 8) },
      { type: 'WATCH_TIME', currentValue: 48200, previousValue: 45600, changePercent: 5.7, trend: 'UP', history: makeHistory(2000, 500) },
      { type: 'REVENUE', currentValue: 285000, previousValue: 262000, changePercent: 8.8, trend: 'UP', history: makeHistory(12000, 3000) },
    ],
    alerts: [
      { id: 1, type: 'SPIKE', title: '조회수 급증', description: '최근 1시간 조회수가 평소의 3배입니다', metric: 'VIEWS', value: 1500, threshold: 500, createdAt: new Date(Date.now() - 1800000).toISOString(), read: false },
      { id: 2, type: 'MILESTONE', title: '구독자 8,000명 돌파', description: '구독자 8,000명을 달성했습니다!', metric: 'SUBSCRIBERS', value: 8000, threshold: 8000, createdAt: new Date(Date.now() - 7200000).toISOString(), read: true },
      { id: 3, type: 'VIRAL', title: '바이럴 콘텐츠 감지', description: '"서울 맛집 TOP 5" 영상이 빠르게 확산 중입니다', metric: 'VIEWS', value: 8500, threshold: 5000, createdAt: new Date(Date.now() - 3600000).toISOString(), read: false },
    ],
    activePlatforms: ['youtube', 'tiktok', 'instagram'],
    lastUpdated: now.toISOString(),
    isConnected: true,
  }
}

export const useLiveDashboardStore = defineStore('liveDashboard', () => {
  const state = ref<LiveDashboardState | null>(null)
  const loading = ref(false)
  const refreshInterval = ref<ReturnType<typeof setInterval> | null>(null)

  const metrics = computed(() => state.value?.metrics ?? [])
  const alerts = computed(() => state.value?.alerts ?? [])
  const unreadAlerts = computed(() => alerts.value.filter((a) => !a.read))
  const isConnected = computed(() => state.value?.isConnected ?? false)

  async function fetchState() {
    loading.value = true
    try {
      state.value = await liveDashboardApi.getState()
    } catch {
      state.value = generateMockState()
    } finally {
      loading.value = false
    }
  }

  async function markAlertRead(alertId: number) {
    try {
      await liveDashboardApi.markAlertRead(alertId)
    } catch {
      // 로컬 업데이트
    }
    if (state.value) {
      const alert = state.value.alerts.find((a) => a.id === alertId)
      if (alert) alert.read = true
    }
  }

  function startAutoRefresh(intervalMs = 30000) {
    stopAutoRefresh()
    refreshInterval.value = setInterval(fetchState, intervalMs)
  }

  function stopAutoRefresh() {
    if (refreshInterval.value) {
      clearInterval(refreshInterval.value)
      refreshInterval.value = null
    }
  }

  return {
    state,
    loading,
    metrics,
    alerts,
    unreadAlerts,
    isConnected,
    fetchState,
    markAlertRead,
    startAutoRefresh,
    stopAutoRefresh,
  }
})
