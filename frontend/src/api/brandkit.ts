import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface BrandKitResponse {
  id: number
  name: string
  primaryColor: string
  secondaryColor: string
  accentColor: string
  fontFamily: string
  logoUrl: string | null
  introTemplateUrl: string | null
  outroTemplateUrl: string | null
  watermarkUrl: string | null
  guidelines: string | null
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateBrandKitRequest {
  name: string
  primaryColor?: string
  secondaryColor?: string
  accentColor?: string
  fontFamily?: string
  logoUrl?: string
  introTemplateUrl?: string
  outroTemplateUrl?: string
  watermarkUrl?: string
  guidelines?: string
}

export interface UpdateBrandKitRequest {
  name?: string
  primaryColor?: string
  secondaryColor?: string
  accentColor?: string
  fontFamily?: string
  logoUrl?: string
  introTemplateUrl?: string
  outroTemplateUrl?: string
  watermarkUrl?: string
  guidelines?: string
}

export const brandKitApi = {
  list() {
    return apiClient.get<ResData<BrandKitResponse[]>>('/brand-kit').then(unwrapResponse)
  },

  create(request: CreateBrandKitRequest) {
    return apiClient.post<ResData<BrandKitResponse>>('/brand-kit', request).then(unwrapResponse)
  },

  update(id: number, request: UpdateBrandKitRequest) {
    return apiClient.put<ResData<BrandKitResponse>>(`/brand-kit/${id}`, request).then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/brand-kit/${id}`).then(unwrapResponse)
  },

  setDefault(id: number) {
    return apiClient.put<ResData<BrandKitResponse>>(`/brand-kit/${id}/default`).then(unwrapResponse)
  },
}
