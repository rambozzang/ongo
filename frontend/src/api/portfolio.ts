import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Portfolio, ShowcaseContent, BrandCollaboration, PortfolioTheme } from '@/types/portfolio'

export interface UpdatePortfolioProfileRequest {
  displayName?: string
  bio?: string
  profileImageUrl?: string
  categories?: string[]
  contactEmail?: string
  website?: string
}

export interface UpdateShowcaseOrderRequest {
  contentIds: number[]
}

export interface UpdatePortfolioSettingsRequest {
  theme?: PortfolioTheme
  slug?: string
  isPublic?: boolean
}

export const portfolioApi = {
  get() {
    return apiClient.get<ResData<Portfolio>>('/portfolios').then(unwrapResponse)
  },

  updateProfile(request: UpdatePortfolioProfileRequest) {
    return apiClient.put<ResData<Portfolio>>('/portfolios/profile', request).then(unwrapResponse)
  },

  updateShowcaseOrder(request: UpdateShowcaseOrderRequest) {
    return apiClient.put<ResData<void>>('/portfolios/showcase/order', request).then(unwrapResponse)
  },

  addShowcaseContent(content: Omit<ShowcaseContent, 'id' | 'order'>) {
    return apiClient.post<ResData<ShowcaseContent>>('/portfolios/showcase', content).then(unwrapResponse)
  },

  removeShowcaseContent(contentId: number) {
    return apiClient.delete<ResData<void>>(`/portfolios/showcase/${contentId}`).then(unwrapResponse)
  },

  addBrandCollaboration(collab: Omit<BrandCollaboration, 'id'>) {
    return apiClient.post<ResData<BrandCollaboration>>('/portfolios/collaborations', collab).then(unwrapResponse)
  },

  removeBrandCollaboration(collabId: number) {
    return apiClient.delete<ResData<void>>(`/portfolios/collaborations/${collabId}`).then(unwrapResponse)
  },

  updateSettings(request: UpdatePortfolioSettingsRequest) {
    return apiClient.put<ResData<Portfolio>>('/portfolios/settings', request).then(unwrapResponse)
  },

  exportPdf() {
    return apiClient.get<Blob>('/portfolios/export/pdf', { responseType: 'blob' })
  },
}
