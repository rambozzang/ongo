<script setup lang="ts">
import { computed } from 'vue'
import type { RevenueProjection } from '@/types/revenueAnalyzer'
import RevenueSourceBadge from './RevenueSourceBadge.vue'

const props = defineProps<{
  projection: RevenueProjection
}>()

const formatKRW = (amount: number) => {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(amount)
}

const changePercent = computed(() => {
  if (props.projection.currentMonthly === 0) return 0
  return Math.round(
    ((props.projection.projectedMonthly - props.projection.currentMonthly) / props.projection.currentMonthly) * 100,
  )
})

const confidenceColor = computed(() => {
  const c = props.projection.confidence
  if (c >= 80) return 'text-green-600 dark:text-green-400'
  if (c >= 60) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

const confidenceBarColor = computed(() => {
  const c = props.projection.confidence
  if (c >= 80) return 'bg-green-500 dark:bg-green-400'
  if (c >= 60) return 'bg-yellow-500 dark:bg-yellow-400'
  return 'bg-red-500 dark:bg-red-400'
})
</script>

<template>
  <tr class="border-b border-gray-100 transition-colors hover:bg-gray-50 dark:border-gray-800 dark:hover:bg-gray-800/50">
    <!-- Source -->
    <td class="px-4 py-3">
      <RevenueSourceBadge :source="projection.source" />
    </td>

    <!-- Current monthly -->
    <td class="px-4 py-3 text-sm font-medium text-gray-900 dark:text-gray-100 text-right">
      {{ formatKRW(projection.currentMonthly) }}
    </td>

    <!-- Projected monthly -->
    <td class="px-4 py-3 text-right">
      <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ formatKRW(projection.projectedMonthly) }}
      </span>
      <span
        v-if="changePercent !== 0"
        :class="changePercent > 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
        class="ml-1.5 text-xs font-medium"
      >
        {{ changePercent > 0 ? '+' : '' }}{{ changePercent }}%
      </span>
    </td>

    <!-- Confidence -->
    <td class="px-4 py-3">
      <div class="flex items-center gap-2">
        <div class="h-1.5 w-16 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            :class="confidenceBarColor"
            class="h-full rounded-full transition-all duration-300"
            :style="{ width: `${projection.confidence}%` }"
          />
        </div>
        <span :class="confidenceColor" class="text-xs font-semibold">{{ projection.confidence }}%</span>
      </div>
    </td>

    <!-- Factors -->
    <td class="px-4 py-3">
      <div class="flex flex-wrap gap-1">
        <span
          v-for="factor in projection.factors"
          :key="factor"
          class="rounded-md bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-400"
        >
          {{ factor }}
        </span>
      </div>
    </td>
  </tr>
</template>
