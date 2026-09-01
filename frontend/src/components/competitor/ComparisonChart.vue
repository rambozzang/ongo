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

function formatValue(value: number | null, metric: string): string {
  // 측정 불가를 0 으로 그리면 막대가 바닥에 붙어 "가장 낮음" 으로 읽힌다.
  if (value === null) return '측정 불가'

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

function getPercentage(value: number | null, maxValue: number): number {
  if (value === null || maxValue === 0) return 0
  return (value / maxValue) * 100
}

function getMaxValue(comparison: CompetitorComparison): number {
  return Math.max(comparison.myValue ?? 0, comparison.competitorValue ?? 0)
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
        <!--
          비교 불가일 때는 증감 배지를 그리지 않는다. 색(초록/빨강)은 그 자체로
          우열을 주장하는데, 측정하지 않은 지표에 대해 그런 주장을 할 수 없다.
        -->
        <div v-if="comparison.comparable && comparison.differencePercent !== null" class="flex items-center space-x-2 text-body">
          <span
            data-testid="comparison-diff"
            :class="[
              'font-semibold',
              comparison.differencePercent > 0
                ? 'text-success-strong'
                : comparison.differencePercent < 0
                ? 'text-error-strong'
                : 'text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ comparison.differencePercent > 0 ? '+' : '' }}{{ comparison.differencePercent }}%
          </span>
        </div>
      </div>

      <!-- 비교할 수 없는 지표는 막대를 그리지 않는다. 0 폭 막대는 "가장 낮음" 이다. -->
      <p
        v-if="!comparison.comparable"
        data-testid="comparison-unavailable"
        class="text-body text-gray-500 dark:text-gray-400"
      >
        {{ comparison.unavailableReason ?? '측정할 수 없어 비교할 수 없습니다' }}
      </p>

      <!-- My channel bar -->
      <div v-if="comparison.comparable" class="space-y-1">
        <div class="flex items-center justify-between text-body">
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
      <div v-if="comparison.comparable" class="space-y-1">
        <div class="flex items-center justify-between text-body">
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
