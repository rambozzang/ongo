import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  RevenueForecast,
  ForecastRequest,
  ForecastPeriod,
} from '@/types/revenueForecaster'
import { revenueForecasterApi } from '@/api/revenueForecaster'

function generateMockForecast(): RevenueForecast {
  const months = ['2024-01', '2024-02', '2024-03', '2024-04', '2024-05', '2024-06']
  return {
    id: 1,
    period: '6M',
    currentMonthlyRevenue: 285000,
    forecastedMonthlyRevenue: 420000,
    growthRate: 47.4,
    breakdowns: [
      { source: 'AD_REVENUE', currentMonthly: 180000, forecastMonthly: 260000, growthRate: 44.4, share: 62 },
      { source: 'SPONSORSHIP', currentMonthly: 60000, forecastMonthly: 95000, growthRate: 58.3, share: 23 },
      { source: 'MEMBERSHIP', currentMonthly: 25000, forecastMonthly: 38000, growthRate: 52.0, share: 9 },
      { source: 'SUPER_CHAT', currentMonthly: 12000, forecastMonthly: 17000, growthRate: 41.7, share: 4 },
      { source: 'AFFILIATE', currentMonthly: 8000, forecastMonthly: 10000, growthRate: 25.0, share: 2 },
    ],
    scenarios: [
      {
        id: 1, name: '보수적', description: '현재 업로드 빈도 유지',
        uploadFrequency: 3, avgViewsPerVideo: 15000, subscriberGrowthRate: 5,
        forecastData: months.map((m, i) => ({ month: m, forecast: 285000 + i * 12000, lowerBound: 270000 + i * 10000, upperBound: 300000 + i * 14000 })),
        totalForecast: 357000,
      },
      {
        id: 2, name: '기본', description: '업로드 빈도 50% 증가',
        uploadFrequency: 5, avgViewsPerVideo: 18000, subscriberGrowthRate: 8,
        forecastData: months.map((m, i) => ({ month: m, forecast: 285000 + i * 22000, lowerBound: 265000 + i * 18000, upperBound: 305000 + i * 26000 })),
        totalForecast: 420000,
      },
      {
        id: 3, name: '적극적', description: '업로드 빈도 2배 + 숏폼 추가',
        uploadFrequency: 8, avgViewsPerVideo: 22000, subscriberGrowthRate: 15,
        forecastData: months.map((m, i) => ({ month: m, forecast: 285000 + i * 38000, lowerBound: 255000 + i * 30000, upperBound: 315000 + i * 46000 })),
        totalForecast: 570000,
      },
    ],
    chartData: months.map((m, i) => ({
      month: m,
      actual: i < 2 ? 285000 + i * 15000 : undefined,
      forecast: 285000 + i * 22000,
      lowerBound: 265000 + i * 18000,
      upperBound: 305000 + i * 26000,
    })),
    confidence: 0.78,
    generatedAt: new Date().toISOString(),
  }
}

export const useRevenueForecasterStore = defineStore('revenueForecaster', () => {
  const forecast = ref<RevenueForecast | null>(null)
  const selectedPeriod = ref<ForecastPeriod>('6M')
  const selectedScenario = ref<number>(2)
  const loading = ref(false)
  const generating = ref(false)

  const totalForecastRevenue = computed(() => {
    if (!forecast.value) return 0
    const scenario = forecast.value.scenarios.find((s) => s.id === selectedScenario.value)
    return scenario?.totalForecast ?? forecast.value.forecastedMonthlyRevenue
  })

  const activeScenario = computed(() => {
    if (!forecast.value) return null
    return forecast.value.scenarios.find((s) => s.id === selectedScenario.value) ?? null
  })

  async function fetchForecast() {
    loading.value = true
    try {
      forecast.value = await revenueForecasterApi.getForecast(selectedPeriod.value)
    } catch {
      forecast.value = generateMockForecast()
    } finally {
      loading.value = false
    }
  }

  async function generateForecast(request: ForecastRequest) {
    generating.value = true
    try {
      const response = await revenueForecasterApi.generateForecast(request)
      forecast.value = response.forecast
    } catch {
      forecast.value = generateMockForecast()
    } finally {
      generating.value = false
    }
  }

  return {
    forecast,
    selectedPeriod,
    selectedScenario,
    loading,
    generating,
    totalForecastRevenue,
    activeScenario,
    fetchForecast,
    generateForecast,
  }
})
