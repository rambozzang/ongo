<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowTrendingUpIcon, ArrowTrendingDownIcon } from '@heroicons/vue/24/outline'
import type { TrendingTopic } from '@/types/competitorAnalysis'

const props = defineProps<{
  topic: TrendingTopic
}>()

const { t } = useI18n({ useScope: 'global' })

const isPositive = computed(() => props.topic.growth >= 0)

const growthColor = computed(() =>
  isPositive.value
    ? 'text-green-600 dark:text-green-400'
    : 'text-red-600 dark:text-red-400',
)

const growthBgColor = computed(() =>
  isPositive.value
    ? 'bg-green-50 dark:bg-green-900/20'
    : 'bg-red-50 dark:bg-red-900/20',
)

function formatVolume(num: number): string {
  if (num >= 1_000_000) return `${(num / 1_000_000).toFixed(1)}M`
  if (num >= 1_000) return `${(num / 1_000).toFixed(1)}K`
  return num.toLocaleString()
}

function platformBadgeColor(platform: string): string {
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
</script>

<template>
  <div class="rounded-lg border border-gray-200 bg-white p-4 transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-800">
    <!-- Header: keyword + platform badge -->
    <div class="mb-3 flex items-start justify-between">
      <h4 class="text-sm font-semibold text-gray-900 dark:text-white">
        {{ topic.keyword }}
      </h4>
      <span
        :class="[
          'inline-block rounded-full px-2 py-0.5 text-[10px] font-medium',
          platformBadgeColor(topic.platform),
        ]"
      >
        {{ t(`competitorAnalysis.platform.${topic.platform}`) }}
      </span>
    </div>

    <!-- Volume -->
    <div class="mb-2 flex items-center justify-between">
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ t('competitorAnalysis.trends.volume') }}
      </span>
      <span class="text-sm font-bold text-gray-900 dark:text-white">
        {{ formatVolume(topic.volume) }}
      </span>
    </div>

    <!-- Growth -->
    <div class="mb-2 flex items-center justify-between">
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ t('competitorAnalysis.trends.growth') }}
      </span>
      <div
        :class="[
          'inline-flex items-center gap-1 rounded-full px-2 py-0.5',
          growthBgColor,
        ]"
      >
        <component
          :is="isPositive ? ArrowTrendingUpIcon : ArrowTrendingDownIcon"
          :class="['h-3.5 w-3.5', growthColor]"
        />
        <span :class="['text-xs font-semibold', growthColor]">
          {{ isPositive ? '+' : '' }}{{ topic.growth.toFixed(1) }}%
        </span>
      </div>
    </div>

    <!-- Competitor usage -->
    <div class="flex items-center justify-between">
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ t('competitorAnalysis.trends.competitorUsage') }}
      </span>
      <span class="text-xs font-medium text-gray-700 dark:text-gray-300">
        {{ topic.competitorUsage }}{{ t('competitorAnalysis.trends.competitors') }}
      </span>
    </div>
  </div>
</template>
