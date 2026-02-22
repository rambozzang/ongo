<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ label }}</span>
      <span class="text-sm text-gray-500 dark:text-gray-400">
        {{ current.toLocaleString() }} / {{ max === -1 ? '무제한' : max.toLocaleString() }} {{ unit }}
      </span>
    </div>
    <div class="h-2.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
      <div
        class="h-full rounded-full transition-all duration-700 ease-out"
        :class="progressColorClass"
        :style="{ width: progressPercentage + '%' }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

interface Props {
  label: string
  current: number
  max: number
  unit: string
  colorClass?: string
}

const props = withDefaults(defineProps<Props>(), {
  colorClass: '',
})

const animationStarted = ref(false)

const progressPercentage = computed(() => {
  if (!animationStarted.value) return 0
  if (props.max === -1) return 0
  if (props.max === 0) return 100
  return Math.min(100, Math.round((props.current / props.max) * 100))
})

const progressColorClass = computed(() => {
  if (props.colorClass) return props.colorClass

  const percentage = props.max === -1 ? 0 : (props.current / props.max) * 100

  if (percentage >= 80) return 'bg-red-500'
  if (percentage >= 60) return 'bg-yellow-500'
  return 'bg-green-500'
})

let animationTimeout: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  // Trigger animation after mount
  animationTimeout = setTimeout(() => {
    animationStarted.value = true
  }, 100)
})

onUnmounted(() => {
  if (animationTimeout) clearTimeout(animationTimeout)
})
</script>
