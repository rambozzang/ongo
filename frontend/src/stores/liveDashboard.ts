import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  LiveDashboardState,
} from '@/types/liveDashboard'
import { liveDashboardApi } from '@/api/liveDashboard'

export const useLiveDashboardStore = defineStore('liveDashboard', () => {
  const state = ref<LiveDashboardState | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const refreshInterval = ref<ReturnType<typeof setInterval> | null>(null)

  const metrics = computed(() => state.value?.metrics ?? [])
  const alerts = computed(() => state.value?.alerts ?? [])
  const unreadAlerts = computed(() => alerts.value.filter((a) => !a.read))
  const isConnected = computed(() => state.value?.isConnected ?? false)

  async function fetchState() {
    loading.value = true
    error.value = null
    try {
      state.value = await liveDashboardApi.getState()
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    } finally {
      loading.value = false
    }
  }

  async function markAlertRead(alertId: number) {
    try {
      await liveDashboardApi.markAlertRead(alertId)
    } catch {
      // API 실패 시에도 로컬 업데이트 진행
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
    error,
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
