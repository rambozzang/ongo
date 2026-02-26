import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { ThumbnailTest, ThumbnailAbTestSummary } from '@/types/thumbnailAbTest'

export const thumbnailAbTestApi = {
  getTests: () =>
    apiClient.get<ResData<ThumbnailTest[]>>('/thumbnail-ab-test/tests').then(unwrapResponse),

  getTest: (id: number) =>
    apiClient.get<ResData<ThumbnailTest>>(`/thumbnail-ab-test/tests/${id}`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<ThumbnailAbTestSummary>>('/thumbnail-ab-test/summary').then(unwrapResponse),

  createTest: (test: Omit<ThumbnailTest, 'id' | 'winner' | 'endedAt'>) =>
    apiClient.post<ResData<ThumbnailTest>>('/thumbnail-ab-test/tests', test).then(unwrapResponse),

  deleteTest: (id: number) =>
    apiClient.delete<ResData<void>>(`/thumbnail-ab-test/tests/${id}`).then(unwrapResponse),
}
