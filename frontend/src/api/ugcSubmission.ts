import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export type SubmissionStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'CHANGES_REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'PUBLISHING'
  | 'PUBLISHED'
  | 'PUBLISH_FAILED'

export interface SubmissionAssetDto {
  assetType: string
  resourceType?: string | null
  resourceId?: number | null
  externalUrl?: string | null
}

export interface SubmissionResponse {
  id: number
  campaignId: number
  creatorId: number
  revision: number
  caption: string | null
  status: SubmissionStatus
  submittedAt: string | null
  approvedAt: string | null
  assets: SubmissionAssetDto[]
  createdAt: string | null
  updatedAt: string | null
}

export interface SubmissionListResponse {
  items: SubmissionResponse[]
  totalElements: number
  page: number
  size: number
}

export interface ReviewResponse {
  id: number
  reviewerId: number
  decision: string
  comment: string | null
  createdAt: string | null
}

export interface SubmissionDetailResponse {
  submission: SubmissionResponse
  reviews: ReviewResponse[]
}

export interface CreateSubmissionRequest {
  caption?: string | null
  assets: SubmissionAssetDto[]
}

export interface ReviewDecisionRequest {
  comment?: string | null
}

const wsBase = (workspaceId: number) => `/workspaces/${workspaceId}/ugc`

export const ugcSubmissionApi = {
  // 크리에이터
  saveDraft(campaignId: number, request: CreateSubmissionRequest) {
    return apiClient
      .post<ResData<SubmissionResponse>>(`/ugc/me/campaigns/${campaignId}/submissions`, request)
      .then(unwrapResponse)
  },

  listMine(campaignId: number) {
    return apiClient
      .get<ResData<SubmissionListResponse>>(`/ugc/me/campaigns/${campaignId}/submissions`)
      .then(unwrapResponse)
  },

  submit(submissionId: number) {
    return apiClient
      .post<ResData<SubmissionResponse>>(`/ugc/me/submissions/${submissionId}/submit`)
      .then(unwrapResponse)
  },

  // 브랜드
  list(workspaceId: number, campaignId: number, params?: { status?: string; page?: number; size?: number }) {
    return apiClient
      .get<ResData<SubmissionListResponse>>(`${wsBase(workspaceId)}/campaigns/${campaignId}/submissions`, { params })
      .then(unwrapResponse)
  },

  detail(workspaceId: number, submissionId: number) {
    return apiClient
      .get<ResData<SubmissionDetailResponse>>(`${wsBase(workspaceId)}/submissions/${submissionId}`)
      .then(unwrapResponse)
  },

  requestChanges(workspaceId: number, submissionId: number, request: ReviewDecisionRequest) {
    return apiClient
      .post<ResData<SubmissionResponse>>(`${wsBase(workspaceId)}/submissions/${submissionId}/request-changes`, request)
      .then(unwrapResponse)
  },

  approve(workspaceId: number, submissionId: number, request: ReviewDecisionRequest) {
    return apiClient
      .post<ResData<SubmissionResponse>>(`${wsBase(workspaceId)}/submissions/${submissionId}/approve`, request)
      .then(unwrapResponse)
  },
}
