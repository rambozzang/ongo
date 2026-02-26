<script setup lang="ts">
import {
  ClockIcon,
  EyeIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'
import type { OptimalTimeSlot } from '@/types/calendarInsights'

defineProps<{
  slot: OptimalTimeSlot
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

const getScoreBarColor = (score: number) => {
  if (score >= 90) return 'bg-green-500'
  if (score >= 75) return 'bg-blue-500'
  if (score >= 60) return 'bg-yellow-500'
  return 'bg-red-500'
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: 요일 + 시간 -->
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <ClockIcon class="h-5 w-5 text-primary-500" />
        <span class="text-base font-bold text-gray-900 dark:text-gray-100">
          {{ dayOfWeekLabels[slot.dayOfWeek] || slot.dayOfWeek }}
        </span>
        <span class="rounded-lg bg-primary-50 px-2.5 py-1 text-sm font-semibold text-primary-700 dark:bg-primary-900/20 dark:text-primary-300">
          {{ slot.hour }}:00
        </span>
      </div>
      <span class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ slot.score }}점
      </span>
    </div>

    <!-- 점수 바 -->
    <div class="mb-4">
      <div class="h-2.5 w-full rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-2.5 rounded-full transition-all duration-500"
          :class="getScoreBarColor(slot.score)"
          :style="{ width: `${slot.score}%` }"
        />
      </div>
    </div>

    <!-- 메트릭 -->
    <div class="mb-3 flex items-center gap-6">
      <div class="flex items-center gap-1.5">
        <EyeIcon class="h-4 w-4 text-gray-400" />
        <span class="text-xs text-gray-500 dark:text-gray-400">예상 조회수</span>
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(slot.expectedViews) }}
        </span>
      </div>
    </div>

    <!-- 이유 -->
    <div class="flex items-start gap-2 rounded-lg bg-gray-50 p-3 dark:bg-gray-800">
      <SparklesIcon class="mt-0.5 h-4 w-4 flex-shrink-0 text-yellow-500" />
      <p class="text-xs leading-relaxed text-gray-600 dark:text-gray-400">
        {{ slot.reason }}
      </p>
    </div>
  </div>
</template>
