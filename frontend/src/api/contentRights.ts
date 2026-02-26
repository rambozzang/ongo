import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ContentRight,
  RightsAlert,
  AlternativeAsset,
  RightsSummary,
  CreateRightRequest,
} from '@/types/contentRights'

export const contentRightsApi = {
  getSummary: () =>
    apiClient.get<ResData<RightsSummary>>('/content-rights/summary').then(unwrapResponse),

  getAll: () =>
    apiClient.get<ResData<ContentRight[]>>('/content-rights').then(unwrapResponse),

  create: (request: CreateRightRequest) =>
    apiClient.post<ResData<ContentRight>>('/content-rights', request).then(unwrapResponse),

  update: (id: number, data: Partial<ContentRight>) =>
    apiClient.put<ResData<ContentRight>>(`/content-rights/${id}`, data).then(unwrapResponse),

  delete: (id: number) =>
    apiClient.delete<ResData<void>>(`/content-rights/${id}`).then(unwrapResponse),

  getAlerts: () =>
    apiClient.get<ResData<RightsAlert[]>>('/content-rights/alerts').then(unwrapResponse),

  markAlertRead: (alertId: number) =>
    apiClient.put<ResData<void>>(`/content-rights/alerts/${alertId}/read`).then(unwrapResponse),

  findAlternatives: (rightId: number) =>
    apiClient.get<ResData<AlternativeAsset[]>>(`/content-rights/${rightId}/alternatives`).then(unwrapResponse),
}
