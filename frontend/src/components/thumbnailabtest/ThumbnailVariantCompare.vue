<script setup lang="ts">
import type { ThumbnailVariant } from '@/types/thumbnailAbTest'

defineProps<{
  variantA: ThumbnailVariant
  variantB: ThumbnailVariant
  winner: 'A' | 'B' | null
}>()

const formatNumber = (value: number) => {
  if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}K`
  return value.toLocaleString()
}
</script>

<template>
  <div class="grid grid-cols-2 gap-3">
    <!-- Variant A -->
    <div
      class="rounded-lg border p-3 transition-colors"
      :class="
        winner === 'A'
          ? 'border-green-300 bg-green-50 dark:border-green-700 dark:bg-green-900/20'
          : 'border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50'
      "
    >
      <div class="mb-2 flex items-center justify-between">
        <span class="text-xs font-bold text-gray-600 dark:text-gray-400">Variant A</span>
        <span
          v-if="winner === 'A'"
          class="rounded-full bg-green-100 px-2 py-0.5 text-[10px] font-bold text-green-700 dark:bg-green-900/40 dark:text-green-300"
        >
          WINNER
        </span>
      </div>
      <div class="space-y-1.5">
        <div class="flex items-center justify-between">
          <span class="text-[11px] text-gray-500 dark:text-gray-400">CTR</span>
          <span class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ variantA.ctr.toFixed(1) }}%</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-[11px] text-gray-500 dark:text-gray-400">Impressions</span>
          <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ formatNumber(variantA.impressions) }}</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-[11px] text-gray-500 dark:text-gray-400">Clicks</span>
          <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ formatNumber(variantA.clicks) }}</span>
        </div>
      </div>
    </div>

    <!-- Variant B -->
    <div
      class="rounded-lg border p-3 transition-colors"
      :class="
        winner === 'B'
          ? 'border-green-300 bg-green-50 dark:border-green-700 dark:bg-green-900/20'
          : 'border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50'
      "
    >
      <div class="mb-2 flex items-center justify-between">
        <span class="text-xs font-bold text-gray-600 dark:text-gray-400">Variant B</span>
        <span
          v-if="winner === 'B'"
          class="rounded-full bg-green-100 px-2 py-0.5 text-[10px] font-bold text-green-700 dark:bg-green-900/40 dark:text-green-300"
        >
          WINNER
        </span>
      </div>
      <div class="space-y-1.5">
        <div class="flex items-center justify-between">
          <span class="text-[11px] text-gray-500 dark:text-gray-400">CTR</span>
          <span class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ variantB.ctr.toFixed(1) }}%</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-[11px] text-gray-500 dark:text-gray-400">Impressions</span>
          <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ formatNumber(variantB.impressions) }}</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-[11px] text-gray-500 dark:text-gray-400">Clicks</span>
          <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ formatNumber(variantB.clicks) }}</span>
        </div>
      </div>
    </div>

    <!-- CTR Comparison Bar (full width) -->
    <div v-if="variantA.ctr > 0 || variantB.ctr > 0" class="col-span-2">
      <div class="flex items-center gap-2">
        <div class="flex-1">
          <div class="h-2 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="winner === 'A' ? 'bg-green-500' : 'bg-blue-400'"
              :style="{ width: `${(variantA.ctr / Math.max(variantA.ctr, variantB.ctr)) * 100}%` }"
            />
          </div>
        </div>
        <span class="text-[10px] font-medium text-gray-400 dark:text-gray-500">vs</span>
        <div class="flex-1">
          <div class="h-2 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="winner === 'B' ? 'bg-green-500' : 'bg-orange-400'"
              :style="{ width: `${(variantB.ctr / Math.max(variantA.ctr, variantB.ctr)) * 100}%` }"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
