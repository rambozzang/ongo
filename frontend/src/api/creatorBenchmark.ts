import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  BenchmarkResult,
  BenchmarkPeer,
  CreatorBenchmarkSummary,
} from '@/types/creatorBenchmark'

export const creatorBenchmarkApi = {
  getResults(platform?: string) {
    return apiClient
      .get<ResData<BenchmarkResult[]>>('/creator-benchmark', { params: { platform } })
      .then(unwrapResponse)
  },

  getPeers(platform?: string) {
    return apiClient
      .get<ResData<BenchmarkPeer[]>>('/creator-benchmark/peers', { params: { platform } })
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<CreatorBenchmarkSummary>>('/creator-benchmark/summary')
      .then(unwrapResponse)
  },
}
