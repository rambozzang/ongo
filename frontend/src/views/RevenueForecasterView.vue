<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('revenueForecaster.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('revenueForecaster.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('revenueForecaster.creditsRemaining') }}: {{ creditBalance }}
        </span>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="generating"
          @click="handleGenerate"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ generating ? $t('revenueForecaster.generating') : $t('revenueForecaster.generateButton') }}
        </button>
      </div>
    </div>

    <PageGuide
      :title="$t('revenueForecaster.pageGuideTitle')"
      :items="($tm('revenueForecaster.pageGuide') as string[])"
    />

    <LoadingSpinner v-if="loading" full-page />

    <template v-else-if="forecast">
      <!-- Period Selector -->
      <div class="mb-6 flex items-center gap-2">
        <button
          v-for="period in periods"
          :key="period.value"
          class="rounded-lg px-4 py-2 text-sm font-medium transition-colors"
          :class="selectedPeriod === period.value
            ? 'bg-primary-500 text-white shadow-sm'
            : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'"
          @click="handlePeriodChange(period.value)"
        >
          {{ period.label }}
        </button>
      </div>

      <!-- Summary Row -->
      <div class="mb-6 grid gap-4 grid-cols-2 desktop:grid-cols-4">
        <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('revenueForecaster.currentMonthly') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatRevenue(forecast.currentMonthlyRevenue) }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('revenueForecaster.forecastedMonthly') }}
          </p>
          <p class="mt-1 text-xl font-bold text-primary-600 dark:text-primary-400">
            {{ formatRevenue(totalForecastRevenue) }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('revenueForecaster.growthRate') }}
          </p>
          <p
            class="mt-1 inline-flex items-center gap-1 text-xl font-bold"
            :class="forecast.growthRate >= 0
              ? 'text-green-600 dark:text-green-400'
              : 'text-red-600 dark:text-red-400'"
          >
            <ArrowTrendingUpIcon v-if="forecast.growthRate >= 0" class="h-5 w-5" />
            <ArrowTrendingDownIcon v-else class="h-5 w-5" />
            {{ forecast.growthRate >= 0 ? '+' : '' }}{{ forecast.growthRate }}%
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('revenueForecaster.confidence') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ Math.round(forecast.confidence * 100) }}%
          </p>
          <div class="mt-2 h-1.5 w-full rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-1.5 rounded-full transition-all duration-500"
              :class="forecast.confidence >= 0.7
                ? 'bg-green-500'
                : forecast.confidence >= 0.4
                  ? 'bg-yellow-500'
                  : 'bg-red-500'"
              :style="{ width: `${forecast.confidence * 100}%` }"
            />
          </div>
        </div>
      </div>

      <!-- Forecast Chart -->
      <div class="mb-6 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('revenueForecaster.chartTitle') }}
        </h2>
        <ForecastChart :chart-data="displayChartData" />
      </div>

      <!-- Scenario Selector -->
      <div class="mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('revenueForecaster.scenarioTitle') }}
        </h2>
        <div class="grid gap-4 tablet:grid-cols-3">
          <ScenarioCard
            v-for="scenario in forecast.scenarios"
            :key="scenario.id"
            :scenario="scenario"
            :selected="selectedScenario === scenario.id"
            @select="handleScenarioSelect(scenario.id)"
          />
        </div>
      </div>

      <!-- Revenue Breakdown -->
      <div class="mb-6 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('revenueForecaster.breakdownTitle') }}
        </h2>
        <BreakdownTable :breakdowns="forecast.breakdowns" />
      </div>
    </template>

    <!-- Empty State -->
    <div
      v-else
      class="flex flex-col items-center justify-center rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 shadow-sm"
    >
      <ChartBarIcon class="mb-4 h-12 w-12 text-gray-300 dark:text-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ $t('revenueForecaster.emptyState') }}
      </p>
      <button
        class="btn-primary mt-4 inline-flex items-center gap-2"
        :disabled="generating"
        @click="handleGenerate"
      >
        <SparklesIcon class="h-4 w-4" />
        {{ $t('revenueForecaster.generateButton') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  SparklesIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import ForecastChart from '@/components/revenueforecaster/ForecastChart.vue'
import ScenarioCard from '@/components/revenueforecaster/ScenarioCard.vue'
import BreakdownTable from '@/components/revenueforecaster/BreakdownTable.vue'
import { useRevenueForecasterStore } from '@/stores/revenueForecaster'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { ForecastPeriod } from '@/types/revenueForecaster'

const { t } = useI18n({ useScope: 'global' })
const store = useRevenueForecasterStore()
const { balance: creditBalance, checkAndUse } = useCredit()
const notification = useNotification()

const {
  forecast,
  selectedPeriod,
  selectedScenario,
  loading,
  generating,
  totalForecastRevenue,
  activeScenario,
} = storeToRefs(store)

const periods: { value: ForecastPeriod; label: string }[] = [
  { value: '1M', label: '1M' },
  { value: '3M', label: '3M' },
  { value: '6M', label: '6M' },
  { value: '1Y', label: '1Y' },
]

const displayChartData = computed(() => {
  if (activeScenario.value) {
    return activeScenario.value.forecastData
  }
  return forecast.value?.chartData ?? []
})

function formatRevenue(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}${t('revenueForecaster.manwon')}`
  }
  return `${value.toLocaleString()}${t('revenueForecaster.won')}`
}

function handlePeriodChange(period: ForecastPeriod) {
  selectedPeriod.value = period
  store.fetchForecast()
}

function handleScenarioSelect(scenarioId: number) {
  selectedScenario.value = scenarioId
}

async function handleGenerate() {
  const canUse = await checkAndUse(5, t('revenueForecaster.title'))
  if (!canUse) return

  try {
    await store.generateForecast({
      period: selectedPeriod.value,
      includeScenarios: true,
    })
    notification.success(t('revenueForecaster.generateSuccess'))
  } catch {
    notification.error(t('revenueForecaster.generateError'))
  }
}

onMounted(() => {
  store.fetchForecast()
})
</script>
