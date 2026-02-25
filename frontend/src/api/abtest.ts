import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AbTest,
  AbTestSummary,
  VideoForAbTest,
  CreateAbTestRequest,
  CreateAbTestResponse,
} from '@/types/abtest'

export const abTestApi = {
  getVideos() {
    return apiClient
      .get<ResData<VideoForAbTest[]>>('/ab-tests/videos')
      .then(unwrapResponse)
  },

  getTests() {
    return apiClient
      .get<ResData<AbTest[]>>('/ab-tests')
      .then(unwrapResponse)
  },

  getTest(testId: number) {
    return apiClient
      .get<ResData<AbTest>>(`/ab-tests/${testId}`)
      .then(unwrapResponse)
  },

  createTest(request: CreateAbTestRequest) {
    return apiClient
      .post<ResData<CreateAbTestResponse>>('/ab-tests', request)
      .then(unwrapResponse)
  },

  startTest(testId: number) {
    return apiClient
      .post<ResData<AbTest>>(`/ab-tests/${testId}/start`)
      .then(unwrapResponse)
  },

  pauseTest(testId: number) {
    return apiClient
      .post<ResData<AbTest>>(`/ab-tests/${testId}/pause`)
      .then(unwrapResponse)
  },

  completeTest(testId: number) {
    return apiClient
      .post<ResData<AbTest>>(`/ab-tests/${testId}/complete`)
      .then(unwrapResponse)
  },

  applyWinner(testId: number) {
    return apiClient
      .post<ResData<void>>(`/ab-tests/${testId}/apply-winner`)
      .then(unwrapResponse)
  },

  deleteTest(testId: number) {
    return apiClient
      .delete<ResData<void>>(`/ab-tests/${testId}`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<AbTestSummary>>('/ab-tests/summary')
      .then(unwrapResponse)
  },
}
