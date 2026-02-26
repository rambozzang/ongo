<script setup lang="ts">
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
} from '@heroicons/vue/24/outline'
import type { SeoKeyword } from '@/types/videoSeo'

defineProps<{
  keyword: SeoKeyword
  rank: number
}>()

const competitionColors: Record<string, { bg: string; text: string }> = {
  HIGH: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  MEDIUM: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' },
  LOW: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const competitionLabels: Record<string, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
}

const trendConfig: Record<string, { icon: typeof ArrowTrendingUpIcon; color: string; label: string }> = {
  UP: { icon: ArrowTrendingUpIcon, color: 'text-green-600 dark:text-green-400', label: '상승' },
  DOWN: { icon: ArrowTrendingDownIcon, color: 'text-red-600 dark:text-red-400', label: '하락' },
  STABLE: { icon: MinusIcon, color: 'text-gray-500 dark:text-gray-400', label: '유지' },
}

const getCompetitionStyle = (comp: string) => competitionColors[comp] ?? competitionColors.MEDIUM
const getCompetitionLabel = (comp: string) => competitionLabels[comp] ?? comp
const getTrend = (trend: string) => trendConfig[trend] ?? trendConfig.STABLE

const formatVolume = (vol: number) => {
  if (vol >= 10000) return `${(vol / 10000).toFixed(1)}만`
  if (vol >= 1000) return `${(vol / 1000).toFixed(1)}천`
  return vol.toString()
}
</script>

<template>
  <tr class="border-b border-gray-100 dark:border-gray-800 transition-colors hover:bg-gray-50 dark:hover:bg-gray-800/50">
    <!-- Rank -->
    <td class="py-3 pr-3 text-center">
      <span class="text-xs font-semibold text-gray-400 dark:text-gray-500">{{ rank }}</span>
    </td>

    <!-- Keyword -->
    <td class="py-3 pr-4">
      <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ keyword.keyword }}</span>
    </td>

    <!-- Search Volume -->
    <td class="py-3 pr-4 text-right">
      <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ formatVolume(keyword.searchVolume) }}</span>
    </td>

    <!-- Competition -->
    <td class="py-3 pr-4 text-center">
      <span
        :class="[getCompetitionStyle(keyword.competition).bg, getCompetitionStyle(keyword.competition).text]"
        class="inline-block rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ getCompetitionLabel(keyword.competition) }}
      </span>
    </td>

    <!-- Relevance -->
    <td class="py-3 pr-4">
      <div class="flex items-center gap-2">
        <div class="flex-1 h-1.5 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
          <div
            class="h-full rounded-full bg-primary-500 transition-all duration-500"
            :style="{ width: `${keyword.relevance}%` }"
          />
        </div>
        <span class="w-8 text-right text-xs font-medium text-gray-600 dark:text-gray-400">{{ keyword.relevance }}</span>
      </div>
    </td>

    <!-- Trend -->
    <td class="py-3 text-center">
      <div class="inline-flex items-center gap-1" :class="getTrend(keyword.trend).color">
        <component :is="getTrend(keyword.trend).icon" class="h-3.5 w-3.5" />
        <span class="text-xs font-medium">{{ getTrend(keyword.trend).label }}</span>
      </div>
    </td>
  </tr>
</template>
