import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  SeoAnalysis,
  SeoKeyword,
  SeoOptimizeRequest,
  VideoSeoSummary,
} from '@/types/videoSeo'

export const videoSeoApi = {
  getAnalyses(platform?: string) {
    return apiClient
      .get<ResData<SeoAnalysis[]>>('/video-seo', { params: { platform } })
      .then(unwrapResponse)
  },

  analyze(request: SeoOptimizeRequest) {
    return apiClient
      .post<ResData<SeoAnalysis>>('/video-seo/analyze', request)
      .then(unwrapResponse)
  },

  getKeywords(videoId: number) {
    return apiClient
      .get<ResData<SeoKeyword[]>>(`/video-seo/${videoId}/keywords`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<VideoSeoSummary>>('/video-seo/summary')
      .then(unwrapResponse)
  },
}
