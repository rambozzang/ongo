<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  score: number
  size?: 'sm' | 'md' | 'lg'
}>()

const sizeConfig = computed(() => {
  switch (props.size) {
    case 'sm':
      return { container: 'h-16 w-16', text: 'text-lg', label: 'text-[9px]', stroke: 4 }
    case 'lg':
      return { container: 'h-28 w-28', text: 'text-3xl', label: 'text-xs', stroke: 6 }
    default:
      return { container: 'h-20 w-20', text: 'text-xl', label: 'text-[10px]', stroke: 5 }
  }
})

const scoreColor = computed(() => {
  if (props.score >= 80) return { stroke: 'stroke-green-500', text: 'text-green-600 dark:text-green-400', label: '우수' }
  if (props.score >= 60) return { stroke: 'stroke-yellow-500', text: 'text-yellow-600 dark:text-yellow-400', label: '보통' }
  return { stroke: 'stroke-red-500', text: 'text-red-600 dark:text-red-400', label: '주의' }
})

const circumference = 2 * Math.PI * 40
const dashOffset = computed(() => circumference * (1 - props.score / 100))
</script>

<template>
  <div class="flex flex-col items-center">
    <div :class="sizeConfig.container" class="relative">
      <svg class="h-full w-full -rotate-90" viewBox="0 0 100 100">
        <!-- Background circle -->
        <circle
          cx="50"
          cy="50"
          r="40"
          fill="none"
          class="stroke-gray-200 dark:stroke-gray-700"
          :stroke-width="sizeConfig.stroke"
        />
        <!-- Progress circle -->
        <circle
          cx="50"
          cy="50"
          r="40"
          fill="none"
          :class="scoreColor.stroke"
          :stroke-width="sizeConfig.stroke"
          stroke-linecap="round"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="dashOffset"
          class="transition-all duration-700 ease-out"
        />
      </svg>
      <!-- Score text -->
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <span :class="[sizeConfig.text, scoreColor.text]" class="font-bold leading-none">
          {{ score }}
        </span>
      </div>
    </div>
    <span :class="[sizeConfig.label, scoreColor.text]" class="mt-1 font-medium">
      {{ scoreColor.label }}
    </span>
  </div>
</template>
