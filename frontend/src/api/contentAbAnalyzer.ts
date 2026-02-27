import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { ContentAbTest, ContentAbSummary } from '@/types/contentAbAnalyzer'

export const contentAbAnalyzerApi = {
  getTests: (status?: string) =>
    apiClient.get<ResData<ContentAbTest[]>>('/content-ab-analyzer', { params: { status } }).then(unwrapResponse),
  getTest: (id: number) =>
    apiClient.get<ResData<ContentAbTest>>(`/content-ab-analyzer/${id}`).then(unwrapResponse),
  create: (title: string, videoIdA: number, videoIdB: number) =>
    apiClient.post<ResData<ContentAbTest>>('/content-ab-analyzer', { title, videoIdA, videoIdB }).then(unwrapResponse),
  complete: (id: number) =>
    apiClient.put<ResData<ContentAbTest>>(`/content-ab-analyzer/${id}/complete`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<ContentAbSummary>>('/content-ab-analyzer/summary').then(unwrapResponse),
}

export default contentAbAnalyzerApi
