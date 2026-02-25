import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Competitor,
  CompetitorComparison,
  ContentGap,
  TrendingTopic,
  AnalysisReport,
  AnalysisPeriod,
  AddCompetitorRequest,
} from '@/types/competitorAnalysis'

export const competitorAnalysisApi = {
  getCompetitors() {
    return apiClient
      .get<ResData<Competitor[]>>('/competitor-analysis/competitors')
      .then(unwrapResponse)
  },

  addCompetitor(request: AddCompetitorRequest) {
    return apiClient
      .post<ResData<Competitor>>('/competitor-analysis/competitors', request)
      .then(unwrapResponse)
  },

  removeCompetitor(competitorId: number) {
    return apiClient
      .delete<ResData<void>>(`/competitor-analysis/competitors/${competitorId}`)
      .then(unwrapResponse)
  },

  getComparison(period: AnalysisPeriod) {
    return apiClient
      .get<ResData<CompetitorComparison>>('/competitor-analysis/comparison', { params: { period } })
      .then(unwrapResponse)
  },

  getContentGaps() {
    return apiClient
      .get<ResData<ContentGap[]>>('/competitor-analysis/content-gaps')
      .then(unwrapResponse)
  },

  getTrendingTopics() {
    return apiClient
      .get<ResData<TrendingTopic[]>>('/competitor-analysis/trending')
      .then(unwrapResponse)
  },

  generateReport(period: AnalysisPeriod) {
    return apiClient
      .post<ResData<AnalysisReport>>('/competitor-analysis/reports/generate', { period })
      .then(unwrapResponse)
  },

  getReports() {
    return apiClient
      .get<ResData<AnalysisReport[]>>('/competitor-analysis/reports')
      .then(unwrapResponse)
  },
}
