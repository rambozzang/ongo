<script setup lang="ts">
import { computed } from 'vue'
import type { ContentVariant } from '@/types/contentAbAnalyzer'

interface Props {
  variantA: ContentVariant
  variantB: ContentVariant
}

const props = defineProps<Props>()

interface MetricItem {
  label: string
  key: string
  valueA: number
  valueB: number
  format: (v: number) => string
}

const metrics = computed<MetricItem[]>(() => [
  {
    label: '조회수',
    key: 'views',
    valueA: props.variantA.views,
    valueB: props.variantB.views,
    format: formatNumber,
  },
  {
    label: '좋아요',
    key: 'likes',
    valueA: props.variantA.likes,
    valueB: props.variantB.likes,
    format: formatNumber,
  },
  {
    label: '댓글',
    key: 'comments',
    valueA: props.variantA.comments,
    valueB: props.variantB.comments,
    format: formatNumber,
  },
  {
    label: 'CTR',
    key: 'ctr',
    valueA: props.variantA.ctr,
    valueB: props.variantB.ctr,
    format: (v: number) => `${v.toFixed(1)}%`,
  },
  {
    label: '평균 시청시간',
    key: 'watchTime',
    valueA: props.variantA.avgWatchTime,
    valueB: props.variantB.avgWatchTime,
    format: formatSeconds,
  },
])

function getBarWidths(valueA: number, valueB: number): { widthA: number; widthB: number } {
  const maxVal = Math.max(valueA, valueB, 1)
  return {
    widthA: (valueA / maxVal) * 100,
    widthB: (valueB / maxVal) * 100,
  }
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}

function formatSeconds(seconds: number): string {
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  return `${min}분 ${sec}초`
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header: Variant labels -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center gap-2">
        <span class="inline-flex items-center justify-center w-6 h-6 rounded-full bg-blue-500 text-white text-xs font-bold">A</span>
        <span class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{{ variantA.label }}</span>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{{ variantB.label }}</span>
        <span class="inline-flex items-center justify-center w-6 h-6 rounded-full bg-orange-500 text-white text-xs font-bold">B</span>
      </div>
    </div>

    <!-- Metric bars -->
    <div class="space-y-4">
      <div v-for="metric in metrics" :key="metric.key">
        <div class="flex items-center justify-between mb-1">
          <span class="text-xs font-semibold text-blue-600 dark:text-blue-400">{{ metric.format(metric.valueA) }}</span>
          <span class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ metric.label }}</span>
          <span class="text-xs font-semibold text-orange-600 dark:text-orange-400">{{ metric.format(metric.valueB) }}</span>
        </div>
        <div class="flex items-center gap-1">
          <!-- Bar A (right-aligned) -->
          <div class="flex-1 flex justify-end">
            <div
              class="h-3 rounded-l-full bg-blue-500 dark:bg-blue-400 transition-all duration-500"
              :style="{ width: `${getBarWidths(metric.valueA, metric.valueB).widthA}%` }"
            ></div>
          </div>
          <!-- Divider -->
          <div class="w-px h-5 bg-gray-300 dark:bg-gray-600 flex-shrink-0"></div>
          <!-- Bar B (left-aligned) -->
          <div class="flex-1">
            <div
              class="h-3 rounded-r-full bg-orange-500 dark:bg-orange-400 transition-all duration-500"
              :style="{ width: `${getBarWidths(metric.valueA, metric.valueB).widthB}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
