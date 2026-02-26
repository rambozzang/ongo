import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { performanceReportApi } from '@/api/performanceReport'
import type { PerformanceReport, ReportSection, PerformanceReportSummary } from '@/types/performanceReport'

export const usePerformanceReportStore = defineStore('performanceReport', () => {
  const reports = ref<PerformanceReport[]>([])
  const sections = ref<ReportSection[]>([])
  const summary = ref<PerformanceReportSummary | null>(null)
  const loading = ref(false)

  const completedReports = computed(() => reports.value.filter(r => r.status === 'COMPLETED'))
  const scheduledReports = computed(() => reports.value.filter(r => r.status === 'SCHEDULED'))

  async function fetchReports(status?: string) {
    loading.value = true
    try {
      reports.value = await performanceReportApi.getReports(status)
    } catch {
      reports.value = [
        { id: 1, title: '2025년 3월 월간 보고서', period: 'MONTHLY', startDate: '2025-03-01', endDate: '2025-03-31', status: 'COMPLETED', totalViews: 245000, totalEngagement: 18500, topVideoId: 101, topVideoTitle: 'AI 활용 가이드', reportUrl: '/reports/march-2025.pdf', createdAt: '2025-03-31T23:00:00Z' },
        { id: 2, title: '2025년 1분기 보고서', period: 'QUARTERLY', startDate: '2025-01-01', endDate: '2025-03-31', status: 'GENERATING', totalViews: 720000, totalEngagement: 54000, topVideoId: null, topVideoTitle: null, reportUrl: null, createdAt: '2025-04-01T00:00:00Z' },
        { id: 3, title: '2025년 2주차 주간 보고서', period: 'WEEKLY', startDate: '2025-03-10', endDate: '2025-03-16', status: 'COMPLETED', totalViews: 62000, totalEngagement: 4800, topVideoId: 105, topVideoTitle: '봄 코디 추천', reportUrl: '/reports/week2-march.pdf', createdAt: '2025-03-16T23:00:00Z' },
        { id: 4, title: '4월 자동 보고서', period: 'MONTHLY', startDate: '2025-04-01', endDate: '2025-04-30', status: 'SCHEDULED', totalViews: 0, totalEngagement: 0, topVideoId: null, topVideoTitle: null, reportUrl: null, createdAt: '2025-03-31T10:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchSections(reportId: number) {
    try {
      sections.value = await performanceReportApi.getSections(reportId)
    } catch {
      sections.value = [
        { id: 1, reportId, sectionType: 'OVERVIEW', title: '전체 요약', content: '총 조회수 245,000회, 전월 대비 12% 증가', chartData: null, sortOrder: 1 },
        { id: 2, reportId, sectionType: 'ENGAGEMENT', title: '인게이지먼트 분석', content: '좋아요 15,200, 댓글 3,300, 평균 참여율 7.6%', chartData: null, sortOrder: 2 },
        { id: 3, reportId, sectionType: 'TOP_CONTENT', title: '인기 콘텐츠', content: 'AI 활용 가이드가 52,000회로 최고 조회수 기록', chartData: null, sortOrder: 3 },
        { id: 4, reportId, sectionType: 'RECOMMENDATION', title: 'AI 추천', content: '테크 카테고리 콘텐츠 비중을 30%로 확대하세요', chartData: null, sortOrder: 4 },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await performanceReportApi.getSummary()
    } catch {
      summary.value = { totalReports: 18, scheduledReports: 4, avgViewsPerPeriod: 185000, bestPeriod: '2025-02', growthRate: 12.3 }
    }
  }

  async function generateReport(title: string, startDate: string, endDate: string) {
    try {
      const report = await performanceReportApi.generate(title, startDate, endDate)
      reports.value.unshift(report)
    } catch {
      // fallback
    }
  }

  return { reports, sections, summary, loading, completedReports, scheduledReports, fetchReports, fetchSections, fetchSummary, generateReport }
})
