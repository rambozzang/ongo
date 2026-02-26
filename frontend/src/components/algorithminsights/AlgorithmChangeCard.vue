<script setup lang="ts">
import { computed } from 'vue'
import {
  ExclamationTriangleIcon,
  LightBulbIcon,
} from '@heroicons/vue/24/outline'
import type { AlgorithmChange } from '@/types/algorithmInsights'

interface Props {
  change: AlgorithmChange
}

const props = defineProps<Props>()

const impactConfig = computed(() => {
  const configs: Record<string, { label: string; color: string; bgColor: string }> = {
    HIGH: {
      label: '높음',
      color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
      bgColor: 'border-red-200 dark:border-red-800',
    },
    MEDIUM: {
      label: '보통',
      color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
      bgColor: 'border-yellow-200 dark:border-yellow-800',
    },
    LOW: {
      label: '낮음',
      color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
      bgColor: 'border-green-200 dark:border-green-800',
    },
  }
  return configs[props.change.impact] || configs.LOW
})

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.change.platform] || props.change.platform
})

const platformBadgeColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.change.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const formattedDate = computed(() => {
  return new Date(props.change.detectedAt).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
})
</script>

<template>
  <div
    :class="['rounded-xl border bg-white p-5 shadow-sm dark:bg-gray-800', impactConfig.bgColor]"
  >
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex items-start gap-3 flex-1 min-w-0">
        <ExclamationTriangleIcon
          :class="[
            'h-5 w-5 flex-shrink-0 mt-0.5',
            change.impact === 'HIGH' ? 'text-red-500' : change.impact === 'MEDIUM' ? 'text-yellow-500' : 'text-green-500',
          ]"
        />
        <div class="flex-1 min-w-0">
          <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ change.title }}
          </h3>
          <p class="mt-1 text-sm text-gray-600 dark:text-gray-400">
            {{ change.description }}
          </p>
        </div>
      </div>
    </div>

    <!-- Badges -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', platformBadgeColor]">
        {{ platformLabel }}
      </span>
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', impactConfig.color]">
        영향도: {{ impactConfig.label }}
      </span>
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ formattedDate }}
      </span>
    </div>

    <!-- Affected Areas -->
    <div class="mb-3 flex flex-wrap gap-1.5">
      <span
        v-for="area in change.affectedAreas"
        :key="area"
        class="inline-flex items-center rounded bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-700 dark:bg-gray-700 dark:text-gray-300"
      >
        {{ area }}
      </span>
    </div>

    <!-- Recommendation -->
    <div class="rounded-lg bg-blue-50 px-3 py-2 dark:bg-blue-900/20">
      <div class="flex items-start gap-2">
        <LightBulbIcon class="h-4 w-4 flex-shrink-0 mt-0.5 text-blue-600 dark:text-blue-400" />
        <p class="text-xs text-blue-700 dark:text-blue-300">
          {{ change.recommendation }}
        </p>
      </div>
    </div>
  </div>
</template>
