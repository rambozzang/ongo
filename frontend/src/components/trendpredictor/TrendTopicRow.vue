<script setup lang="ts">
import {
  HashtagIcon,
  FilmIcon,
  EyeIcon,
  ArrowTrendingUpIcon,
} from '@heroicons/vue/24/outline'
import type { TrendTopic } from '@/types/trendPredictor'

interface Props {
  topic: TrendTopic
}

defineProps<Props>()

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}
</script>

<template>
  <div
    class="flex flex-col tablet:flex-row tablet:items-center gap-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors"
  >
    <!-- Topic name -->
    <div class="flex-1 min-w-0">
      <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
        {{ topic.topic }}
      </h4>

      <!-- Related keywords -->
      <div class="flex flex-wrap gap-1.5 mt-1.5">
        <span
          v-for="keyword in topic.relatedKeywords"
          :key="keyword"
          class="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
        >
          <HashtagIcon class="w-3 h-3" />
          {{ keyword }}
        </span>
      </div>
    </div>

    <!-- Stats -->
    <div class="flex items-center gap-4 text-sm">
      <div class="flex items-center gap-1 text-gray-600 dark:text-gray-400">
        <FilmIcon class="w-4 h-4" />
        <span>{{ formatNumber(topic.videoCount) }}개</span>
      </div>
      <div class="flex items-center gap-1 text-gray-600 dark:text-gray-400">
        <EyeIcon class="w-4 h-4" />
        <span>{{ formatNumber(topic.avgViews) }}</span>
      </div>
      <div
        class="flex items-center gap-1 font-medium"
        :class="topic.growthRate >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
      >
        <ArrowTrendingUpIcon class="w-4 h-4" />
        <span>{{ topic.growthRate > 0 ? '+' : '' }}{{ topic.growthRate.toFixed(1) }}%</span>
      </div>
    </div>
  </div>
</template>
