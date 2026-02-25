<template>
  <div class="card">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('prediction.resultTitle') }}
      </h3>
      <span
        class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold"
        :class="confidenceBadgeClass"
      >
        <ShieldCheckIcon class="h-3.5 w-3.5" />
        {{ $t('prediction.confidence') }}: {{ result.metrics.confidenceScore }}%
      </span>
    </div>

    <!-- Platform + Title -->
    <div class="mb-4">
      <div class="flex items-center gap-2 mb-1">
        <PlatformBadge :platform="result.platform" />
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ result.title }}</span>
      </div>
      <p class="text-xs text-gray-400 dark:text-gray-500">
        {{ $t('prediction.optimalTime') }}: {{ result.optimalUploadTime }}
      </p>
    </div>

    <!-- Metrics Grid -->
    <div class="grid grid-cols-2 gap-3 tablet:grid-cols-4">
      <!-- Expected Views - Gauge -->
      <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-3">
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ $t('prediction.expectedViews') }}</p>
        <div class="relative flex items-center justify-center mb-2">
          <svg class="h-20 w-20" viewBox="0 0 80 80">
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              :stroke="isDark ? '#374151' : '#e5e7eb'"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="gaugeCircumference"
              :stroke-dashoffset="0"
              :transform="`rotate(-90 40 40)`"
            />
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              stroke="#6366f1"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="gaugeCircumference"
              :stroke-dashoffset="viewsGaugeOffset"
              :transform="`rotate(-90 40 40)`"
              class="transition-all duration-1000"
            />
          </svg>
          <span class="absolute text-sm font-bold text-gray-900 dark:text-gray-100">
            {{ formatCompact(result.metrics.expectedViews) }}
          </span>
        </div>
      </div>

      <!-- Expected Likes -->
      <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-3">
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ $t('prediction.expectedLikes') }}</p>
        <div class="relative flex items-center justify-center mb-2">
          <svg class="h-20 w-20" viewBox="0 0 80 80">
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              :stroke="isDark ? '#374151' : '#e5e7eb'"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="gaugeCircumference"
              :stroke-dashoffset="0"
              :transform="`rotate(-90 40 40)`"
            />
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              stroke="#ec4899"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="gaugeCircumference"
              :stroke-dashoffset="likesGaugeOffset"
              :transform="`rotate(-90 40 40)`"
              class="transition-all duration-1000"
            />
          </svg>
          <span class="absolute text-sm font-bold text-gray-900 dark:text-gray-100">
            {{ formatCompact(result.metrics.expectedLikes) }}
          </span>
        </div>
      </div>

      <!-- Engagement Rate -->
      <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-3">
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ $t('prediction.engagementRate') }}</p>
        <div class="relative flex items-center justify-center mb-2">
          <svg class="h-20 w-20" viewBox="0 0 80 80">
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              :stroke="isDark ? '#374151' : '#e5e7eb'"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="gaugeCircumference"
              :stroke-dashoffset="0"
              :transform="`rotate(-90 40 40)`"
            />
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              stroke="#10b981"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="gaugeCircumference"
              :stroke-dashoffset="engagementGaugeOffset"
              :transform="`rotate(-90 40 40)`"
              class="transition-all duration-1000"
            />
          </svg>
          <span class="absolute text-sm font-bold text-gray-900 dark:text-gray-100">
            {{ result.metrics.engagementRate }}%
          </span>
        </div>
      </div>

      <!-- Comments + Shares -->
      <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-3">
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ $t('prediction.interactions') }}</p>
        <div class="space-y-2 mt-3">
          <div class="flex items-center justify-between">
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('prediction.comments') }}</span>
            <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ formatCompact(result.metrics.expectedComments) }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('prediction.shares') }}</span>
            <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ formatCompact(result.metrics.expectedShares) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ShieldCheckIcon } from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import { useThemeStore } from '@/stores/theme'
import type { PredictionResult } from '@/types/prediction'

const props = defineProps<{
  result: PredictionResult
}>()

const themeStore = useThemeStore()
const isDark = computed(() => themeStore.isDark)

const gaugeCircumference = 2 * Math.PI * 34 // ~213.6

// Gauge offsets: views out of 100k, likes out of 10k, engagement out of 10%
const viewsGaugeOffset = computed(() => {
  const ratio = Math.min(props.result.metrics.expectedViews / 100000, 1)
  return gaugeCircumference * (1 - ratio)
})

const likesGaugeOffset = computed(() => {
  const ratio = Math.min(props.result.metrics.expectedLikes / 10000, 1)
  return gaugeCircumference * (1 - ratio)
})

const engagementGaugeOffset = computed(() => {
  const ratio = Math.min(props.result.metrics.engagementRate / 10, 1)
  return gaugeCircumference * (1 - ratio)
})

const confidenceBadgeClass = computed(() => {
  const score = props.result.metrics.confidenceScore
  if (score >= 80) return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
  if (score >= 60) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
  return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
})

function formatCompact(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}
</script>
