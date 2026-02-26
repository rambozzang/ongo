<template>
  <div
    class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
  >
    <!-- Header: platform + score -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex items-center gap-2">
        <span
          class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
          :class="platformBadge"
        >
          {{ slot.platform }}
        </span>
        <CompetitionBadge :level="slot.competitionLevel" />
      </div>
      <div class="flex items-center gap-1">
        <span class="text-lg font-bold text-primary-600 dark:text-primary-400">
          {{ slot.score }}
        </span>
        <span class="text-xs text-gray-400 dark:text-gray-500">점</span>
      </div>
    </div>

    <!-- Day + Time -->
    <div class="mb-3">
      <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ slot.dayOfWeek }}
      </p>
      <p class="mt-0.5 text-xl font-bold text-gray-900 dark:text-gray-100">
        {{ formatHour(slot.hour) }}
      </p>
    </div>

    <!-- Audience online -->
    <div class="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
      <span class="text-xs text-gray-500 dark:text-gray-400">예상 온라인 수</span>
      <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ formatNumber(slot.audienceOnline) }}
      </span>
    </div>

    <!-- Reason -->
    <p v-if="slot.reason" class="mt-2 text-xs text-gray-500 dark:text-gray-400">
      {{ slot.reason }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import CompetitionBadge from '@/components/scheduleoptimizer/CompetitionBadge.vue'
import type { OptimalSlot } from '@/types/scheduleOptimizer'

const props = defineProps<{
  slot: OptimalSlot
}>()

const platformBadge = computed(() => {
  const configs: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400',
    INSTAGRAM: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    NAVERCLIP: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  }
  return configs[props.slot.platform.toUpperCase()] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
})

function formatHour(hour: number): string {
  const period = hour < 12 ? '오전' : '오후'
  const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return `${period} ${displayHour}:00`
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}
</script>
