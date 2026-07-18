import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export type ApplicationStatus = 'APPLIED' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN'

export interface InviteResponse {
  id: number
  campaignId: number
  token: string | null
  expiresAt: string | null
  maxUses: number | null
  usedCount: number
  active: boolean
}

export interface ApplicationResponse {
  id: number
  campaignId: number
  creatorId: number
  message: string | null
  portfolioUrl: string | null
  status: ApplicationStatus
  decidedBy: number | null
  decidedAt: string | null
  createdAt: string | null
}

export interface ApplicationListResponse {
  items: ApplicationResponse[]
  totalElements: number
  page: number
  size: number
}

export interface PublicCampaignResponse {
  campaignId: number
  name: string
  description: string | null
  objective: string
  status: string
  startAt: string | null
  endAt: string | null
  currency: string
  fixedRewardPerCreator: number
  playbookTitle: string | null
  playbookSummary: string | null
  alreadyApplied: boolean
}

export interface MyApplicationResponse {
  application: ApplicationResponse
  campaignName: string
  campaignStatus: string
  startAt: string | null
  endAt: string | null
}

export interface MyApplicationListResponse {
  items: MyApplicationResponse[]
  totalElements: number
  page: number
  size: number
}

export interface CreateInviteRequest {
  expiresInDays?: number | null
  maxUses?: number | null
}

export interface ApplyRequest {
  message?: string | null
  portfolioUrl?: string | null
}

const wsBase = (workspaceId: number) => `/workspaces/${workspaceId}/ugc`

export const ugcParticipationApi = {
  // 브랜드
  createInvite(workspaceId: number, campaignId: number, request: CreateInviteRequest) {
    return apiClient
      .post<ResData<InviteResponse>>(`${wsBase(workspaceId)}/campaigns/${campaignId}/invites`, request)
      .then(unwrapResponse)
  },

  listApplications(
    workspaceId: number,
    campaignId: number,
    params?: { status?: string; page?: number; size?: number },
  ) {
    return apiClient
      .get<ResData<ApplicationListResponse>>(`${wsBase(workspaceId)}/campaigns/${campaignId}/applications`, { params })
      .then(unwrapResponse)
  },

  accept(workspaceId: number, applicationId: number) {
    return apiClient
      .post<ResData<ApplicationResponse>>(`${wsBase(workspaceId)}/applications/${applicationId}/accept`)
      .then(unwrapResponse)
  },

  reject(workspaceId: number, applicationId: number) {
    return apiClient
      .post<ResData<ApplicationResponse>>(`${wsBase(workspaceId)}/applications/${applicationId}/reject`)
      .then(unwrapResponse)
  },

  // 크리에이터
  viewInvite(token: string) {
    return apiClient
      .get<ResData<PublicCampaignResponse>>(`/ugc/invites/${token}`)
      .then(unwrapResponse)
  },

  apply(token: string, request: ApplyRequest) {
    return apiClient
      .post<ResData<ApplicationResponse>>(`/ugc/invites/${token}/applications`, request)
      .then(unwrapResponse)
  },

  myApplications(params?: { page?: number; size?: number }) {
    return apiClient
      .get<ResData<MyApplicationListResponse>>('/ugc/me/applications', { params })
      .then(unwrapResponse)
  },
}
