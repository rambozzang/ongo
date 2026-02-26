export interface PerformanceReport {
  id: number
  title: string
  period: string
  startDate: string
  endDate: string
  status: 'DRAFT' | 'GENERATING' | 'COMPLETED' | 'SCHEDULED'
  totalViews: number
  totalEngagement: number
  topVideoId: number | null
  topVideoTitle: string | null
  reportUrl: string | null
  createdAt: string
}

export interface ReportSection {
  id: number
  reportId: number
  sectionType: string
  title: string
  content: string
  chartData: Record<string, unknown> | null
  sortOrder: number
}

export interface PerformanceReportSummary {
  totalReports: number
  scheduledReports: number
  avgViewsPerPeriod: number
  bestPeriod: string
  growthRate: number
}
