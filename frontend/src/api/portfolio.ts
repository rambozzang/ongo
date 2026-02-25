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
    return apiClient.get<ResData<Portfolio>>('/portfolio').then(unwrapResponse)
  },

  updateProfile(request: UpdatePortfolioProfileRequest) {
    return apiClient.put<ResData<Portfolio>>('/portfolio/profile', request).then(unwrapResponse)
  },

  updateShowcaseOrder(request: UpdateShowcaseOrderRequest) {
    return apiClient.put<ResData<void>>('/portfolio/showcase/order', request).then(unwrapResponse)
  },

  addShowcaseContent(content: Omit<ShowcaseContent, 'id' | 'order'>) {
    return apiClient.post<ResData<ShowcaseContent>>('/portfolio/showcase', content).then(unwrapResponse)
  },

  removeShowcaseContent(contentId: number) {
    return apiClient.delete<ResData<void>>(`/portfolio/showcase/${contentId}`).then(unwrapResponse)
  },

  addBrandCollaboration(collab: Omit<BrandCollaboration, 'id'>) {
    return apiClient.post<ResData<BrandCollaboration>>('/portfolio/collaborations', collab).then(unwrapResponse)
  },

  removeBrandCollaboration(collabId: number) {
    return apiClient.delete<ResData<void>>(`/portfolio/collaborations/${collabId}`).then(unwrapResponse)
  },

  updateSettings(request: UpdatePortfolioSettingsRequest) {
    return apiClient.put<ResData<Portfolio>>('/portfolio/settings', request).then(unwrapResponse)
  },

  exportPdf() {
    return apiClient.get<Blob>('/portfolio/export/pdf', { responseType: 'blob' })
  },
}
