import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

/**
 * 게시물 한 건의 지표. **`null` 은 측정하지 않았다는 뜻이며 0 이 아니다.**
 *
 * Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다.
 * 스냅샷이 아직 없는 경우도 `null` 이다. 0 으로 채우면 "공유 0회" 라는 성과 보고가 되고,
 * 그 위에서 보상 판단이 이뤄진다.
 */
export interface PostMetricResponse {
  campaignPostId: number
  platform: string
  postStatus: string
  views: number | null
  likes: number | null
  comments: number | null
  shares: number | null
  capturedAt: string | null
  /** 이 게시물에서 측정하지 못한 지표 이름들. */
  unavailableMetrics: string[]
}

/**
 * 캠페인 합계. 합계가 `null` 이면 **그 지표를 측정한 게시물이 하나도 없다.**
 */
export interface CampaignAnalyticsResponse {
  campaignId: number
  totalViews: number | null
  totalLikes: number | null
  totalComments: number | null
  totalShares: number | null
  lastSyncedAt: string | null
  posts: PostMetricResponse[]
  /** 지표별로 합계에 실제로 들어간 게시물 수. 표본 크기를 함께 봐야 한다. */
  measuredPostCounts: Record<string, number>
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

export interface AuditEventResponse {
  id: number
  actorId: number
  action: string
  resourceType: string | null
  resourceId: number | null
  detail: string | null
  createdAt: string | null
}

export interface AuditEventListResponse {
  items: AuditEventResponse[]
  totalElements: number
  page: number
  size: number
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

  listAuditEvents(workspaceId: number, campaignId: number) {
    return apiClient
      .get<ResData<AuditEventListResponse>>(`${wsBase(workspaceId)}/campaigns/${campaignId}/audit-events`)
      .then(unwrapResponse)
  },
}
