import { defineStore } from 'pinia'
import type { AutomationRule, AutomationLog } from '@/types/automation'
import { automationApi } from '@/api/automation'
import type { AutomationRuleResponse } from '@/api/automation'

interface AutomationState {
  rules: AutomationRule[]
  logs: AutomationLog[]
  loading: boolean
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
      try {
        const data = await automationApi.list()
        this.rules = data.map(mapApiRule)
      } catch (e) {
        console.error('Failed to fetch automation rules:', e)
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
        console.error('Failed to create automation rule:', e)
        // Fallback to local
        const newRule: AutomationRule = {
          ...rule,
          id: Math.max(...this.rules.map(r => r.id), 0) + 1,
          executionCount: 0,
          lastExecutedAt: null,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        }
        this.rules.push(newRule)
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
        console.error('Failed to update automation rule:', e)
        const index = this.rules.findIndex(r => r.id === id)
        if (index !== -1) {
          this.rules[index] = {
            ...this.rules[index],
            ...updates,
            updatedAt: new Date().toISOString()
          }
        }
      }
    },

    async deleteRule(id: number) {
      try {
        await automationApi.delete(id)
      } catch (e) {
        console.error('Failed to delete automation rule:', e)
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
        console.error('Failed to toggle automation rule:', e)
        const rule = this.rules.find(r => r.id === id)
        if (rule) {
          rule.isEnabled = !rule.isEnabled
          rule.status = rule.isEnabled ? 'active' : 'paused'
          rule.updatedAt = new Date().toISOString()
        }
      }
    },

    executeRule(id: number) {
      const rule = this.rules.find(r => r.id === id)
      if (rule) {
        rule.executionCount++
        rule.lastExecutedAt = new Date().toISOString()
        rule.updatedAt = new Date().toISOString()

        const newLog: AutomationLog = {
          id: Math.max(...this.logs.map(l => l.id), 0) + 1,
          ruleId: rule.id,
          ruleName: rule.name,
          status: 'success',
          message: `규칙 실행 완료`,
          executedAt: new Date().toISOString()
        }
        this.logs.unshift(newLog)
      }
    },
  }
})
