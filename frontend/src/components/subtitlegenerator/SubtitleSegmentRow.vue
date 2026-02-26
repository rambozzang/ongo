<script setup lang="ts">
import { computed } from 'vue'
import type { SubtitleSegment } from '@/types/subtitleGenerator'

const props = defineProps<{
  segment: SubtitleSegment
}>()

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  const ms = Math.round((seconds % 1) * 100)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(ms).padStart(2, '0')}`
}

const confidenceBadge = computed(() => {
  const c = props.segment.confidence
  if (c >= 0.9) return { label: 'high', class: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' }
  if (c >= 0.7) return { label: 'mid', class: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' }
  return { label: 'low', class: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' }
})
</script>

<template>
  <div class="flex items-center gap-3 rounded-lg border border-gray-100 bg-white px-3 py-2 dark:border-gray-700 dark:bg-gray-900">
    <!-- Time range -->
    <div class="flex-shrink-0 text-xs font-mono text-gray-500 dark:text-gray-400 w-32 text-center">
      {{ formatTime(segment.startTime) }} - {{ formatTime(segment.endTime) }}
    </div>

    <!-- Divider -->
    <div class="h-6 w-px bg-gray-200 dark:bg-gray-700 flex-shrink-0" />

    <!-- Text -->
    <p class="flex-1 text-sm text-gray-800 dark:text-gray-200 min-w-0 truncate">
      {{ segment.text }}
    </p>

    <!-- Confidence badge -->
    <span
      class="flex-shrink-0 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
      :class="confidenceBadge.class"
    >
      {{ Math.round(segment.confidence * 100) }}%
    </span>
  </div>
</template>
