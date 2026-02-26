<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  label: string
  percentile: number
  myValue?: string
}

const props = defineProps<Props>()

const barColor = computed(() => {
  const p = props.percentile
  if (p >= 75) return 'bg-green-500 dark:bg-green-400'
  if (p >= 50) return 'bg-blue-500 dark:bg-blue-400'
  if (p >= 25) return 'bg-yellow-500 dark:bg-yellow-400'
  return 'bg-red-500 dark:bg-red-400'
})

const textColor = computed(() => {
  const p = props.percentile
  if (p >= 75) return 'text-green-600 dark:text-green-400'
  if (p >= 50) return 'text-blue-600 dark:text-blue-400'
  if (p >= 25) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

const badgeColor = computed(() => {
  const p = props.percentile
  if (p >= 75) return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
  if (p >= 50) return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  if (p >= 25) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
  return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
})

const gradeLabel = computed(() => {
  const p = props.percentile
  if (p >= 90) return '최상위'
  if (p >= 75) return '우수'
  if (p >= 50) return '평균 이상'
  if (p >= 25) return '평균 이하'
  return '개선 필요'
})
</script>

<template>
  <div class="flex items-center gap-4 rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-800">
    <!-- Label -->
    <div class="w-24 flex-shrink-0">
      <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ label }}</p>
      <p v-if="myValue" class="text-xs text-gray-500 dark:text-gray-400">{{ myValue }}</p>
    </div>

    <!-- Horizontal Progress Bar -->
    <div class="flex flex-1 items-center gap-3">
      <div class="relative flex-1 h-4 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          :class="['h-full rounded-full transition-all duration-700 ease-out', barColor]"
          :style="{ width: `${percentile}%` }"
        />
        <!-- Marker lines at 25%, 50%, 75% -->
        <div class="absolute top-0 left-1/4 h-full w-px bg-gray-300/50 dark:bg-gray-600/50" />
        <div class="absolute top-0 left-1/2 h-full w-px bg-gray-300/50 dark:bg-gray-600/50" />
        <div class="absolute top-0 left-3/4 h-full w-px bg-gray-300/50 dark:bg-gray-600/50" />
      </div>

      <!-- Percentile Value -->
      <span :class="['w-12 text-right text-sm font-bold', textColor]">
        {{ percentile }}%
      </span>
    </div>

    <!-- Grade Badge -->
    <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium flex-shrink-0', badgeColor]">
      {{ gradeLabel }}
    </span>
  </div>
</template>
