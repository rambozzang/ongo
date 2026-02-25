import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  HeatmapData,
  HeatmapRequest,
  UploadTimeRecommendation,
} from '@/types/performanceHeatmap'

export const performanceHeatmapApi = {
  getHeatmap(request: HeatmapRequest) {
    return apiClient
      .get<ResData<HeatmapData>>('/analytics/heatmap', { params: request })
      .then(unwrapResponse)
  },

  getRecommendations() {
    return apiClient
      .get<ResData<{ recommendations: UploadTimeRecommendation[] }>>('/analytics/heatmap/recommendations')
      .then(unwrapResponse)
      .then((res) => res.recommendations)
  },
}
