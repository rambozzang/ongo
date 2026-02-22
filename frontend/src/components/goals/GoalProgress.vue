<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

interface Props {
  percentage: number
  size?: 'sm' | 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
})

const animatedPercentage = ref(0)

const sizes = {
  sm: { width: 60, strokeWidth: 4, fontSize: 'text-xs' },
  md: { width: 80, strokeWidth: 5, fontSize: 'text-sm' },
  lg: { width: 120, strokeWidth: 6, fontSize: 'text-lg' },
}

const config = computed(() => sizes[props.size])
const radius = computed(() => (config.value.width - config.value.strokeWidth) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const strokeDashoffset = computed(() => {
  return circumference.value - (animatedPercentage.value / 100) * circumference.value
})

const progressColor = computed(() => {
  if (props.percentage < 25) return 'stroke-red-500 dark:stroke-red-400'
  if (props.percentage < 50) return 'stroke-orange-500 dark:stroke-orange-400'
  if (props.percentage < 75) return 'stroke-yellow-500 dark:stroke-yellow-400'
  return 'stroke-green-500 dark:stroke-green-400'
})

const textColor = computed(() => {
  if (props.percentage < 25) return 'text-red-600 dark:text-red-400'
  if (props.percentage < 50) return 'text-orange-600 dark:text-orange-400'
  if (props.percentage < 75) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-green-600 dark:text-green-400'
})

let animationTimeout: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  animationTimeout = setTimeout(() => {
    animatedPercentage.value = Math.min(props.percentage, 100)
  }, 100)
})

onUnmounted(() => {
  if (animationTimeout) clearTimeout(animationTimeout)
})
</script>

<template>
  <div class="relative inline-flex items-center justify-center">
    <svg
      :width="config.width"
      :height="config.width"
      class="transform -rotate-90"
    >
      <!-- Background circle -->
      <circle
        :cx="config.width / 2"
        :cy="config.width / 2"
        :r="radius"
        class="stroke-gray-200 dark:stroke-gray-700"
        :stroke-width="config.strokeWidth"
        fill="none"
      />
      <!-- Progress circle -->
      <circle
        :cx="config.width / 2"
        :cy="config.width / 2"
        :r="radius"
        :class="progressColor"
        :stroke-width="config.strokeWidth"
        fill="none"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="strokeDashoffset"
        stroke-linecap="round"
        class="transition-all duration-1000 ease-out"
      />
    </svg>
    <div
      class="absolute inset-0 flex items-center justify-center"
      :class="[config.fontSize, textColor]"
    >
      <span class="font-bold">{{ Math.round(percentage) }}%</span>
    </div>
  </div>
</template>
