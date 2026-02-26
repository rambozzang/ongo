import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Portfolio, PortfolioBuilderSummary } from '@/types/portfolioBuilder'

export const portfolioBuilderApi = {
  getPortfolios: () =>
    apiClient.get<ResData<Portfolio[]>>('/api/v1/portfolio-builder').then(unwrapResponse),

  create: (data: { title: string; template: string }) =>
    apiClient.post<ResData<Portfolio>>('/api/v1/portfolio-builder', data).then(unwrapResponse),

  publish: (id: number) =>
    apiClient.post<ResData<Portfolio>>(`/api/v1/portfolio-builder/${id}/publish`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<PortfolioBuilderSummary>>('/api/v1/portfolio-builder/summary').then(unwrapResponse),
}
