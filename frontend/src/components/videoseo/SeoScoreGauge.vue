<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  score: number
  label?: string
  size?: 'sm' | 'md' | 'lg'
}>()

const sizeConfig = computed(() => {
  switch (props.size) {
    case 'sm': return { container: 'w-16 h-16', text: 'text-sm', label: 'text-[10px]', stroke: 4 }
    case 'lg': return { container: 'w-32 h-32', text: 'text-3xl', label: 'text-xs', stroke: 6 }
    default: return { container: 'w-24 h-24', text: 'text-xl', label: 'text-[11px]', stroke: 5 }
  }
})

const radius = computed(() => {
  switch (props.size) {
    case 'sm': return 28
    case 'lg': return 58
    default: return 42
  }
})

const circumference = computed(() => 2 * Math.PI * radius.value)
const offset = computed(() => circumference.value - (props.score / 100) * circumference.value)

const scoreColor = computed(() => {
  if (props.score >= 80) return 'text-green-500 stroke-green-500'
  if (props.score >= 60) return 'text-yellow-500 stroke-yellow-500'
  return 'text-red-500 stroke-red-500'
})

const trackColor = 'stroke-gray-200 dark:stroke-gray-700'
</script>

<template>
  <div class="flex flex-col items-center">
    <div :class="sizeConfig.container" class="relative">
      <svg class="w-full h-full -rotate-90" viewBox="0 0 128 128">
        <!-- Track -->
        <circle
          cx="64" cy="64"
          :r="radius"
          fill="none"
          :class="trackColor"
          :stroke-width="sizeConfig.stroke"
        />
        <!-- Progress -->
        <circle
          cx="64" cy="64"
          :r="radius"
          fill="none"
          :class="scoreColor"
          :stroke-width="sizeConfig.stroke"
          stroke-linecap="round"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="offset"
          class="transition-all duration-700 ease-out"
        />
      </svg>
      <!-- Score text -->
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <span :class="[sizeConfig.text, scoreColor.split(' ')[0]]" class="font-bold">
          {{ score }}
        </span>
      </div>
    </div>
    <span v-if="label" :class="sizeConfig.label" class="mt-1 font-medium text-gray-500 dark:text-gray-400">
      {{ label }}
    </span>
  </div>
</template>
