<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import type { Competitor, MetricTrend } from '@/types/competitorAnalysis'

interface CompetitorMetrics {
  views: number
  viewsTrend: MetricTrend
  subscribers: number
  subscribersTrend: MetricTrend
  engagement: number
  engagementTrend: MetricTrend
  uploadFrequency: number
}

const props = defineProps<{
  competitor: Competitor
  metrics: CompetitorMetrics
}>()

const emit = defineEmits<{
  (e: 'remove', id: number): void
}>()

const { t } = useI18n({ useScope: 'global' })

function formatNumber(num: number): string {
  if (num >= 1_000_000) return `${(num / 1_000_000).toFixed(1)}M`
  if (num >= 1_000) return `${(num / 1_000).toFixed(1)}K`
  return num.toLocaleString()
}

function trendIcon(trend: MetricTrend) {
  if (trend === 'UP') return ArrowTrendingUpIcon
  if (trend === 'DOWN') return ArrowTrendingDownIcon
  return MinusIcon
}

function trendColor(trend: MetricTrend): string {
  if (trend === 'UP') return 'text-green-600 dark:text-green-400'
  if (trend === 'DOWN') return 'text-red-600 dark:text-red-400'
  return 'text-gray-400 dark:text-gray-500'
}

const platformBadges = computed(() =>
  props.competitor.platforms.map((p) => ({
    key: p,
    label: t(`competitorAnalysis.platform.${p}`),
    color: platformColor(p),
  })),
)

function platformColor(platform: string): string {
  switch (platform) {
    case 'YOUTUBE':
      return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'
    case 'TIKTOK':
      return 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400'
    case 'INSTAGRAM':
      return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400'
    case 'NAVER':
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
  }
}

const metricRows = computed(() => [
  {
    label: t('competitorAnalysis.views'),
    value: formatNumber(props.metrics.views),
    trend: props.metrics.viewsTrend,
  },
  {
    label: t('competitorAnalysis.subscribers'),
    value: formatNumber(props.metrics.subscribers),
    trend: props.metrics.subscribersTrend,
  },
  {
    label: t('competitorAnalysis.engagement'),
    value: `${props.metrics.engagement.toFixed(1)}%`,
    trend: props.metrics.engagementTrend,
  },
  {
    label: t('competitorAnalysis.uploadFrequency'),
    value: `${props.metrics.uploadFrequency}${t('competitorAnalysis.uploadsPerWeek')}`,
    trend: 'STABLE' as MetricTrend,
  },
])
</script>

<template>
  <div
    class="group relative rounded-lg border border-gray-200 bg-white p-4 transition-all hover:shadow-lg dark:border-gray-700 dark:bg-gray-800"
  >
    <!-- Remove button (visible on hover) -->
    <button
      class="absolute right-2 top-2 rounded-lg p-1.5 opacity-0 transition-opacity hover:bg-red-100 group-hover:opacity-100 dark:hover:bg-red-900/30"
      @click.stop="emit('remove', competitor.id)"
    >
      <XMarkIcon class="h-4 w-4 text-red-600 dark:text-red-400" />
    </button>

    <!-- Header: avatar + name + platforms -->
    <div class="mb-3 flex items-center gap-3">
      <img
        :src="competitor.avatarUrl"
        :alt="competitor.name"
        class="h-10 w-10 rounded-full object-cover"
      />
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-white">
          {{ competitor.name }}
        </h3>
        <div class="mt-1 flex flex-wrap gap-1">
          <span
            v-for="badge in platformBadges"
            :key="badge.key"
            :class="[
              'inline-block rounded-full px-2 py-0.5 text-[10px] font-medium',
              badge.color,
            ]"
          >
            {{ badge.label }}
          </span>
        </div>
      </div>
    </div>

    <!-- Metrics -->
    <div class="space-y-2">
      <div
        v-for="row in metricRows"
        :key="row.label"
        class="flex items-center justify-between"
      >
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ row.label }}</span>
        <div class="flex items-center gap-1.5">
          <span class="text-sm font-semibold text-gray-900 dark:text-white">
            {{ row.value }}
          </span>
          <component
            :is="trendIcon(row.trend)"
            :class="['h-3.5 w-3.5', trendColor(row.trend)]"
          />
        </div>
      </div>
    </div>
  </div>
</template>
