import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export type PostStatus = 'PENDING' | 'PUBLISHING' | 'PUBLISHED' | 'FAILED' | 'EXTERNAL'

export interface CampaignPostResponse {
  id: number
  campaignId: number
  submissionId: number
  creatorId: number
  platform: string
  postType: 'DIRECT' | 'EXTERNAL'
  videoUploadId: number | null
  externalPostUrl: string | null
  platformPostId: string | null
  status: PostStatus
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface CampaignPostListResponse {
  items: CampaignPostResponse[]
}

export interface PublishRequest {
  platforms: string[]
}

export interface RegisterExternalPostRequest {
  platform: string
  externalPostUrl: string
  platformPostId?: string | null
}

const wsBase = (workspaceId: number) => `/workspaces/${workspaceId}/ugc`

export const ugcPublishingApi = {
  // 브랜드
  publish(workspaceId: number, submissionId: number, request: PublishRequest) {
    return apiClient
      .post<ResData<CampaignPostListResponse>>(`${wsBase(workspaceId)}/submissions/${submissionId}/publish`, request)
      .then(unwrapResponse)
  },

  listCampaignPosts(workspaceId: number, campaignId: number) {
    return apiClient
      .get<ResData<CampaignPostListResponse>>(`${wsBase(workspaceId)}/campaigns/${campaignId}/posts`)
      .then(unwrapResponse)
  },

  // 크리에이터
  registerExternal(submissionId: number, request: RegisterExternalPostRequest) {
    return apiClient
      .post<ResData<CampaignPostResponse>>(`/ugc/me/submissions/${submissionId}/external-posts`, request)
      .then(unwrapResponse)
  },

  myPosts(submissionId: number) {
    return apiClient
      .get<ResData<CampaignPostListResponse>>(`/ugc/me/submissions/${submissionId}/posts`)
      .then(unwrapResponse)
  },
}
