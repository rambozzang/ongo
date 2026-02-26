<script setup lang="ts">
import { computed } from 'vue'
import type { SubtitleJob } from '@/types/subtitleGenerator'

const props = defineProps<{
  progress: number
  status: SubtitleJob['status']
}>()

const barColor = computed(() => {
  const colors: Record<SubtitleJob['status'], string> = {
    PENDING: 'bg-gray-400 dark:bg-gray-500',
    PROCESSING: 'bg-blue-500 dark:bg-blue-400',
    COMPLETED: 'bg-green-500 dark:bg-green-400',
    FAILED: 'bg-red-500 dark:bg-red-400',
  }
  return colors[props.status] ?? 'bg-gray-400'
})

const isAnimating = computed(() => props.status === 'PROCESSING')
</script>

<template>
  <div class="w-full">
    <div class="flex items-center justify-between mb-1">
      <span class="text-xs font-medium text-gray-600 dark:text-gray-400">
        {{ progress }}%
      </span>
    </div>
    <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
      <div
        class="h-full rounded-full transition-all duration-500"
        :class="[barColor, isAnimating ? 'animate-pulse' : '']"
        :style="{ width: `${Math.min(Math.max(progress, 0), 100)}%` }"
      />
    </div>
  </div>
</template>
