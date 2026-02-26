import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { MediaKit, MediaKitTemplate, MediaKitGenerateRequest } from '@/types/mediaKit'

export const mediaKitApi = {
  getTemplates: () =>
    apiClient.get<ResData<MediaKitTemplate[]>>('/media-kit/templates').then(unwrapResponse),

  generate: (request: MediaKitGenerateRequest) =>
    apiClient.post<ResData<MediaKit>>('/media-kit/generate', request).then(unwrapResponse),

  getMyKits: () =>
    apiClient.get<ResData<MediaKit[]>>('/media-kit').then(unwrapResponse),

  getKit: (id: number) =>
    apiClient.get<ResData<MediaKit>>(`/media-kit/${id}`).then(unwrapResponse),

  updateKit: (id: number, data: Partial<MediaKit>) =>
    apiClient.put<ResData<MediaKit>>(`/media-kit/${id}`, data).then(unwrapResponse),

  publishKit: (id: number) =>
    apiClient.post<ResData<MediaKit>>(`/media-kit/${id}/publish`).then(unwrapResponse),

  deleteKit: (id: number) =>
    apiClient.delete<ResData<void>>(`/media-kit/${id}`).then(unwrapResponse),

  downloadPdf: (id: number) =>
    apiClient.get(`/media-kit/${id}/pdf`, { responseType: 'blob' }),
}
