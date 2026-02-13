import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface LinkBioLinkResponse {
  id: number
  title: string
  url: string
  icon: string | null
  sortOrder: number
  clickCount: number
  isActive: boolean
  createdAt: string
}

export interface LinkBioPageResponse {
  id: number
  slug: string
  title: string | null
  bio: string | null
  avatarUrl: string | null
  theme: string
  backgroundColor: string
  textColor: string
  isPublished: boolean
  viewCount: number
  links: LinkBioLinkResponse[]
  createdAt: string
  updatedAt: string
}

export interface CreatePageRequest {
  slug: string
  title?: string
  bio?: string
  avatarUrl?: string
  theme?: string
  backgroundColor?: string
  textColor?: string
}

export interface UpdatePageRequest {
  slug?: string
  title?: string
  bio?: string
  avatarUrl?: string
  theme?: string
  backgroundColor?: string
  textColor?: string
}

export interface LinkItem {
  id?: number
  title: string
  url: string
  icon?: string
  sortOrder: number
  isActive: boolean
}

export interface LinkBioPublicResponse {
  slug: string
  title: string | null
  bio: string | null
  avatarUrl: string | null
  theme: string
  backgroundColor: string
  textColor: string
  links: { id: number; title: string; url: string; icon: string | null; sortOrder: number }[]
}

export interface LinkBioAnalyticsResponse {
  viewCount: number
  links: { id: number; title: string; clickCount: number }[]
}

export const linkBioApi = {
  getPage() {
    return apiClient.get<ResData<LinkBioPageResponse | null>>('/linkbio').then(unwrapResponse)
  },

  createPage(request: CreatePageRequest) {
    return apiClient.post<ResData<LinkBioPageResponse>>('/linkbio', request).then(unwrapResponse)
  },

  updatePage(request: UpdatePageRequest) {
    return apiClient.put<ResData<LinkBioPageResponse>>('/linkbio', request).then(unwrapResponse)
  },

  updateLinks(links: LinkItem[]) {
    return apiClient.put<ResData<LinkBioPageResponse>>('/linkbio/links', { links }).then(unwrapResponse)
  },

  togglePublish(isPublished: boolean) {
    return apiClient.put<ResData<LinkBioPageResponse>>('/linkbio/publish', { isPublished }).then(unwrapResponse)
  },

  getPublicPage(slug: string) {
    return apiClient.get<ResData<LinkBioPublicResponse>>(`/linkbio/${slug}`).then(unwrapResponse)
  },

  getAnalytics() {
    return apiClient.get<ResData<LinkBioAnalyticsResponse>>('/linkbio/analytics').then(unwrapResponse)
  },
}
