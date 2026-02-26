<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
} from '@heroicons/vue/24/outline'
import type { BenchmarkResult } from '@/types/creatorBenchmark'

interface Props {
  result: BenchmarkResult
}

const props = defineProps<Props>()

const metricLabel = computed(() => {
  const labels: Record<string, string> = {
    SUBSCRIBERS: '구독자 수',
    AVG_VIEWS: '평균 조회수',
    ENGAGEMENT_RATE: '참여율',
    WATCH_TIME: '시청 시간',
    LIKES: '좋아요',
    COMMENTS: '댓글',
  }
  return labels[props.result.metric] || props.result.metric
})

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.result.platform] || props.result.platform
})

const platformBadgeColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.result.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const trendConfig = computed(() => {
  const configs: Record<string, { icon: typeof ArrowTrendingUpIcon; color: string; label: string }> = {
    UP: { icon: ArrowTrendingUpIcon, color: 'text-green-600 dark:text-green-400', label: '상승' },
    DOWN: { icon: ArrowTrendingDownIcon, color: 'text-red-600 dark:text-red-400', label: '하락' },
    STABLE: { icon: MinusIcon, color: 'text-gray-500 dark:text-gray-400', label: '유지' },
  }
  return configs[props.result.trend] || configs.STABLE
})

const percentileColor = computed(() => {
  const p = props.result.percentile
  if (p >= 75) return 'text-green-600 dark:text-green-400'
  if (p >= 50) return 'text-blue-600 dark:text-blue-400'
  if (p >= 25) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

const percentileBarColor = computed(() => {
  const p = props.result.percentile
  if (p >= 75) return 'bg-green-500 dark:bg-green-400'
  if (p >= 50) return 'bg-blue-500 dark:bg-blue-400'
  if (p >= 25) return 'bg-yellow-500 dark:bg-yellow-400'
  return 'bg-red-500 dark:bg-red-400'
})

const isAboveAvg = computed(() => props.result.myValue >= props.result.avgValue)

const formatValue = (value: number): string => {
  if (props.result.metric === 'ENGAGEMENT_RATE') return `${value}%`
  if (value >= 10000) return `${(value / 10000).toFixed(1)}만`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}K`
  return value.toLocaleString()
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
    <!-- Header -->
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ metricLabel }}
        </h3>
        <component
          :is="trendConfig.icon"
          :class="['h-4 w-4', trendConfig.color]"
          :title="trendConfig.label"
        />
      </div>
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', platformBadgeColor]">
        {{ platformLabel }}
      </span>
    </div>

    <!-- Values Comparison -->
    <div class="mb-4 grid grid-cols-3 gap-2">
      <div class="rounded-lg bg-blue-50 px-3 py-2 text-center dark:bg-blue-900/20">
        <p class="text-xs text-gray-500 dark:text-gray-400">내 값</p>
        <p :class="['text-base font-bold', isAboveAvg ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400']">
          {{ formatValue(result.myValue) }}
        </p>
      </div>
      <div class="rounded-lg bg-gray-50 px-3 py-2 text-center dark:bg-gray-900/50">
        <p class="text-xs text-gray-500 dark:text-gray-400">평균</p>
        <p class="text-base font-bold text-gray-700 dark:text-gray-300">
          {{ formatValue(result.avgValue) }}
        </p>
      </div>
      <div class="rounded-lg bg-yellow-50 px-3 py-2 text-center dark:bg-yellow-900/20">
        <p class="text-xs text-gray-500 dark:text-gray-400">상위</p>
        <p class="text-base font-bold text-yellow-700 dark:text-yellow-400">
          {{ formatValue(result.topValue) }}
        </p>
      </div>
    </div>

    <!-- Percentile -->
    <div class="mb-2 flex items-center justify-between">
      <span class="text-xs font-medium text-gray-600 dark:text-gray-400">백분위</span>
      <span :class="['text-sm font-bold', percentileColor]">
        상위 {{ 100 - result.percentile }}%
      </span>
    </div>

    <!-- Percentile Progress Bar -->
    <div class="h-2.5 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
      <div
        :class="['h-full rounded-full transition-all duration-500', percentileBarColor]"
        :style="{ width: `${result.percentile}%` }"
      />
    </div>
  </div>
</template>
