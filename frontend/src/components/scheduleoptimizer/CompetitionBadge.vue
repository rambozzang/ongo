<template>
  <span
    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
    :class="badgeClass"
  >
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { OptimalSlot } from '@/types/scheduleOptimizer'

const props = defineProps<{
  level: OptimalSlot['competitionLevel']
}>()

const config = computed(() => {
  const configs: Record<OptimalSlot['competitionLevel'], { label: string; color: string }> = {
    LOW: {
      label: '낮음',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    MEDIUM: {
      label: '보통',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    HIGH: {
      label: '높음',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
  }
  return configs[props.level] ?? configs.MEDIUM
})

const badgeClass = computed(() => config.value.color)
const label = computed(() => config.value.label)
</script>
