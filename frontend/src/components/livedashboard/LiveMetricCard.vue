<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: icon + label -->
    <div class="mb-3 flex items-center gap-2">
      <component
        :is="metricIcon"
        class="h-5 w-5 text-gray-500 dark:text-gray-400"
      />
      <span class="text-sm font-medium text-gray-600 dark:text-gray-400">
        {{ $t(`liveDashboard.metricType.${metric.type}`) }}
      </span>
    </div>

    <!-- Current value -->
    <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
      {{ formattedCurrentValue }}
    </p>

    <!-- Change badge + previous comparison -->
    <div class="mt-2 flex items-center gap-2">
      <span
        class="inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-xs font-medium"
        :class="changeBadgeClass"
      >
        <ArrowTrendingUpIcon v-if="metric.trend === 'UP'" class="h-3 w-3" />
        <ArrowTrendingDownIcon v-if="metric.trend === 'DOWN'" class="h-3 w-3" />
        <MinusIcon v-if="metric.trend === 'STABLE'" class="h-3 w-3" />
        {{ metric.changePercent >= 0 ? '+' : '' }}{{ metric.changePercent.toFixed(1) }}%
      </span>
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ $t('liveDashboard.previousValue', { value: formattedPreviousValue }) }}
      </span>
    </div>

    <!-- Sparkline -->
    <div class="mt-3">
      <SparklineChart
        :points="metric.history"
        :trend="metric.trend"
        :width="200"
        :height="40"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  EyeIcon,
  UsersIcon,
  HeartIcon,
  ChatBubbleLeftIcon,
  ClockIcon,
  CurrencyDollarIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
} from '@heroicons/vue/24/outline'
import SparklineChart from './SparklineChart.vue'
import type { LiveMetric } from '@/types/liveDashboard'

const props = defineProps<{
  metric: LiveMetric
}>()

const metricIcon = computed(() => {
  switch (props.metric.type) {
    case 'VIEWS': return EyeIcon
    case 'SUBSCRIBERS': return UsersIcon
    case 'LIKES': return HeartIcon
    case 'COMMENTS': return ChatBubbleLeftIcon
    case 'WATCH_TIME': return ClockIcon
    case 'REVENUE': return CurrencyDollarIcon
    default: return EyeIcon
  }
})

function formatValue(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}

const formattedCurrentValue = computed(() => formatValue(props.metric.currentValue))
const formattedPreviousValue = computed(() => formatValue(props.metric.previousValue))

const changeBadgeClass = computed(() => {
  switch (props.metric.trend) {
    case 'UP':
      return 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400'
    case 'DOWN':
      return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
  }
})
</script>
