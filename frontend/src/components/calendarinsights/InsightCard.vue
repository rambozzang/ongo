<script setup lang="ts">
import {
  CalendarDaysIcon,
  EyeIcon,
  HeartIcon,
  ArrowUpCircleIcon,
} from '@heroicons/vue/24/outline'
import type { CalendarInsight } from '@/types/calendarInsights'

defineProps<{
  insight: CalendarInsight
}>()

const dayOfWeekLabels: Record<string, string> = {
  MON: '월요일',
  TUE: '화요일',
  WED: '수요일',
  THU: '목요일',
  FRI: '금요일',
  SAT: '토요일',
  SUN: '일요일',
}

const formatNumber = (n: number) => n.toLocaleString('ko-KR')

const getScoreColor = (score: number) => {
  if (score >= 90) return { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' }
  if (score >= 75) return { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300' }
  if (score >= 60) return { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' }
  return { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' }
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: 날짜 + 요일 + 점수 배지 -->
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <CalendarDaysIcon class="h-4 w-4 text-primary-500" />
        <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ insight.date }}
        </span>
        <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400">
          {{ dayOfWeekLabels[insight.dayOfWeek] || insight.dayOfWeek }}
        </span>
      </div>
      <span
        :class="[getScoreColor(insight.score).bg, getScoreColor(insight.score).text]"
        class="rounded-full px-2.5 py-0.5 text-xs font-bold"
      >
        {{ insight.score }}점
      </span>
    </div>

    <!-- 메트릭 -->
    <div class="mb-3 grid grid-cols-3 gap-3">
      <div>
        <div class="flex items-center gap-1">
          <ArrowUpCircleIcon class="h-3 w-3 text-gray-400" />
          <p class="text-xs text-gray-500 dark:text-gray-400">업로드</p>
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ insight.uploadCount }}건
        </p>
      </div>
      <div>
        <div class="flex items-center gap-1">
          <EyeIcon class="h-3 w-3 text-gray-400" />
          <p class="text-xs text-gray-500 dark:text-gray-400">평균 조회수</p>
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(insight.avgViews) }}
        </p>
      </div>
      <div>
        <div class="flex items-center gap-1">
          <HeartIcon class="h-3 w-3 text-gray-400" />
          <p class="text-xs text-gray-500 dark:text-gray-400">참여율</p>
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ insight.avgEngagement.toFixed(1) }}%
        </p>
      </div>
    </div>

    <!-- 시간대 표시 -->
    <div class="border-t border-gray-100 pt-3 dark:border-gray-800">
      <span class="text-xs text-gray-500 dark:text-gray-400">
        주요 업로드 시간:
        <span class="font-semibold text-gray-700 dark:text-gray-300">{{ insight.hour }}시</span>
      </span>
    </div>
  </div>
</template>
