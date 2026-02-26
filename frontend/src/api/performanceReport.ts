import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { PerformanceReport, ReportSection, PerformanceReportSummary } from '@/types/performanceReport'

export const performanceReportApi = {
  getReports: (status?: string) =>
    apiClient.get<ResData<PerformanceReport[]>>('/api/v1/performance-reports', { params: { status } }).then(unwrapResponse),
  getReport: (id: number) =>
    apiClient.get<ResData<PerformanceReport>>(`/api/v1/performance-reports/${id}`).then(unwrapResponse),
  generate: (title: string, startDate: string, endDate: string) =>
    apiClient.post<ResData<PerformanceReport>>('/api/v1/performance-reports', { title, startDate, endDate }).then(unwrapResponse),
  getSections: (reportId: number) =>
    apiClient.get<ResData<ReportSection[]>>(`/api/v1/performance-reports/${reportId}/sections`).then(unwrapResponse),
  deleteReport: (id: number) =>
    apiClient.delete<ResData<void>>(`/api/v1/performance-reports/${id}`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<PerformanceReportSummary>>('/api/v1/performance-reports/summary').then(unwrapResponse),
}

export default performanceReportApi
