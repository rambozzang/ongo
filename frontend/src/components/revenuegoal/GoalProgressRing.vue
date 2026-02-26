<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    progress: number
    size?: number
    strokeWidth?: number
    color?: string
  }>(),
  {
    size: 80,
    strokeWidth: 6,
    color: 'text-primary-500',
  },
)

const radius = computed(() => (props.size - props.strokeWidth) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const clampedProgress = computed(() => Math.min(Math.max(props.progress, 0), 100))
const dashOffset = computed(() => circumference.value * (1 - clampedProgress.value / 100))
const center = computed(() => props.size / 2)

const progressColor = computed(() => {
  if (clampedProgress.value >= 100) return 'text-green-500'
  if (clampedProgress.value >= 75) return 'text-blue-500'
  if (clampedProgress.value >= 50) return 'text-primary-500'
  if (clampedProgress.value >= 25) return 'text-yellow-500'
  return 'text-orange-500'
})
</script>

<template>
  <div class="relative inline-flex items-center justify-center" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" class="-rotate-90">
      <!-- Background circle -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        fill="none"
        :stroke-width="strokeWidth"
        class="stroke-gray-200 dark:stroke-gray-700"
      />
      <!-- Progress circle -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        fill="none"
        :stroke-width="strokeWidth"
        stroke-linecap="round"
        class="transition-all duration-700 ease-out"
        :class="progressColor"
        stroke="currentColor"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
      />
    </svg>
    <!-- Center text -->
    <div class="absolute inset-0 flex items-center justify-center">
      <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
        {{ Math.round(clampedProgress) }}%
      </span>
    </div>
  </div>
</template>
