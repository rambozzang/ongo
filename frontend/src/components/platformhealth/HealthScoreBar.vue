<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  score: number
  maxScore?: number
}>()

const max = computed(() => props.maxScore ?? 100)

const widthPct = computed(() => Math.min((props.score / max.value) * 100, 100))

const barColor = computed(() => {
  if (props.score >= 80) return 'bg-green-500'
  if (props.score >= 60) return 'bg-yellow-500'
  if (props.score >= 40) return 'bg-orange-500'
  return 'bg-red-500'
})

const textColor = computed(() => {
  if (props.score >= 80) return 'text-green-600 dark:text-green-400'
  if (props.score >= 60) return 'text-yellow-600 dark:text-yellow-400'
  if (props.score >= 40) return 'text-orange-600 dark:text-orange-400'
  return 'text-red-600 dark:text-red-400'
})
</script>

<template>
  <div class="flex items-center gap-3">
    <span class="w-20 text-right text-xs font-medium text-gray-600 dark:text-gray-400">
      {{ label }}
    </span>
    <div class="flex-1">
      <div class="h-4 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
        <div
          :class="barColor"
          class="h-full rounded-full transition-all duration-500"
          :style="{ width: `${widthPct}%` }"
        />
      </div>
    </div>
    <span :class="textColor" class="w-10 text-right text-xs font-bold">
      {{ score }}
    </span>
  </div>
</template>
