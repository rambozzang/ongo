<template>
  <div
    class="rounded-xl border bg-white p-4 shadow-sm transition-colors"
    :class="[
      type === 'best'
        ? 'border-green-200 dark:border-green-800 dark:bg-gray-900'
        : 'border-red-200 dark:border-red-800 dark:bg-gray-900',
    ]"
  >
    <div class="flex items-center gap-3">
      <!-- Rank badge -->
      <div
        class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-sm font-bold"
        :class="[
          type === 'best'
            ? 'bg-green-100 text-green-700 dark:bg-green-900/50 dark:text-green-400'
            : 'bg-red-100 text-red-700 dark:bg-red-900/50 dark:text-red-400',
        ]"
      >
        {{ rank }}
      </div>

      <!-- Day + Hour -->
      <div class="flex-1">
        <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ dayLabel }} {{ slotData.hour }}{{ $t('performanceHeatmap.hourSuffix') }}
        </div>
        <div class="text-xs text-gray-500 dark:text-gray-400">
          {{ $t(`performanceHeatmap.rank`, { rank }) }}
        </div>
      </div>

      <!-- Value -->
      <div class="text-right">
        <div
          class="text-lg font-bold"
          :class="[
            type === 'best'
              ? 'text-green-600 dark:text-green-400'
              : 'text-red-600 dark:text-red-400',
          ]"
        >
          {{ formattedValue }}
        </div>
        <div class="text-xs text-gray-400 dark:text-gray-500">
          {{ metricLabel }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DayOfWeek } from '@/types/performanceHeatmap'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  slotData: { day: DayOfWeek; hour: number; value: number }
  rank: number
  type: 'best' | 'worst'
  metric: string
}>()

const dayLabel = computed(() => {
  const map: Record<DayOfWeek, string> = {
    MON: t('performanceHeatmap.dayMon'),
    TUE: t('performanceHeatmap.dayTue'),
    WED: t('performanceHeatmap.dayWed'),
    THU: t('performanceHeatmap.dayThu'),
    FRI: t('performanceHeatmap.dayFri'),
    SAT: t('performanceHeatmap.daySat'),
    SUN: t('performanceHeatmap.daySun'),
  }
  return map[props.slotData.day] ?? props.slotData.day
})

const metricLabel = computed(() => {
  const map: Record<string, string> = {
    VIEWS: t('performanceHeatmap.metricViews'),
    LIKES: t('performanceHeatmap.metricLikes'),
    COMMENTS: t('performanceHeatmap.metricComments'),
    CTR: t('performanceHeatmap.metricCtr'),
    WATCH_TIME: t('performanceHeatmap.metricWatchTime'),
  }
  return map[props.metric] ?? props.metric
})

const formattedValue = computed(() => {
  if (props.metric === 'CTR') return `${props.slotData.value.toFixed(1)}%`
  if (props.slotData.value >= 1000) return `${(props.slotData.value / 1000).toFixed(1)}K`
  return props.slotData.value.toLocaleString()
})
</script>
