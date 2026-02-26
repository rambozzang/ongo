<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  CalendarDaysIcon,
  ChartBarIcon,
  ClockIcon,
  ArrowTrendingUpIcon,
} from '@heroicons/vue/24/outline'
import { useCalendarInsightsStore } from '@/stores/calendarInsights'
import InsightCard from '@/components/calendarinsights/InsightCard.vue'
import OptimalTimeCard from '@/components/calendarinsights/OptimalTimeCard.vue'
import PatternChart from '@/components/calendarinsights/PatternChart.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useCalendarInsightsStore()
const { insights, optimalTimeSlots, uploadPatterns, summary, isLoading } = storeToRefs(store)

const dayOfWeekLabels: Record<string, string> = {
  MON: '월요일',
  TUE: '화요일',
  WED: '수요일',
  THU: '목요일',
  FRI: '금요일',
  SAT: '토요일',
  SUN: '일요일',
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
            콘텐츠 캘린더 인사이트
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          업로드 패턴 분석과 최적 시간대 추천으로 콘텐츠 전략을 최적화하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CalendarDaysIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 업로드</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalUploads.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ArrowTrendingUpIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">주간 평균</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgUploadsPerWeek.toFixed(1) }}건
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ClockIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">최적 요일</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ dayOfWeekLabels[summary.bestDay] || summary.bestDay || '-' }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChartBarIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">일관성 점수</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.consistencyScore }}점
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
          최적 업로드 시간
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
            최적 시간 데이터가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            콘텐츠를 업로드하면 최적 시간이 분석됩니다
          </p>
        </div>
      </div>

      <!-- 일별 인사이트 그리드 -->
      <div class="mb-8">
        <h2 class="mb-4 flex items-center gap-2 text-lg font-bold text-gray-900 dark:text-gray-100">
          <CalendarDaysIcon class="h-5 w-5 text-primary-500" />
          일별 인사이트
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
            일별 인사이트 데이터가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            업로드 기록이 쌓이면 일별 인사이트를 확인할 수 있습니다
          </p>
        </div>
      </div>

      <!-- 업로드 패턴 섹션 -->
      <div class="mb-8">
        <h2 class="mb-4 flex items-center gap-2 text-lg font-bold text-gray-900 dark:text-gray-100">
          <ChartBarIcon class="h-5 w-5 text-primary-500" />
          플랫폼별 업로드 패턴
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
            업로드 패턴 데이터가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            여러 플랫폼에 콘텐츠를 업로드하면 패턴이 분석됩니다
          </p>
        </div>
      </div>
    </template>
  </div>
</template>
