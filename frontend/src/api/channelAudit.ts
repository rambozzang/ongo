import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { ChannelAuditReport, ChannelAuditListResponse } from '@/types/channelAudit'

export const channelAuditApi = {
  generateAudit() {
    return apiClient
      .post<ResData<ChannelAuditReport>>('/reports/channel-audit')
      .then(unwrapResponse)
  },

  getAudits(page = 0, size = 10) {
    return apiClient
      .get<ResData<ChannelAuditListResponse>>('/reports/channel-audit', { params: { page, size } })
      .then(unwrapResponse)
  },

  getAuditDetail(id: number) {
    return apiClient
      .get<ResData<ChannelAuditReport>>(`/reports/channel-audit/${id}`)
      .then(unwrapResponse)
  },
}
