import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export type ShortsBackgroundStyle = 'BLACK_BARS' | 'BLURRED' | 'SOLID'
export type ShortsTextPosition = 'TOP' | 'CENTER' | 'BOTTOM'

export interface ShortsTemplateResponse {
  id: number
  name: string
  description: string | null
  aspectRatio: string
  width: number
  height: number
  backgroundStyle: ShortsBackgroundStyle
  hookFontFamily: string | null
  hookFontSize: number | null
  hookFontColor: string | null
  hookStrokeColor: string | null
  hookPosition: ShortsTextPosition
  captionFontFamily: string | null
  captionFontSize: number | null
  captionFontColor: string | null
  captionStrokeColor: string | null
  captionPosition: ShortsTextPosition
  safeAreaTop: number
  safeAreaBottom: number
  referenceImageUrl: string | null
  isDefault: boolean
  createdAt: string | null
  updatedAt: string | null
}

/** 생성/수정 요청 본문 — 응답에서 id, referenceImageUrl, createdAt, updatedAt 을 제외한 필드 */
export interface ShortsTemplateRequest {
  name: string
  description: string | null
  aspectRatio: string
  width: number
  height: number
  backgroundStyle: ShortsBackgroundStyle
  hookFontFamily: string | null
  hookFontSize: number | null
  hookFontColor: string | null
  hookStrokeColor: string | null
  hookPosition: ShortsTextPosition
  captionFontFamily: string | null
  captionFontSize: number | null
  captionFontColor: string | null
  captionStrokeColor: string | null
  captionPosition: ShortsTextPosition
  safeAreaTop: number
  safeAreaBottom: number
  isDefault: boolean
}

const base = (workspaceId: number) => `/workspaces/${workspaceId}/ugc/shorts/templates`

export const ugcShortsTemplateApi = {
  list(workspaceId: number) {
    return apiClient
      .get<ResData<ShortsTemplateResponse[]>>(base(workspaceId))
      .then(unwrapResponse)
  },

  get(workspaceId: number, templateId: number) {
    return apiClient
      .get<ResData<ShortsTemplateResponse>>(`${base(workspaceId)}/${templateId}`)
      .then(unwrapResponse)
  },

  create(workspaceId: number, request: ShortsTemplateRequest) {
    return apiClient
      .post<ResData<ShortsTemplateResponse>>(base(workspaceId), request)
      .then(unwrapResponse)
  },

  update(workspaceId: number, templateId: number, request: ShortsTemplateRequest) {
    return apiClient
      .put<ResData<ShortsTemplateResponse>>(`${base(workspaceId)}/${templateId}`, request)
      .then(unwrapResponse)
  },

  remove(workspaceId: number, templateId: number) {
    return apiClient
      .delete<ResData<void>>(`${base(workspaceId)}/${templateId}`)
      .then(unwrapResponse)
  },

  /** 레퍼런스 이미지는 별도 multipart 엔드포인트로 업로드 */
  uploadReferenceImage(workspaceId: number, templateId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient
      .post<ResData<ShortsTemplateResponse>>(
        `${base(workspaceId)}/${templateId}/reference-image`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } },
      )
      .then(unwrapResponse)
  },
}
