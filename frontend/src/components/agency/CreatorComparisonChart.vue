<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
    <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('agency.performanceComparison') }}
      </h3>
      <div class="flex items-center gap-2">
        <select
          v-model="metric"
          class="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300"
        >
          <option value="views">{{ $t('agency.metric.views') }}</option>
          <option value="engagement">{{ $t('agency.metric.engagement') }}</option>
        </select>
      </div>
    </div>

    <!-- Legend -->
    <div class="mb-4 flex flex-wrap gap-3">
      <div
        v-for="comp in comparisons"
        :key="comp.creatorId"
        class="flex items-center gap-1.5"
      >
        <span class="h-3 w-3 rounded-full" :style="{ backgroundColor: comp.color }" />
        <span class="text-xs text-gray-600 dark:text-gray-400">{{ comp.creatorName }}</span>
      </div>
    </div>

    <!-- Chart Area -->
    <div class="relative h-64">
      <!-- Y-axis labels -->
      <div class="absolute left-0 top-0 flex h-full flex-col justify-between pr-2">
        <span class="text-[10px] text-gray-400 dark:text-gray-500">{{ formatAxis(maxValue) }}</span>
        <span class="text-[10px] text-gray-400 dark:text-gray-500">{{ formatAxis(maxValue / 2) }}</span>
        <span class="text-[10px] text-gray-400 dark:text-gray-500">0</span>
      </div>

      <!-- Bars -->
      <div class="ml-10 flex h-full items-end gap-1">
        <div
          v-for="(group, gIdx) in chartGroups"
          :key="gIdx"
          class="flex flex-1 flex-col items-center gap-0"
        >
          <div class="flex w-full items-end justify-center gap-0.5" style="height: calc(100% - 24px)">
            <div
              v-for="(bar, bIdx) in group.bars"
              :key="bIdx"
              class="w-3 rounded-t transition-all duration-300 sm:w-4"
              :style="{ height: `${bar.heightPercent}%`, backgroundColor: bar.color }"
            />
          </div>
          <span class="mt-1 text-[10px] text-gray-400 dark:text-gray-500">
            {{ group.label }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { CreatorComparison } from '@/types/agency'

const props = defineProps<{
  comparisons: CreatorComparison[]
}>()

const metric = ref<'views' | 'engagement'>('views')

const maxValue = computed(() => {
  let max = 0
  for (const comp of props.comparisons) {
    for (const d of comp.data) {
      const val = metric.value === 'views' ? d.views : d.engagement
      if (val > max) max = val
    }
  }
  return max || 1
})

const chartGroups = computed(() => {
  if (props.comparisons.length === 0) return []

  const dates = props.comparisons[0].data.map((d) => d.date)
  return dates.map((date) => ({
    label: date.split('-')[2],
    bars: props.comparisons.map((comp) => {
      const point = comp.data.find((d) => d.date === date)
      const val = point ? (metric.value === 'views' ? point.views : point.engagement) : 0
      return {
        color: comp.color,
        heightPercent: Math.max((val / maxValue.value) * 100, 2),
      }
    }),
  }))
})

function formatAxis(value: number): string {
  if (metric.value === 'engagement') return `${value.toFixed(1)}%`
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(0)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(0)}K`
  return value.toLocaleString()
}
</script>
