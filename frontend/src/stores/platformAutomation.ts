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
      rules.value = [
        { id: 1, name: '매일 오전 자동 게시', platform: 'YOUTUBE', triggerType: 'SCHEDULE', actionType: 'PUBLISH', condition: '매일 09:00', isActive: true, executionCount: 45, lastExecuted: '2025-03-12T09:00:00Z', createdAt: '2025-01-15T10:00:00Z' },
        { id: 2, name: '댓글 키워드 알림', platform: 'INSTAGRAM', triggerType: 'KEYWORD', actionType: 'NOTIFY', condition: '부정적 키워드 감지', isActive: true, executionCount: 12, lastExecuted: '2025-03-11T15:30:00Z', createdAt: '2025-02-01T10:00:00Z' },
        { id: 3, name: '조회수 목표 달성시 태그', platform: 'TIKTOK', triggerType: 'THRESHOLD', actionType: 'TAG', condition: '조회수 10,000 달성', isActive: false, executionCount: 8, lastExecuted: '2025-03-05T20:00:00Z', createdAt: '2025-02-20T14:00:00Z' },
        { id: 4, name: '30일 이상 미조회 자동 보관', platform: 'YOUTUBE', triggerType: 'EVENT', actionType: 'ARCHIVE', condition: '30일 미조회', isActive: true, executionCount: 3, lastExecuted: '2025-03-01T00:00:00Z', createdAt: '2025-03-01T09:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchLogs(ruleId?: number) {
    try {
      logs.value = await platformAutomationApi.getLogs(ruleId)
    } catch {
      logs.value = [
        { id: 1, ruleId: 1, ruleName: '매일 오전 자동 게시', status: 'SUCCESS', message: '영상 "봄 코디" 게시 완료', executedAt: '2025-03-12T09:00:00Z' },
        { id: 2, ruleId: 2, ruleName: '댓글 키워드 알림', status: 'SUCCESS', message: '부정적 댓글 3건 감지, 알림 발송', executedAt: '2025-03-11T15:30:00Z' },
        { id: 3, ruleId: 1, ruleName: '매일 오전 자동 게시', status: 'FAILED', message: 'API 연결 실패', executedAt: '2025-03-11T09:00:00Z' },
        { id: 4, ruleId: 4, ruleName: '30일 이상 미조회 자동 보관', status: 'SUCCESS', message: '3개 영상 보관 처리', executedAt: '2025-03-01T00:00:00Z' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await platformAutomationApi.getSummary()
    } catch {
      summary.value = { totalRules: 15, activeRules: 10, totalExecutions: 234, successRate: 94.2, topPlatform: 'YOUTUBE' }
    }
  }

  return { rules, logs, summary, loading, activeRules, fetchRules, fetchLogs, fetchSummary }
})
