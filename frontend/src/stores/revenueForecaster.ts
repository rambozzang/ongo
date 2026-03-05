import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  RevenueForecast,
  ForecastRequest,
  ForecastPeriod,
} from '@/types/revenueForecaster'
import { revenueForecasterApi } from '@/api/revenueForecaster'

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
      forecast.value = null
    } finally {
      loading.value = false
    }
  }

  async function generateForecast(request: ForecastRequest) {
    generating.value = true
    try {
      const response = await revenueForecasterApi.generateForecast(request)
      forecast.value = response.forecast
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
