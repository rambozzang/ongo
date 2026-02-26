import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { AutomationRule, AutomationLog, PlatformAutomationSummary } from '@/types/platformAutomation'

export const platformAutomationApi = {
  getRules: () =>
    apiClient.get<ResData<AutomationRule[]>>('/api/v1/platform-automation').then(unwrapResponse),
  getRule: (id: number) =>
    apiClient.get<ResData<AutomationRule>>(`/api/v1/platform-automation/${id}`).then(unwrapResponse),
  createRule: (data: Partial<AutomationRule>) =>
    apiClient.post<ResData<AutomationRule>>('/api/v1/platform-automation', data).then(unwrapResponse),
  toggleRule: (id: number) =>
    apiClient.put<ResData<AutomationRule>>(`/api/v1/platform-automation/${id}/toggle`).then(unwrapResponse),
  getLogs: (ruleId?: number) =>
    apiClient.get<ResData<AutomationLog[]>>('/api/v1/platform-automation/logs', { params: { ruleId } }).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<PlatformAutomationSummary>>('/api/v1/platform-automation/summary').then(unwrapResponse),
}

export default platformAutomationApi
