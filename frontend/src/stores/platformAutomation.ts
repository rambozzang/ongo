import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { platformAutomationApi } from '@/api/platformAutomation'
import type { AutomationRule, AutomationLog, PlatformAutomationSummary } from '@/types/platformAutomation'

export const usePlatformAutomationStore = defineStore('platformAutomation', () => {
  const rules = ref<AutomationRule[]>([])
  const logs = ref<AutomationLog[]>([])
  const summary = ref<PlatformAutomationSummary | null>(null)
  const loading = ref(false)

  const activeRules = computed(() => rules.value.filter(r => r.isActive))

  async function fetchRules() {
    loading.value = true
    try {
      rules.value = await platformAutomationApi.getRules()
    } catch {
      rules.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchLogs(ruleId?: number) {
    try {
      logs.value = await platformAutomationApi.getLogs(ruleId)
    } catch {
      logs.value = []
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await platformAutomationApi.getSummary()
    } catch {
      summary.value = null
    }
  }

  return { rules, logs, summary, loading, activeRules, fetchRules, fetchLogs, fetchSummary }
})
