import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ThumbnailGenerateRequest,
  ThumbnailGenerateResponse,
  ThumbnailHistory,
  ThumbnailTemplate,
} from '@/types/thumbnailGenerator'

export const thumbnailGeneratorApi = {
  getTemplates() {
    return apiClient
      .get<ResData<{ templates: ThumbnailTemplate[] }>>('/ai/thumbnail/templates')
      .then(unwrapResponse)
      .then((res) => res.templates)
  },

  generate(request: ThumbnailGenerateRequest) {
    return apiClient
      .post<ResData<ThumbnailGenerateResponse>>('/ai/thumbnail/generate', request)
      .then(unwrapResponse)
  },

  getHistory() {
    return apiClient
      .get<ResData<{ history: ThumbnailHistory[] }>>('/ai/thumbnail/history')
      .then(unwrapResponse)
      .then((res) => res.history)
  },

  selectThumbnail(historyId: number, thumbnailId: number) {
    return apiClient
      .post<ResData<void>>(`/ai/thumbnail/history/${historyId}/select/${thumbnailId}`)
      .then(unwrapResponse)
  },
}
