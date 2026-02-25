import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  HeatmapData,
  HeatmapRequest,
  UploadTimeRecommendation,
  HeatmapMetric,
} from '@/types/performanceHeatmap'
import { performanceHeatmapApi } from '@/api/performanceHeatmap'

function generateMockHeatmap(metric: HeatmapMetric): HeatmapData {
  const days = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const
  const cells = []
  for (const day of days) {
    for (let hour = 0; hour < 24; hour++) {
      const isWeekend = day === 'SAT' || day === 'SUN'
      const isPeakHour = hour >= 18 && hour <= 22
      const isMorning = hour >= 7 && hour <= 9
      let base = Math.random() * 50
      if (isPeakHour) base += 60 + Math.random() * 40
      else if (isMorning) base += 30 + Math.random() * 20
      if (isWeekend) base *= 1.3
      cells.push({ day, hour, value: Math.round(base), contentCount: Math.floor(Math.random() * 5) })
    }
  }
  const sorted = [...cells].sort((a, b) => b.value - a.value)
  return {
    metric,
    platform: 'youtube',
    period: '30d',
    cells,
    bestSlots: sorted.slice(0, 5).map((c) => ({ day: c.day, hour: c.hour, value: c.value })),
    worstSlots: sorted.slice(-5).map((c) => ({ day: c.day, hour: c.hour, value: c.value })),
  }
}

function generateMockRecommendations(): UploadTimeRecommendation[] {
  return [
    { platform: 'youtube', bestDay: 'TUE', bestHour: 18, expectedLift: 35, confidence: 0.82 },
    { platform: 'youtube', bestDay: 'FRI', bestHour: 19, expectedLift: 28, confidence: 0.78 },
    { platform: 'tiktok', bestDay: 'SAT', bestHour: 21, expectedLift: 42, confidence: 0.85 },
    { platform: 'instagram', bestDay: 'WED', bestHour: 12, expectedLift: 22, confidence: 0.71 },
  ]
}

export const usePerformanceHeatmapStore = defineStore('performanceHeatmap', () => {
  const heatmapData = ref<HeatmapData | null>(null)
  const recommendations = ref<UploadTimeRecommendation[]>([])
  const selectedMetric = ref<HeatmapMetric>('VIEWS')
  const selectedPlatform = ref('youtube')
  const selectedPeriod = ref('30d')
  const loading = ref(false)

  const maxValue = computed(() => {
    if (!heatmapData.value) return 0
    return Math.max(...heatmapData.value.cells.map((c) => c.value))
  })

  async function fetchHeatmap(request?: HeatmapRequest) {
    loading.value = true
    const req = request ?? { platform: selectedPlatform.value, metric: selectedMetric.value, period: selectedPeriod.value }
    try {
      heatmapData.value = await performanceHeatmapApi.getHeatmap(req)
    } catch {
      heatmapData.value = generateMockHeatmap(req.metric as HeatmapMetric)
    } finally {
      loading.value = false
    }
  }

  async function fetchRecommendations() {
    try {
      recommendations.value = await performanceHeatmapApi.getRecommendations()
    } catch {
      recommendations.value = generateMockRecommendations()
    }
  }

  return {
    heatmapData,
    recommendations,
    selectedMetric,
    selectedPlatform,
    selectedPeriod,
    loading,
    maxValue,
    fetchHeatmap,
    fetchRecommendations,
  }
})
