import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { KeywordPlatform, KeywordResearchResult, KeywordHistoryResponse } from '@/types/keywordResearch'

export const keywordResearchApi = {
  research(keyword: string, platforms: KeywordPlatform[]) {
    return apiClient
      .post<ResData<KeywordResearchResult>>('/keywords/research', { keyword, platforms })
      .then(unwrapResponse)
  },

  getHistory(page = 0, size = 10) {
    return apiClient
      .get<ResData<KeywordHistoryResponse>>('/keywords/history', { params: { page, size } })
      .then(unwrapResponse)
  },
}
