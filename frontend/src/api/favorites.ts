import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface FavoriteToggleResponse {
  videoId: number
  favorite: boolean
}

export const favoritesApi = {
  list() {
    return apiClient.get<ResData<number[]>>('/videos/favorites').then(unwrapResponse)
  },
  toggle(videoId: number) {
    return apiClient.put<ResData<FavoriteToggleResponse>>(`/videos/favorites/${videoId}`).then(unwrapResponse)
  },
  remove(videoId: number) {
    return apiClient.delete<ResData<void>>(`/videos/favorites/${videoId}`).then(unwrapResponse)
  },
  removeAll() {
    return apiClient.delete<ResData<void>>('/videos/favorites').then(unwrapResponse)
  },
}
