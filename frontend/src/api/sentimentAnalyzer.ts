import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  SentimentResult,
  CommentSentiment,
  SentimentTrend,
  SentimentAnalyzerSummary,
  AnalyzeSentimentRequest,
} from '@/types/sentimentAnalyzer'

export const sentimentAnalyzerApi = {
  getResults() {
    return apiClient
      .get<ResData<SentimentResult[]>>('/sentiment-analyzer/results')
      .then(unwrapResponse)
  },

  getResult(id: number) {
    return apiClient
      .get<ResData<SentimentResult>>(`/sentiment-analyzer/results/${id}`)
      .then(unwrapResponse)
  },

  analyze(request: AnalyzeSentimentRequest) {
    return apiClient
      .post<ResData<SentimentResult>>('/sentiment-analyzer/analyze', request)
      .then(unwrapResponse)
  },

  getComments(resultId: number, sentiment?: string) {
    return apiClient
      .get<ResData<CommentSentiment[]>>(`/sentiment-analyzer/results/${resultId}/comments`, {
        params: { sentiment },
      })
      .then(unwrapResponse)
  },

  getTrends(days = 30) {
    return apiClient
      .get<ResData<SentimentTrend[]>>('/sentiment-analyzer/trends', { params: { days } })
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<SentimentAnalyzerSummary>>('/sentiment-analyzer/summary')
      .then(unwrapResponse)
  },
}
