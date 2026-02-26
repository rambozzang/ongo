<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
} from '@heroicons/vue/24/outline'
import type { AlgorithmInsight } from '@/types/algorithmInsights'

interface Props {
  insight: AlgorithmInsight
}

const props = defineProps<Props>()

const importanceBadge = computed(() => {
  const i = props.insight.importance
  if (i >= 90) return { label: '매우 높음', color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' }
  if (i >= 75) return { label: '높음', color: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400' }
  if (i >= 50) return { label: '보통', color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' }
  return { label: '낮음', color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' }
})

const categoryConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    CONTENT: { label: '콘텐츠', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' },
    ENGAGEMENT: { label: '참여', color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
    METADATA: { label: '메타데이터', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' },
    CONSISTENCY: { label: '일관성', color: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400' },
    AUDIENCE: { label: '오디언스', color: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400' },
  }
  return configs[props.insight.category] || { label: props.insight.category, color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' }
})

const trendConfig = computed(() => {
  const configs: Record<string, { icon: typeof ArrowTrendingUpIcon; color: string; label: string }> = {
    UP: { icon: ArrowTrendingUpIcon, color: 'text-green-600 dark:text-green-400', label: '상승' },
    DOWN: { icon: ArrowTrendingDownIcon, color: 'text-red-600 dark:text-red-400', label: '하락' },
    STABLE: { icon: MinusIcon, color: 'text-gray-500 dark:text-gray-400', label: '유지' },
  }
  return configs[props.insight.trend] || configs.STABLE
})

const scoreBarColor = computed(() => {
  const s = props.insight.currentScore
  if (s >= 80) return 'bg-green-500 dark:bg-green-400'
  if (s >= 60) return 'bg-yellow-500 dark:bg-yellow-400'
  if (s >= 40) return 'bg-orange-500 dark:bg-orange-400'
  return 'bg-red-500 dark:bg-red-400'
})
</script>

<template>
  <div class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
    <div class="flex flex-col gap-3 tablet:flex-row tablet:items-center">
      <!-- Factor Info -->
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 mb-1">
          <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ insight.factor }}
          </h4>
          <component
            :is="trendConfig.icon"
            :class="['h-4 w-4', trendConfig.color]"
            :title="trendConfig.label"
          />
        </div>

        <!-- Badges -->
        <div class="flex flex-wrap items-center gap-2 mb-2">
          <span :class="['inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium', importanceBadge.color]">
            중요도: {{ insight.importance }}
          </span>
          <span :class="['inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium', categoryConfig.color]">
            {{ categoryConfig.label }}
          </span>
        </div>
      </div>

      <!-- Score Bar -->
      <div class="flex items-center gap-3 tablet:w-56">
        <div class="flex-1 h-2.5 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            :class="['h-full rounded-full transition-all duration-500', scoreBarColor]"
            :style="{ width: `${insight.currentScore}%` }"
          />
        </div>
        <span class="w-8 text-right text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ insight.currentScore }}
        </span>
      </div>
    </div>

    <!-- Recommendation -->
    <div class="mt-2 rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900/50">
      <p class="text-xs text-gray-600 dark:text-gray-400">
        <span class="font-medium text-gray-700 dark:text-gray-300">추천: </span>
        {{ insight.recommendation }}
      </p>
    </div>
  </div>
</template>
