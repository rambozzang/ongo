import { defineStore } from 'pinia'
import type { AutomationRule, AutomationLog } from '@/types/automation'
import { automationApi } from '@/api/automation'
import { useNotificationStore } from '@/stores/notification'
import type { AutomationRuleResponse } from '@/api/automation'

interface AutomationState {
  rules: AutomationRule[]
  logs: AutomationLog[]
  loading: boolean
  error: string | null
}

function mapApiRule(r: AutomationRuleResponse): AutomationRule {
  return {
    id: r.id,
    name: r.name,
    description: r.description ?? '',
    trigger: {
      type: r.triggerType as AutomationRule['trigger']['type'],
      config: (r.triggerConfig ?? {}) as Record<string, string | number | boolean>,
    },
    actions: [
      {
        type: r.actionType as AutomationRule['actions'][0]['type'],
        config: (r.actionConfig ?? {}) as Record<string, string | number | boolean>,
      },
    ],
    status: r.isActive ? 'active' : 'paused',
    executionCount: r.executionCount,
    lastExecutedAt: r.lastTriggeredAt,
    createdAt: r.createdAt ?? new Date().toISOString(),
    updatedAt: r.updatedAt ?? new Date().toISOString(),
    isEnabled: r.isActive,
  }
}

export const useAutomationStore = defineStore('automation', {
  state: (): AutomationState => ({
    rules: [],
    logs: [],
    loading: false,
    error: null,
  }),

  getters: {
    activeRules: (state): AutomationRule[] => {
      return state.rules.filter(rule => rule.status === 'active' && rule.isEnabled)
    },

    pausedRules: (state): AutomationRule[] => {
      return state.rules.filter(rule => rule.status === 'paused' || !rule.isEnabled)
    },

    rulesByTriggerType: (state) => {
      return (triggerType: string): AutomationRule[] => {
        return state.rules.filter(rule => rule.trigger.type === triggerType)
      }
    },

    recentLogs: (state): AutomationLog[] => {
      return [...state.logs].sort((a, b) =>
        new Date(b.executedAt).getTime() - new Date(a.executedAt).getTime()
      )
    }
  },

  actions: {
    async fetchRules() {
      this.loading = true
      this.error = null
      try {
        const data = await automationApi.list()
        this.rules = data.map(mapApiRule)
      } catch (e) {
        this.error = e instanceof Error ? e.message : '자동화 규칙을 불러오지 못했습니다.'
        useNotificationStore().error('자동화 처리 중 오류가 발생했습니다')
      } finally {
        this.loading = false
      }
    },

    async createRule(rule: Omit<AutomationRule, 'id' | 'createdAt' | 'updatedAt' | 'executionCount' | 'lastExecutedAt'>) {
      try {
        const data = await automationApi.create({
          name: rule.name,
          description: rule.description,
          triggerType: rule.trigger.type,
          triggerConfig: rule.trigger.config,
          actionType: rule.actions[0]?.type ?? 'SEND_NOTIFICATION',
          actionConfig: rule.actions[0]?.config ?? {},
          isActive: rule.isEnabled,
        })
        this.rules.push(mapApiRule(data))
      } catch (e) {
        useNotificationStore().error('자동화 처리 중 오류가 발생했습니다')
        throw e
      }
    },

    async updateRule(id: number, updates: Partial<AutomationRule>) {
      try {
        const data = await automationApi.update(id, {
          name: updates.name,
          description: updates.description,
          triggerType: updates.trigger?.type,
          triggerConfig: updates.trigger?.config,
          actionType: updates.actions?.[0]?.type,
          actionConfig: updates.actions?.[0]?.config,
        })
        const index = this.rules.findIndex(r => r.id === id)
        if (index !== -1) {
          this.rules[index] = mapApiRule(data)
        }
      } catch (e) {
        useNotificationStore().error('자동화 처리 중 오류가 발생했습니다')
        throw e
      }
    },

    async deleteRule(id: number) {
      try {
        await automationApi.delete(id)
      } catch (e) {
        useNotificationStore().error('자동화 처리 중 오류가 발생했습니다')
        throw e
      }
      const index = this.rules.findIndex(r => r.id === id)
      if (index !== -1) {
        this.rules.splice(index, 1)
      }
    },

    async toggleRule(id: number) {
      try {
        const data = await automationApi.toggle(id)
        const index = this.rules.findIndex(r => r.id === id)
        if (index !== -1) {
          this.rules[index] = mapApiRule(data)
        }
      } catch (e) {
        useNotificationStore().error('자동화 처리 중 오류가 발생했습니다')
        throw e
      }
    },
  }
})
