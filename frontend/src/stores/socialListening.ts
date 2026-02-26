import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SocialListeningReport } from '@/types/socialListening'
import { socialListeningApi } from '@/api/socialListening'

function generateMockReport(): SocialListeningReport {
  return {
    totalMentions: 342,
    sentimentBreakdown: { positive: 185, neutral: 112, negative: 45 },
    topMentions: [
      { id: 1, source: 'YOUTUBE', author: '맛집탐험가', text: '이 채널 진짜 추천! 정보가 너무 알차요', sentiment: 'POSITIVE', sentimentScore: 0.92, url: '', reach: 45000, createdAt: new Date(Date.now() - 3600000).toISOString() },
      { id: 2, source: 'TWITTER', author: 'tech_lover', text: '오늘 본 영상 중 최고. 편집도 깔끔하고 내용도 좋음', sentiment: 'POSITIVE', sentimentScore: 0.88, url: '', reach: 12000, createdAt: new Date(Date.now() - 7200000).toISOString() },
      { id: 3, source: 'INSTAGRAM', author: 'daily_vlogger', text: '이 크리에이터 스타일 참고하면 좋을듯', sentiment: 'NEUTRAL', sentimentScore: 0.55, url: '', reach: 8500, createdAt: new Date(Date.now() - 14400000).toISOString() },
      { id: 4, source: 'BLOG', author: '리뷰왕', text: '최근 영상 퀄리티가 조금 떨어진 느낌...', sentiment: 'NEGATIVE', sentimentScore: 0.25, url: '', reach: 3200, createdAt: new Date(Date.now() - 28800000).toISOString() },
    ],
    sentimentTrends: Array.from({ length: 14 }, (_, i) => ({
      date: new Date(Date.now() - (13 - i) * 86400000).toISOString().split('T')[0],
      positive: 10 + Math.floor(Math.random() * 8),
      neutral: 6 + Math.floor(Math.random() * 5),
      negative: 2 + Math.floor(Math.random() * 3),
      total: 0,
    })).map((d) => ({ ...d, total: d.positive + d.neutral + d.negative })),
    topKeywords: [
      { keyword: '추천', count: 48, sentiment: 'POSITIVE' },
      { keyword: '편집', count: 35, sentiment: 'POSITIVE' },
      { keyword: '유용한', count: 29, sentiment: 'POSITIVE' },
      { keyword: '재미있는', count: 24, sentiment: 'POSITIVE' },
      { keyword: '아쉬운', count: 8, sentiment: 'NEGATIVE' },
    ],
    alerts: [
      { id: 1, keyword: '채널명', enabled: true, mentionCount: 125, lastTriggered: new Date(Date.now() - 1800000).toISOString() },
      { id: 2, keyword: '콜라보', enabled: true, mentionCount: 18 },
      { id: 3, keyword: '광고', enabled: false, mentionCount: 42 },
    ],
    period: '14d',
  }
}

export const useSocialListeningStore = defineStore('socialListening', () => {
  const report = ref<SocialListeningReport | null>(null)
  const loading = ref(false)
  const selectedPeriod = ref('14d')

  const sentimentRatio = computed(() => {
    if (!report.value) return { positive: 0, neutral: 0, negative: 0 }
    const total = report.value.totalMentions || 1
    const b = report.value.sentimentBreakdown
    return {
      positive: Math.round((b.positive / total) * 100),
      neutral: Math.round((b.neutral / total) * 100),
      negative: Math.round((b.negative / total) * 100),
    }
  })

  async function fetchReport(period?: string) {
    loading.value = true
    if (period) selectedPeriod.value = period
    try {
      report.value = await socialListeningApi.getReport(selectedPeriod.value)
    } catch {
      report.value = generateMockReport()
    } finally {
      loading.value = false
    }
  }

  async function addAlert(keyword: string) {
    try {
      const alert = await socialListeningApi.addKeywordAlert(keyword)
      report.value?.alerts.push(alert)
    } catch {
      report.value?.alerts.push({ id: Date.now(), keyword, enabled: true, mentionCount: 0 })
    }
  }

  async function toggleAlert(id: number, enabled: boolean) {
    try { await socialListeningApi.toggleAlert(id, enabled) } catch { /* 로컬 */ }
    const alert = report.value?.alerts.find((a) => a.id === id)
    if (alert) alert.enabled = enabled
  }

  async function deleteAlert(id: number) {
    try { await socialListeningApi.deleteAlert(id) } catch { /* 로컬 */ }
    if (report.value) report.value.alerts = report.value.alerts.filter((a) => a.id !== id)
  }

  return { report, loading, selectedPeriod, sentimentRatio, fetchReport, addAlert, toggleAlert, deleteAlert }
})
