import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AgencyCreator,
  AgencyKpi,
  CreatorComparison,
  AgencyScheduleItem,
  AgencyActivity,
  ClientPortalLink,
  ClientPortalData,
} from '@/types/agency'

export const agencyApi = {
  getKpi() {
    return apiClient.get<ResData<AgencyKpi>>('/agency/kpi').then(unwrapResponse)
  },

  listCreators() {
    return apiClient.get<ResData<AgencyCreator[]>>('/agency/creators').then(unwrapResponse)
  },

  getCreatorComparison(creatorIds: number[], period: string) {
    return apiClient
      .get<ResData<CreatorComparison[]>>('/agency/creators/comparison', {
        params: { creatorIds: creatorIds.join(','), period },
      })
      .then(unwrapResponse)
  },

  listSchedules() {
    return apiClient.get<ResData<AgencyScheduleItem[]>>('/agency/schedules').then(unwrapResponse)
  },

  listActivities() {
    return apiClient.get<ResData<AgencyActivity[]>>('/agency/activities').then(unwrapResponse)
  },

  listClientLinks() {
    return apiClient.get<ResData<ClientPortalLink[]>>('/agency/client-links').then(unwrapResponse)
  },

  createClientLink(creatorId: number) {
    return apiClient
      .post<ResData<ClientPortalLink>>('/agency/client-links', { creatorId })
      .then(unwrapResponse)
  },

  revokeClientLink(linkId: number) {
    return apiClient
      .delete<ResData<void>>(`/agency/client-links/${linkId}`)
      .then(unwrapResponse)
  },

  getClientPortalData(token: string) {
    return apiClient
      .get<ResData<ClientPortalData>>(`/portal/${token}`)
      .then(unwrapResponse)
  },
}
