import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface PostMetricResponse {
  campaignPostId: number
  platform: string
  postStatus: string
  views: number
  likes: number
  comments: number
  shares: number
  capturedAt: string | null
}

export interface CampaignAnalyticsResponse {
  campaignId: number
  totalViews: number
  totalLikes: number
  totalComments: number
  totalShares: number
  lastSyncedAt: string | null
  posts: PostMetricResponse[]
}

export interface ParticipantRewardResponse {
  participantId: number
  creatorId: number
  agreedReward: number
  rewardId: number | null
  baseAmount: number
  bonusAmount: number
  totalAmount: number
  status: string
  note: string | null
  confirmedAt: string | null
}

export interface ParticipantRewardListResponse {
  items: ParticipantRewardResponse[]
  totalBudget: number
  settledTotal: number
  remaining: number
}

export interface UpdateRewardRequest {
  baseAmount: number
  bonusAmount?: number
  note?: string | null
}

const wsBase = (workspaceId: number) => `/workspaces/${workspaceId}/ugc`

export const ugcRewardApi = {
  getAnalytics(workspaceId: number, campaignId: number) {
    return apiClient
      .get<ResData<CampaignAnalyticsResponse>>(`${wsBase(workspaceId)}/campaigns/${campaignId}/analytics`)
      .then(unwrapResponse)
  },

  recordMetric(workspaceId: number, campaignPostId: number, request: { views: number; likes: number; comments: number; shares: number }) {
    return apiClient
      .post<ResData<PostMetricResponse>>(`${wsBase(workspaceId)}/campaign-posts/${campaignPostId}/metrics`, request)
      .then(unwrapResponse)
  },

  listParticipants(workspaceId: number, campaignId: number) {
    return apiClient
      .get<ResData<ParticipantRewardListResponse>>(`${wsBase(workspaceId)}/campaigns/${campaignId}/participants`)
      .then(unwrapResponse)
  },

  updateReward(workspaceId: number, participantId: number, request: UpdateRewardRequest) {
    return apiClient
      .put<ResData<ParticipantRewardResponse>>(`${wsBase(workspaceId)}/participants/${participantId}/reward`, request)
      .then(unwrapResponse)
  },

  confirmReward(workspaceId: number, participantId: number) {
    return apiClient
      .post<ResData<ParticipantRewardResponse>>(`${wsBase(workspaceId)}/participants/${participantId}/reward/confirm`)
      .then(unwrapResponse)
  },

  markPaid(workspaceId: number, participantId: number) {
    return apiClient
      .post<ResData<ParticipantRewardResponse>>(`${wsBase(workspaceId)}/participants/${participantId}/reward/mark-paid`)
      .then(unwrapResponse)
  },

  downloadCsv(workspaceId: number, campaignId: number): Promise<Blob> {
    return apiClient
      .get(`${wsBase(workspaceId)}/campaigns/${campaignId}/rewards.csv`, { responseType: 'blob' })
      .then((r) => r.data as Blob)
  },
}
