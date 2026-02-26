<script setup lang="ts">
import {
  ChartBarIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import type { UploadPattern } from '@/types/calendarInsights'

defineProps<{
  pattern: UploadPattern
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

const platformConfig: Record<string, { label: string; bg: string; text: string; bar: string }> = {
  YOUTUBE: { label: 'YouTube', bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', bar: 'bg-red-500' },
  TIKTOK: { label: 'TikTok', bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300', bar: 'bg-gray-600' },
  INSTAGRAM: { label: 'Instagram', bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300', bar: 'bg-pink-500' },
  NAVER_CLIP: { label: 'Naver Clip', bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', bar: 'bg-green-500' },
}

const getPlatform = (p: string) => platformConfig[p] ?? platformConfig.YOUTUBE

const getConsistencyColor = (c: number) => {
  if (c >= 80) return 'bg-green-500'
  if (c >= 60) return 'bg-blue-500'
  if (c >= 40) return 'bg-yellow-500'
  return 'bg-red-500'
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: 플랫폼명 + 총 업로드 -->
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <ChartBarIcon class="h-5 w-5 text-primary-500" />
        <span
          :class="[getPlatform(pattern.platform).bg, getPlatform(pattern.platform).text]"
          class="rounded-full px-3 py-1 text-sm font-semibold"
        >
          {{ getPlatform(pattern.platform).label }}
        </span>
      </div>
      <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
        총 {{ pattern.totalUploads }}건
      </span>
    </div>

    <!-- 상세 메트릭 -->
    <div class="mb-4 grid grid-cols-2 gap-3">
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">주간 평균</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ pattern.avgUploadsPerWeek.toFixed(1) }}건
        </p>
      </div>
      <div>
        <div class="flex items-center gap-1">
          <ClockIcon class="h-3 w-3 text-gray-400" />
          <p class="text-xs text-gray-500 dark:text-gray-400">활발 시간</p>
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ pattern.mostActiveHour }}시
        </p>
      </div>
    </div>

    <!-- 활발 요일 -->
    <div class="mb-4">
      <p class="mb-1 text-xs text-gray-500 dark:text-gray-400">가장 활발한 요일</p>
      <span class="rounded-full bg-primary-50 px-2.5 py-1 text-xs font-semibold text-primary-700 dark:bg-primary-900/20 dark:text-primary-300">
        {{ dayOfWeekLabels[pattern.mostActiveDay] || pattern.mostActiveDay }}
      </span>
    </div>

    <!-- 일관성 프로그레스바 -->
    <div class="border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="mb-1.5 flex items-center justify-between">
        <p class="text-xs text-gray-500 dark:text-gray-400">일관성</p>
        <span class="text-xs font-semibold text-gray-700 dark:text-gray-300">{{ pattern.consistency }}%</span>
      </div>
      <div class="h-2 w-full rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-2 rounded-full transition-all duration-500"
          :class="getConsistencyColor(pattern.consistency)"
          :style="{ width: `${pattern.consistency}%` }"
        />
      </div>
    </div>
  </div>
</template>
