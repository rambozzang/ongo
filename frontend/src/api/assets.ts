import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { AssetListResponse, AssetResponse, UpdateAssetRequest } from '@/types/asset'

export const assetsApi = {
  list(params?: { fileType?: string; folder?: string; page?: number; size?: number }) {
    return apiClient
      .get<ResData<AssetListResponse>>('/assets', { params })
      .then(unwrapResponse)
  },

  upload(file: File, folder?: string, tags?: string[]) {
    const formData = new FormData()
    formData.append('file', file)
    if (folder) formData.append('folder', folder)
    if (tags) tags.forEach((tag) => formData.append('tags', tag))
    return apiClient
      .post<ResData<AssetResponse>>('/assets', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateAssetRequest) {
    return apiClient
      .put<ResData<AssetResponse>>(`/assets/${id}`, request)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/assets/${id}`).then(unwrapResponse)
  },
}
