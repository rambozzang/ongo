<template>
  <div v-if="variant === 'linear'" class="flex items-center gap-3">
    <div class="relative flex-1 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700" :class="linearHeightClass">
      <div
        class="h-full rounded-full transition-all duration-500 ease-out"
        :class="[colorClass, animated ? 'shimmer' : '']"
        :style="{ width: `${clampedValue}%` }"
      />
    </div>
    <span
      v-if="showLabel"
      class="min-w-[3rem] text-right text-sm font-medium text-gray-700 dark:text-gray-300"
    >
      {{ clampedValue }}%
    </span>
  </div>
  <div
    v-else
    class="relative inline-flex items-center justify-center"
    :style="{ width: circularSize, height: circularSize }"
  >
    <svg
      class="transform -rotate-90"
      :width="circularSize"
      :height="circularSize"
    >
      <circle
        :cx="circularSizeNum / 2"
        :cy="circularSizeNum / 2"
        :r="radius"
        stroke-width="4"
        fill="none"
        class="stroke-gray-200 dark:stroke-gray-700"
      />
      <circle
        :cx="circularSizeNum / 2"
        :cy="circularSizeNum / 2"
        :r="radius"
        :stroke-width="strokeWidth"
        fill="none"
        :class="strokeColorClass"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
        stroke-linecap="round"
        class="transition-all duration-500 ease-out"
      />
    </svg>
    <span
      v-if="showLabel"
      class="absolute inset-0 flex items-center justify-center text-sm font-semibold text-gray-700 dark:text-gray-300"
    >
      {{ clampedValue }}%
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  variant?: 'linear' | 'circular'
  size?: 'sm' | 'md' | 'lg'
  color?: 'primary' | 'success' | 'warning' | 'danger'
  showLabel?: boolean
  animated?: boolean
}>(), {
  variant: 'linear',
  size: 'md',
  color: 'primary',
  showLabel: false,
  animated: true,
})

const clampedValue = computed(() => {
  return Math.min(Math.max(props.value, 0), 100)
})

const linearHeightClass = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'h-1'
    case 'md':
      return 'h-2'
    case 'lg':
      return 'h-3'
    default:
      return 'h-2'
  }
})

const colorClass = computed(() => {
  switch (props.color) {
    case 'primary':
      return 'bg-gradient-to-r from-primary-500 to-primary-600'
    case 'success':
      return 'bg-gradient-to-r from-green-500 to-green-600'
    case 'warning':
      return 'bg-gradient-to-r from-yellow-500 to-yellow-600'
    case 'danger':
      return 'bg-gradient-to-r from-red-500 to-red-600'
    default:
      return ''
  }
})

const strokeColorClass = computed(() => {
  switch (props.color) {
    case 'primary':
      return 'stroke-primary-600 dark:stroke-primary-500'
    case 'success':
      return 'stroke-green-600 dark:stroke-green-500'
    case 'warning':
      return 'stroke-yellow-600 dark:stroke-yellow-500'
    case 'danger':
      return 'stroke-red-600 dark:stroke-red-500'
    default:
      return 'stroke-primary-600'
  }
})

const circularSizeNum = computed(() => {
  switch (props.size) {
    case 'sm':
      return 32
    case 'md':
      return 48
    case 'lg':
      return 64
    default:
      return 48
  }
})

const circularSize = computed(() => {
  return `${circularSizeNum.value}px`
})

const strokeWidth = computed(() => {
  switch (props.size) {
    case 'sm':
      return 3
    case 'md':
      return 4
    case 'lg':
      return 5
    default:
      return 4
  }
})

const radius = computed(() => {
  return (circularSizeNum.value - strokeWidth.value) / 2
})

const circumference = computed(() => {
  return 2 * Math.PI * radius.value
})

const dashOffset = computed(() => {
  return circumference.value - (clampedValue.value / 100) * circumference.value
})
</script>

<style scoped>
.shimmer {
  position: relative;
  overflow: hidden;
}

.shimmer::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.4),
    transparent
  );
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}
</style>
