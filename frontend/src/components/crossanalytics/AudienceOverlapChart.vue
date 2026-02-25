<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PlatformKey } from '@/types/crossAnalytics'

defineProps<{
  overlaps: { platforms: [PlatformKey, PlatformKey]; overlapPercent: number }[]
}>()

const { t } = useI18n({ useScope: 'global' })

function platformLabel(platform: PlatformKey): string {
  return t(`crossAnalytics.platform.${platform}`)
}

function overlapLevel(percent: number): 'high' | 'medium' | 'low' {
  if (percent >= 40) return 'high'
  if (percent >= 20) return 'medium'
  return 'low'
}

function overlapLevelLabel(percent: number): string {
  const level = overlapLevel(percent)
  return t(`crossAnalytics.audience.${level}Overlap`)
}

function overlapBarColor(percent: number): string {
  const level = overlapLevel(percent)
  switch (level) {
    case 'high':
      return 'bg-green-500 dark:bg-green-400'
    case 'medium':
      return 'bg-yellow-500 dark:bg-yellow-400'
    case 'low':
      return 'bg-gray-400 dark:bg-gray-500'
  }
}

function overlapBadgeColor(percent: number): string {
  const level = overlapLevel(percent)
  switch (level) {
    case 'high':
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
    case 'medium':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'low':
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
  }
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <div class="mb-1">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('crossAnalytics.audience.title') }}
      </h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        {{ $t('crossAnalytics.audience.description') }}
      </p>
    </div>

    <div v-if="overlaps.length > 0" class="mt-4 space-y-4">
      <div
        v-for="(overlap, idx) in overlaps"
        :key="idx"
        class="space-y-2"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm">
            <span class="font-medium text-gray-700 dark:text-gray-300">
              {{ platformLabel(overlap.platforms[0]) }}
            </span>
            <span class="text-gray-400 dark:text-gray-500">&amp;</span>
            <span class="font-medium text-gray-700 dark:text-gray-300">
              {{ platformLabel(overlap.platforms[1]) }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <span
              :class="[
                'inline-block rounded-full px-2 py-0.5 text-[10px] font-medium',
                overlapBadgeColor(overlap.overlapPercent),
              ]"
            >
              {{ overlapLevelLabel(overlap.overlapPercent) }}
            </span>
            <span class="min-w-[3rem] text-right text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ overlap.overlapPercent }}%
            </span>
          </div>
        </div>
        <div class="h-2.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
          <div
            :class="['h-full rounded-full transition-all duration-500', overlapBarColor(overlap.overlapPercent)]"
            :style="{ width: `${overlap.overlapPercent}%` }"
          />
        </div>
      </div>
    </div>

    <div v-else class="flex h-32 items-center justify-center text-sm text-gray-400 dark:text-gray-500">
      {{ $t('crossAnalytics.audience.noData') }}
    </div>
  </div>
</template>
