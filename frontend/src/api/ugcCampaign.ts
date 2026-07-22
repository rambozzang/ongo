import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export type CampaignStatus =
  | 'DRAFT'
  | 'RECRUITING'
  | 'ACTIVE'
  | 'PAUSED'
  | 'COMPLETED'
  | 'CANCELLED'

export interface CampaignResponse {
  id: number
  workspaceId: number
  name: string
  description: string | null
  status: CampaignStatus
  objective: string
  totalBudget: number
  currency: string
  fixedRewardPerCreator: number
  startAt: string | null
  endAt: string | null
  createdBy: number
  createdAt: string | null
  updatedAt: string | null
  version: number
}

export interface CampaignListResponse {
  items: CampaignResponse[]
  totalElements: number
  page: number
  size: number
}

export interface PlaybookStepDto {
  sortOrder: number
  stepType: string
  title: string
  instruction: string | null
  exampleUrl: string | null
  required: boolean
}

export interface PlaybookResponse {
  id: number
  campaignId: number
  title: string
  summary: string | null
  contentType: string
  revision: number
  steps: PlaybookStepDto[]
}

export interface CampaignDetailResponse {
  campaign: CampaignResponse
  playbook: PlaybookResponse | null
}

export interface CreateCampaignRequest {
  name: string
  description?: string | null
  objective?: string
  totalBudget?: number
  currency?: string
  fixedRewardPerCreator?: number
  startAt?: string | null
  endAt?: string | null
}

export type UpdateCampaignRequest = Partial<CreateCampaignRequest>

export interface PlaybookStepRequest {
  stepType?: string
  title: string
  instruction?: string | null
  exampleUrl?: string | null
  required?: boolean
}

export interface UpsertPlaybookRequest {
  title: string
  summary?: string | null
  contentType?: string
  steps: PlaybookStepRequest[]
}

export interface CampaignListParams {
  page?: number
  size?: number
  status?: string
  query?: string
}

const base = (workspaceId: number) => `/workspaces/${workspaceId}/ugc/campaigns`

export const ugcCampaignApi = {
  list(workspaceId: number, params?: CampaignListParams) {
    return apiClient
      .get<ResData<CampaignListResponse>>(base(workspaceId), { params })
      .then(unwrapResponse)
  },

  get(workspaceId: number, campaignId: number) {
    return apiClient
      .get<ResData<CampaignDetailResponse>>(`${base(workspaceId)}/${campaignId}`)
      .then(unwrapResponse)
  },

  create(workspaceId: number, request: CreateCampaignRequest) {
    return apiClient
      .post<ResData<CampaignDetailResponse>>(base(workspaceId), request)
      .then(unwrapResponse)
  },

  update(workspaceId: number, campaignId: number, request: UpdateCampaignRequest) {
    return apiClient
      .patch<ResData<CampaignDetailResponse>>(`${base(workspaceId)}/${campaignId}`, request)
      .then(unwrapResponse)
  },

  publish(workspaceId: number, campaignId: number) {
    return apiClient
      .post<ResData<CampaignDetailResponse>>(`${base(workspaceId)}/${campaignId}/publish`)
      .then(unwrapResponse)
  },

  pause(workspaceId: number, campaignId: number) {
    return apiClient
      .post<ResData<CampaignDetailResponse>>(`${base(workspaceId)}/${campaignId}/pause`)
      .then(unwrapResponse)
  },

  complete(workspaceId: number, campaignId: number) {
    return apiClient
      .post<ResData<CampaignDetailResponse>>(`${base(workspaceId)}/${campaignId}/complete`)
      .then(unwrapResponse)
  },

  upsertPlaybook(workspaceId: number, campaignId: number, request: UpsertPlaybookRequest) {
    return apiClient
      .put<ResData<PlaybookResponse>>(`${base(workspaceId)}/${campaignId}/playbook`, request)
      .then(unwrapResponse)
  },
}
