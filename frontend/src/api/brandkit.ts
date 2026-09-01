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
  colors: BrandKitColor[]
  fonts: BrandKitFont[]
  assets: BrandKitAsset[]
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
  colors?: BrandKitColor[]
  fonts?: BrandKitFont[]
  assets?: BrandKitAsset[]
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
  colors?: BrandKitColor[]
  fonts?: BrandKitFont[]
  assets?: BrandKitAsset[]
}

export interface BrandKitColor { id: number; name: string; hex: string; usage: string }
export interface BrandKitFont { id: number; name: string; family: string; weight: string; usage: string; sampleText: string }
/**
 * 브랜드킷이 참조하는 파일.
 *
 * `assetId` 가 있으면 서버가 조회할 때마다 소유권을 확인하고 저장 키로 `url` 을 새로
 * 발급한다. 없으면 이 필드가 생기기 전에 저장된 항목이고, 그 `url` 은 업로드 당시의
 * 7 일짜리 서명이라 **이미 만료됐을 수 있다.**
 */
export interface BrandKitAsset {
  id: number
  name: string
  type: string
  url: string
  format: string
  size: string
  uploadedAt: string
  assetId?: number | null
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
