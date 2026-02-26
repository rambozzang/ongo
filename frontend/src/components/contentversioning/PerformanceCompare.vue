<script setup lang="ts">
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
} from '@heroicons/vue/24/outline'
import type { VersionPerformance } from '@/types/contentVersioning'

defineProps<{
  before: VersionPerformance
  after: VersionPerformance
}>()

const formatNumber = (n: number) => n.toLocaleString('ko-KR')

const calcChange = (before: number, after: number) => {
  if (before === 0) return after > 0 ? 100 : 0
  return ((after - before) / before) * 100
}

const getChangeColor = (change: number) => {
  if (change > 0) return 'text-green-600 dark:text-green-400'
  if (change < 0) return 'text-red-600 dark:text-red-400'
  return 'text-gray-500 dark:text-gray-400'
}

const metrics = [
  { key: 'views' as const, label: '조회수' },
  { key: 'likes' as const, label: '좋아요' },
  { key: 'engagement' as const, label: '참여율', suffix: '%' },
  { key: 'ctr' as const, label: 'CTR', suffix: '%' },
]
</script>

<template>
  <div class="rounded-lg border border-gray-100 bg-gray-50 p-3 dark:border-gray-800 dark:bg-gray-800/50">
    <p class="mb-2 text-xs font-semibold text-gray-500 dark:text-gray-400">성과 비교</p>
    <div class="grid grid-cols-2 gap-2 tablet:grid-cols-4">
      <div
        v-for="metric in metrics"
        :key="metric.key"
        class="text-center"
      >
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ metric.label }}</p>
        <div class="mt-0.5 flex items-center justify-center gap-1">
          <span class="text-xs text-gray-400 line-through">
            {{ metric.suffix ? before[metric.key].toFixed(1) + metric.suffix : formatNumber(before[metric.key]) }}
          </span>
          <span class="text-xs font-bold text-gray-900 dark:text-gray-100">
            {{ metric.suffix ? after[metric.key].toFixed(1) + metric.suffix : formatNumber(after[metric.key]) }}
          </span>
        </div>
        <div class="mt-0.5 flex items-center justify-center gap-0.5">
          <component
            :is="calcChange(before[metric.key], after[metric.key]) >= 0 ? ArrowTrendingUpIcon : ArrowTrendingDownIcon"
            class="h-3 w-3"
            :class="getChangeColor(calcChange(before[metric.key], after[metric.key]))"
          />
          <span
            class="text-xs font-semibold"
            :class="getChangeColor(calcChange(before[metric.key], after[metric.key]))"
          >
            {{ calcChange(before[metric.key], after[metric.key]) >= 0 ? '+' : '' }}{{ calcChange(before[metric.key], after[metric.key]).toFixed(1) }}%
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
