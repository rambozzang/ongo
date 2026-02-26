import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { MusicRecommendation, MusicRecommenderSummary } from '@/types/musicRecommender'

export const musicRecommenderApi = {
  getRecommendations: () =>
    apiClient.get<ResData<MusicRecommendation[]>>('/api/v1/music-recommender').then(unwrapResponse),
  getRecommendation: (id: number) =>
    apiClient.get<ResData<MusicRecommendation>>(`/api/v1/music-recommender/${id}`).then(unwrapResponse),
  recommend: (videoId: number, mood?: string) =>
    apiClient.post<ResData<MusicRecommendation>>('/api/v1/music-recommender', { videoId, mood }).then(unwrapResponse),
  applyTrack: (recommendationId: number, trackId: number) =>
    apiClient.put<ResData<MusicRecommendation>>(`/api/v1/music-recommender/${recommendationId}/apply`, { trackId }).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<MusicRecommenderSummary>>('/api/v1/music-recommender/summary').then(unwrapResponse),
}

export default musicRecommenderApi
