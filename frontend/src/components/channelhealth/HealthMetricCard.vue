<script setup lang="ts">
import type { ChannelHealthMetric } from '@/types/channelHealth'
import HealthScoreGauge from './HealthScoreGauge.vue'

defineProps<{
  metric: ChannelHealthMetric
  selected?: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
}>()

const platformConfig: Record<string, { color: string; label: string }> = {
  YOUTUBE: { color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300', label: 'YouTube' },
  TIKTOK: { color: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300', label: 'TikTok' },
  INSTAGRAM: { color: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-300', label: 'Instagram' },
  NAVERCLIP: { color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300', label: 'Naver Clip' },
}

interface MetricBar {
  key: string
  label: string
  value: number
}

function getMetricBars(m: ChannelHealthMetric): MetricBar[] {
  return [
    { key: 'growth', label: '성장', value: m.growthScore },
    { key: 'engagement', label: '참여도', value: m.engagementScore },
    { key: 'consistency', label: '일관성', value: m.consistencyScore },
    { key: 'audience', label: '오디언스', value: m.audienceScore },
    { key: 'monetization', label: '수익화', value: m.monetizationScore },
  ]
}

function barColor(score: number): string {
  if (score >= 80) return 'bg-green-500'
  if (score >= 60) return 'bg-yellow-500'
  return 'bg-red-500'
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border bg-white p-5 shadow-sm transition-all hover:shadow-md dark:bg-gray-900"
    :class="
      selected
        ? 'border-primary-500 ring-1 ring-primary-500 dark:border-primary-400 dark:ring-primary-400'
        : 'border-gray-200 dark:border-gray-700'
    "
    @click="emit('select', metric.id)"
  >
    <!-- Header -->
    <div class="mb-4 flex items-center justify-between">
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ metric.channelName }}
        </h3>
        <span
          class="mt-1 inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium"
          :class="platformConfig[metric.platform]?.color ?? 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'"
        >
          {{ platformConfig[metric.platform]?.label ?? metric.platform }}
        </span>
      </div>
      <HealthScoreGauge :score="metric.overallScore" size="sm" />
    </div>

    <!-- Metric Bars -->
    <div class="space-y-2.5">
      <div
        v-for="bar in getMetricBars(metric)"
        :key="bar.key"
        class="flex items-center gap-2"
      >
        <span class="w-14 text-xs text-gray-500 dark:text-gray-400">{{ bar.label }}</span>
        <div class="flex-1 h-2 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
          <div
            :class="barColor(bar.value)"
            class="h-full rounded-full transition-all duration-500"
            :style="{ width: `${bar.value}%` }"
          />
        </div>
        <span class="w-7 text-right text-xs font-medium text-gray-700 dark:text-gray-300">
          {{ bar.value }}
        </span>
      </div>
    </div>
  </div>
</template>
