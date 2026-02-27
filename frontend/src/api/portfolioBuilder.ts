import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Portfolio, PortfolioBuilderSummary } from '@/types/portfolioBuilder'

export const portfolioBuilderApi = {
  getPortfolios: () =>
    apiClient.get<ResData<Portfolio[]>>('/portfolio-builder').then(unwrapResponse),

  create: (data: { title: string; template: string }) =>
    apiClient.post<ResData<Portfolio>>('/portfolio-builder', data).then(unwrapResponse),

  publish: (id: number) =>
    apiClient.post<ResData<Portfolio>>(`/portfolio-builder/${id}/publish`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<PortfolioBuilderSummary>>('/portfolio-builder/summary').then(unwrapResponse),
}
