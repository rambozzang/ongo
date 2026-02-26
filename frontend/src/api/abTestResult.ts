import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ABTestResult,
  TestTimeline,
  ABTestResultSummary,
} from '@/types/abTestResult'

export const abTestResultApi = {
  getResults(status?: string) {
    return apiClient
      .get<ResData<ABTestResult[]>>('/ab-test-results', { params: { status } })
      .then(unwrapResponse)
  },

  getResult(id: number) {
    return apiClient
      .get<ResData<ABTestResult>>(`/ab-test-results/${id}`)
      .then(unwrapResponse)
  },

  getTimeline(id: number) {
    return apiClient
      .get<ResData<TestTimeline[]>>(`/ab-test-results/${id}/timeline`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<ABTestResultSummary>>('/ab-test-results/summary')
      .then(unwrapResponse)
  },

  pauseTest(id: number) {
    return apiClient
      .post<ResData<ABTestResult>>(`/ab-test-results/${id}/pause`)
      .then(unwrapResponse)
  },

  resumeTest(id: number) {
    return apiClient
      .post<ResData<ABTestResult>>(`/ab-test-results/${id}/resume`)
      .then(unwrapResponse)
  },

  stopTest(id: number) {
    return apiClient
      .post<ResData<ABTestResult>>(`/ab-test-results/${id}/stop`)
      .then(unwrapResponse)
  },
}
