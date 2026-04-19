import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface BenchmarkPlatformData {
  platform: string
  myEngagementRate: number
  industryAvg: number
  topPerformerAvg: number
  rank: 'BELOW' | 'AVERAGE' | 'ABOVE' | 'TOP'
}

export interface EngagementBenchmarkResponse {
  overallEngagementRate: number
  industryAvgEngagementRate: number
  percentile: number
  platforms: BenchmarkPlatformData[]
  insights: string[]
  creditsUsed: number
}

export const engagementBenchmarkApi = {
  analyze() {
    return apiClient
      .post<ResData<EngagementBenchmarkResponse>>('/analytics/engagement-benchmark')
      .then(unwrapResponse)
  },
}
