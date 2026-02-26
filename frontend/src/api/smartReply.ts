import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  SmartReplyRule,
  SmartReplySuggestion,
  SmartReplyStats,
  SmartReplyConfig,
} from '@/types/smartReply'

export const smartReplyApi = {
  getSuggestions: () =>
    apiClient.get<ResData<SmartReplySuggestion[]>>('/smart-reply/suggestions').then(unwrapResponse),

  sendReply: (suggestionId: string, text: string) =>
    apiClient.post<ResData<void>>('/smart-reply/send', { suggestionId, text }).then(unwrapResponse),

  dismissSuggestion: (suggestionId: string) =>
    apiClient.delete<ResData<void>>(`/smart-reply/suggestions/${suggestionId}`).then(unwrapResponse),

  getRules: () =>
    apiClient.get<ResData<SmartReplyRule[]>>('/smart-reply/rules').then(unwrapResponse),

  createRule: (rule: Omit<SmartReplyRule, 'id' | 'replyCount' | 'lastUsed'>) =>
    apiClient.post<ResData<SmartReplyRule>>('/smart-reply/rules', rule).then(unwrapResponse),

  updateRule: (id: number, rule: Partial<SmartReplyRule>) =>
    apiClient.put<ResData<SmartReplyRule>>(`/smart-reply/rules/${id}`, rule).then(unwrapResponse),

  deleteRule: (id: number) =>
    apiClient.delete<ResData<void>>(`/smart-reply/rules/${id}`).then(unwrapResponse),

  getStats: () =>
    apiClient.get<ResData<SmartReplyStats>>('/smart-reply/stats').then(unwrapResponse),

  getConfig: () =>
    apiClient.get<ResData<SmartReplyConfig>>('/smart-reply/config').then(unwrapResponse),

  updateConfig: (config: Partial<SmartReplyConfig>) =>
    apiClient.put<ResData<SmartReplyConfig>>('/smart-reply/config', config).then(unwrapResponse),
}
