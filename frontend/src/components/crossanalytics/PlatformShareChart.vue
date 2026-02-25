<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PlatformPerformanceSummary, PlatformKey } from '@/types/crossAnalytics'

const props = defineProps<{
  summaries: PlatformPerformanceSummary[]
  totalViews: number
}>()

const { t } = useI18n({ useScope: 'global' })

const platformColors: Record<PlatformKey, { light: string; dark: string }> = {
  youtube: { light: '#FF0000', dark: '#FF0000' },
  tiktok: { light: '#000000', dark: '#ffffff' },
  instagram: { light: '#E4405F', dark: '#E4405F' },
  naverClip: { light: '#03C75A', dark: '#03C75A' },
}

function platformLabel(platform: PlatformKey): string {
  return t(`crossAnalytics.platform.${platform}`)
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString()
}

const sortedSummaries = computed(() => {
  return [...props.summaries].sort((a, b) => b.totalViews - a.totalViews)
})

function sharePercent(views: number): number {
  if (props.totalViews === 0) return 0
  return Math.round((views / props.totalViews) * 100)
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('crossAnalytics.overview.platformShare') }}
    </h3>

    <div v-if="sortedSummaries.length > 0" class="space-y-4">
      <div
        v-for="summary in sortedSummaries"
        :key="summary.platform"
        class="space-y-1.5"
      >
        <div class="flex items-center justify-between text-sm">
          <div class="flex items-center gap-2">
            <span
              class="h-3 w-3 rounded-full"
              :style="{ backgroundColor: platformColors[summary.platform].light }"
              :class="{ 'dark:hidden': summary.platform === 'tiktok' }"
            />
            <span
              v-if="summary.platform === 'tiktok'"
              class="hidden h-3 w-3 rounded-full dark:block"
              :style="{ backgroundColor: platformColors[summary.platform].dark }"
            />
            <span class="font-medium text-gray-700 dark:text-gray-300">
              {{ platformLabel(summary.platform) }}
            </span>
          </div>
          <div class="flex items-center gap-3">
            <span class="text-xs text-gray-500 dark:text-gray-400">
              {{ formatNumber(summary.totalViews) }}
            </span>
            <span class="min-w-[3rem] text-right text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ sharePercent(summary.totalViews) }}%
            </span>
          </div>
        </div>
        <div class="h-3 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
          <div
            class="h-full rounded-full transition-all duration-500"
            :style="{
              width: `${sharePercent(summary.totalViews)}%`,
              backgroundColor: platformColors[summary.platform].light,
            }"
            :class="{ 'dark:hidden': summary.platform === 'tiktok' }"
          />
          <div
            v-if="summary.platform === 'tiktok'"
            class="hidden h-full rounded-full transition-all duration-500 dark:block"
            :style="{
              width: `${sharePercent(summary.totalViews)}%`,
              backgroundColor: platformColors[summary.platform].dark,
            }"
          />
        </div>
      </div>
    </div>

    <div v-else class="flex h-32 items-center justify-center text-sm text-gray-400 dark:text-gray-500">
      {{ $t('crossAnalytics.noReport') }}
    </div>
  </div>
</template>
