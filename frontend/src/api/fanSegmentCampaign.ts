import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { SegmentCampaign, FanSegment, CampaignSummary } from '@/types/fanSegmentCampaign'

export const fanSegmentCampaignApi = {
  getSummary: () =>
    apiClient.get<ResData<CampaignSummary>>('/fan-segment-campaigns/summary').then(unwrapResponse),

  getCampaigns: () =>
    apiClient.get<ResData<SegmentCampaign[]>>('/fan-segment-campaigns').then(unwrapResponse),

  getSegments: () =>
    apiClient.get<ResData<FanSegment[]>>('/fan-segment-campaigns/segments').then(unwrapResponse),

  createCampaign: (campaign: Omit<SegmentCampaign, 'id' | 'sentCount' | 'openRate' | 'clickRate' | 'createdAt'>) =>
    apiClient.post<ResData<SegmentCampaign>>('/fan-segment-campaigns', campaign).then(unwrapResponse),

  deleteCampaign: (id: number) =>
    apiClient.delete<ResData<void>>(`/fan-segment-campaigns/${id}`).then(unwrapResponse),
}
