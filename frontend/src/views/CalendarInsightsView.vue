<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  CalendarDaysIcon,
  ChartBarIcon,
  ClockIcon,
  ArrowTrendingUpIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useCalendarInsightsStore } from '@/stores/calendarInsights'
import InsightCard from '@/components/calendarinsights/InsightCard.vue'
import OptimalTimeCard from '@/components/calendarinsights/OptimalTimeCard.vue'
import PatternChart from '@/components/calendarinsights/PatternChart.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const { t } = useLocale()
const store = useCalendarInsightsStore()
const { insights, optimalTimeSlots, uploadPatterns, summary, isLoading } = storeToRefs(store)

const dayOfWeekLabels: Record<string, string> = {
  MON: t('calendarInsights.mon'),
  TUE: t('calendarInsights.tue'),
  WED: t('calendarInsights.wed'),
  THU: t('calendarInsights.thu'),
  FRI: t('calendarInsights.fri'),
  SAT: t('calendarInsights.sat'),
  SUN: t('calendarInsights.sun'),
}

onMounted(() => {
  store.fetchInsights(2025, 3)
  store.fetchOptimalTimeSlots()
  store.fetchUploadPatterns()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ $t('calendarInsights.title') }}
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('calendarInsights.description') }}
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CalendarDaysIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('calendarInsights.totalUploads') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalUploads.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ArrowTrendingUpIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('calendarInsights.weeklyAvg') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgUploadsPerWeek.toFixed(1) }}{{ $t('calendarInsights.countSuffix') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ClockIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('calendarInsights.bestDay') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ dayOfWeekLabels[summary.bestDay] || summary.bestDay || '-' }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChartBarIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('calendarInsights.consistencyScore') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.consistencyScore }}{{ $t('calendarInsights.scoreSuffix') }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <template v-else>
      <!-- 최적 업로드 시간 섹션 -->
      <div class="mb-8">
        <h2 class="mb-4 flex items-center gap-2 text-lg font-bold text-gray-900 dark:text-gray-100">
          <ClockIcon class="h-5 w-5 text-primary-500" />
          {{ $t('calendarInsights.optimalUploadTime') }}
        </h2>

        <div v-if="optimalTimeSlots.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
          <OptimalTimeCard
            v-for="(slot, index) in optimalTimeSlots"
            :key="index"
            :slot="slot"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ClockIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('calendarInsights.noOptimalTimeTitle') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('calendarInsights.noOptimalTimeDesc') }}
          </p>
        </div>
      </div>

      <!-- 일별 인사이트 그리드 -->
      <div class="mb-8">
        <h2 class="mb-4 flex items-center gap-2 text-lg font-bold text-gray-900 dark:text-gray-100">
          <CalendarDaysIcon class="h-5 w-5 text-primary-500" />
          {{ $t('calendarInsights.dailyInsights') }}
        </h2>

        <div v-if="insights.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <InsightCard
            v-for="insight in insights"
            :key="insight.id"
            :insight="insight"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <CalendarDaysIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('calendarInsights.noDailyInsightsTitle') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('calendarInsights.noDailyInsightsDesc') }}
          </p>
        </div>
      </div>

      <!-- 업로드 패턴 섹션 -->
      <div class="mb-8">
        <h2 class="mb-4 flex items-center gap-2 text-lg font-bold text-gray-900 dark:text-gray-100">
          <ChartBarIcon class="h-5 w-5 text-primary-500" />
          {{ $t('calendarInsights.uploadPatterns') }}
        </h2>

        <div v-if="uploadPatterns.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
          <PatternChart
            v-for="(pattern, index) in uploadPatterns"
            :key="index"
            :pattern="pattern"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ChartBarIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('calendarInsights.noUploadPatternsTitle') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('calendarInsights.noUploadPatternsDesc') }}
          </p>
        </div>
      </div>
    </template>
  </div>
</template>
