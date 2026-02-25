<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ChannelStat, PlatformType } from '@/types/portfolio'

defineProps<{
  stats: ChannelStat[]
}>()

const { t } = useI18n()

const platformConfig: Record<PlatformType, { label: string; bgClass: string; textClass: string; borderClass: string }> = {
  youtube: {
    label: 'YouTube',
    bgClass: 'bg-red-50 dark:bg-red-900/20',
    textClass: 'text-youtube',
    borderClass: 'border-l-youtube',
  },
  tiktok: {
    label: 'TikTok',
    bgClass: 'bg-gray-50 dark:bg-gray-800',
    textClass: 'text-gray-900 dark:text-gray-100',
    borderClass: 'border-l-gray-900 dark:border-l-gray-100',
  },
  instagram: {
    label: 'Instagram',
    bgClass: 'bg-pink-50 dark:bg-pink-900/20',
    textClass: 'text-instagram',
    borderClass: 'border-l-instagram',
  },
  naver: {
    label: 'Naver',
    bgClass: 'bg-green-50 dark:bg-green-900/20',
    textClass: 'text-naver',
    borderClass: 'border-l-naver',
  },
}

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}
</script>

<template>
  <div class="card p-6">
    <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
      {{ t('portfolio.channelStats') }}
    </h2>
    <div class="grid gap-4 sm:grid-cols-2">
      <div
        v-for="stat in stats"
        :key="stat.id"
        :class="[
          'rounded-lg border-l-4 p-4',
          platformConfig[stat.platform].bgClass,
          platformConfig[stat.platform].borderClass,
        ]"
      >
        <div class="mb-2 flex items-center gap-2">
          <span :class="['text-sm font-bold', platformConfig[stat.platform].textClass]">
            {{ platformConfig[stat.platform].label }}
          </span>
          <span class="text-xs text-gray-500 dark:text-gray-400">{{ stat.channelName }}</span>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="text-xs text-gray-500 dark:text-gray-400">{{ t('portfolio.subscribers') }}</div>
            <div class="text-lg font-bold text-gray-900 dark:text-gray-100">
              {{ formatNumber(stat.subscribers) }}
            </div>
          </div>
          <div>
            <div class="text-xs text-gray-500 dark:text-gray-400">{{ t('portfolio.totalViews') }}</div>
            <div class="text-lg font-bold text-gray-900 dark:text-gray-100">
              {{ formatNumber(stat.totalViews) }}
            </div>
          </div>
          <div>
            <div class="text-xs text-gray-500 dark:text-gray-400">{{ t('portfolio.avgViews') }}</div>
            <div class="text-sm font-semibold text-gray-700 dark:text-gray-300">
              {{ formatNumber(stat.avgViews) }}
            </div>
          </div>
          <div>
            <div class="text-xs text-gray-500 dark:text-gray-400">{{ t('portfolio.engagementRate') }}</div>
            <div class="text-sm font-semibold text-gray-700 dark:text-gray-300">
              {{ stat.engagementRate }}%
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
