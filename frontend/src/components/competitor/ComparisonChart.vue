<script setup lang="ts">
// vue imports
import type { CompetitorComparison } from '@/types/competitor'

interface Props {
  comparisons: CompetitorComparison[]
  myName?: string
  competitorName?: string
}

withDefaults(defineProps<Props>(), {
  myName: '내 채널',
  competitorName: '경쟁 채널',
})

function formatValue(value: number, metric: string): string {
  if (metric === '참여율' || metric === '성장률') {
    return `${value.toFixed(1)}%`
  }

  if (value >= 1000000) {
    return `${(value / 1000000).toFixed(1)}M`
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(1)}K`
  }
  return value.toString()
}

function getPercentage(value: number, maxValue: number): number {
  if (maxValue === 0) return 0
  return (value / maxValue) * 100
}

function getMaxValue(comparison: CompetitorComparison): number {
  return Math.max(comparison.myValue, comparison.competitorValue)
}
</script>

<template>
  <div class="space-y-6">
    <div
      v-for="comparison in comparisons"
      :key="comparison.metric"
      class="space-y-2"
    >
      <!-- Metric label -->
      <div class="flex items-center justify-between">
        <h4 class="font-medium text-gray-900 dark:text-white">
          {{ comparison.metric }}
        </h4>
        <div class="flex items-center space-x-2 text-sm">
          <span
            :class="[
              'font-semibold',
              comparison.differencePercent > 0
                ? 'text-green-600 dark:text-green-400'
                : comparison.differencePercent < 0
                ? 'text-red-600 dark:text-red-400'
                : 'text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ comparison.differencePercent > 0 ? '+' : '' }}{{ comparison.differencePercent }}%
          </span>
        </div>
      </div>

      <!-- My channel bar -->
      <div class="space-y-1">
        <div class="flex items-center justify-between text-sm">
          <span class="text-gray-600 dark:text-gray-400">{{ myName }}</span>
          <span class="font-medium text-gray-900 dark:text-white">
            {{ formatValue(comparison.myValue, comparison.metric) }}
          </span>
        </div>
        <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-3 overflow-hidden">
          <div
            class="bg-blue-600 dark:bg-blue-500 h-full rounded-full transition-all duration-500"
            :style="{
              width: `${getPercentage(comparison.myValue, getMaxValue(comparison))}%`,
            }"
          ></div>
        </div>
      </div>

      <!-- Competitor bar -->
      <div class="space-y-1">
        <div class="flex items-center justify-between text-sm">
          <span class="text-gray-600 dark:text-gray-400">{{ competitorName }}</span>
          <span class="font-medium text-gray-900 dark:text-white">
            {{ formatValue(comparison.competitorValue, comparison.metric) }}
          </span>
        </div>
        <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-3 overflow-hidden">
          <div
            class="bg-gray-400 dark:bg-gray-500 h-full rounded-full transition-all duration-500"
            :style="{
              width: `${getPercentage(comparison.competitorValue, getMaxValue(comparison))}%`,
            }"
          ></div>
        </div>
      </div>
    </div>
  </div>
</template>
