import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { BrandDeal, MediaKit } from '@/types/branddeal'

export const brandDealApi = {
  getDeals(status?: string) {
    return apiClient
      .get<ResData<BrandDeal[]>>('/brand-deals', { params: status ? { status } : {} })
      .then(unwrapResponse)
  },

  getDeal(dealId: number) {
    return apiClient
      .get<ResData<BrandDeal>>(`/brand-deals/${dealId}`)
      .then(unwrapResponse)
  },

  createDeal(data: { brandName: string; contactName?: string; contactEmail?: string; dealValue?: number; status?: string; deadline?: string; notes?: string }) {
    return apiClient
      .post<ResData<BrandDeal>>('/brand-deals', data)
      .then(unwrapResponse)
  },

  updateDeal(dealId: number, data: Partial<BrandDeal>) {
    return apiClient
      .put<ResData<BrandDeal>>(`/brand-deals/${dealId}`, data)
      .then(unwrapResponse)
  },

  deleteDeal(dealId: number) {
    return apiClient
      .delete<ResData<void>>(`/brand-deals/${dealId}`)
      .then(unwrapResponse)
  },

  getMediaKit() {
    return apiClient
      .get<ResData<MediaKit | null>>('/brand-deals/media-kit')
      .then(unwrapResponse)
  },

  saveMediaKit(data: { displayName?: string; bio?: string; categories?: string[]; socialLinks?: Record<string, string>; isPublic?: boolean; slug?: string }) {
    return apiClient
      .put<ResData<MediaKit>>('/brand-deals/media-kit', data)
      .then(unwrapResponse)
  },

  getPublicMediaKit(slug: string) {
    return apiClient
      .get<ResData<MediaKit | null>>(`/brand-deals/media-kit/public/${slug}`)
      .then(unwrapResponse)
  },
}
