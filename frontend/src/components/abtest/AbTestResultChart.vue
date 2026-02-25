<script setup lang="ts">
import { computed } from 'vue'
import { StarIcon } from '@heroicons/vue/24/outline'
import type { AbTestVariant, VariantLabel } from '@/types/abtest'

const props = defineProps<{
  variants: AbTestVariant[]
}>()

const maxCtr = computed(() => {
  const ctrs = props.variants.map(v => v.ctr)
  return Math.max(...ctrs, 0.01)
})

const barColors: Record<VariantLabel, string> = {
  A: 'bg-blue-500',
  B: 'bg-purple-500',
  C: 'bg-orange-500',
  D: 'bg-teal-500',
}

const textColors: Record<VariantLabel, string> = {
  A: 'text-blue-600 dark:text-blue-400',
  B: 'text-purple-600 dark:text-purple-400',
  C: 'text-orange-600 dark:text-orange-400',
  D: 'text-teal-600 dark:text-teal-400',
}

function getBarColor(label: VariantLabel): string {
  return barColors[label] ?? 'bg-gray-500'
}

function getTextColor(label: VariantLabel): string {
  return textColors[label] ?? 'text-gray-600 dark:text-gray-400'
}

function getBarWidth(ctr: number): string {
  if (maxCtr.value === 0) return '0%'
  return `${(ctr / maxCtr.value) * 100}%`
}
</script>

<template>
  <div class="space-y-3">
    <div
      v-for="variant in variants"
      :key="variant.id"
      class="space-y-1"
    >
      <!-- Label row -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span :class="['text-sm font-semibold', getTextColor(variant.label)]">
            {{ variant.label }}
          </span>
          <StarIcon
            v-if="variant.isWinner"
            class="h-4 w-4 text-yellow-500"
          />
        </div>
        <span class="text-sm font-bold text-gray-900 dark:text-white">
          {{ variant.ctr.toFixed(1) }}%
        </span>
      </div>

      <!-- CTR Bar -->
      <div class="h-6 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          :class="['flex h-full items-center justify-end rounded-full px-2 transition-all duration-500', getBarColor(variant.label)]"
          :style="{ width: getBarWidth(variant.ctr) }"
        >
          <StarIcon
            v-if="variant.isWinner"
            class="h-3.5 w-3.5 text-white"
          />
        </div>
      </div>

      <!-- Metrics row -->
      <div class="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
        <span>
          {{ $t('abTest.impressions') }}:
          <span class="font-medium text-gray-700 dark:text-gray-300">{{ variant.impressions.toLocaleString() }}</span>
        </span>
        <span>
          {{ $t('abTest.clicks') }}:
          <span class="font-medium text-gray-700 dark:text-gray-300">{{ variant.clicks.toLocaleString() }}</span>
        </span>
        <span>
          {{ $t('abTest.ctr') }}:
          <span class="font-medium text-gray-700 dark:text-gray-300">{{ variant.ctr.toFixed(2) }}%</span>
        </span>
        <span>
          {{ $t('abTest.avgWatchTime') }}:
          <span class="font-medium text-gray-700 dark:text-gray-300">{{ variant.avgWatchTime }}{{ $t('abTest.sec') }}</span>
        </span>
      </div>
    </div>
  </div>
</template>
