<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  status: 'DRAFT' | 'GENERATING' | 'COMPLETED' | 'SCHEDULED'
}

const props = defineProps<Props>()

const config = computed(() => {
  const configs: Record<string, { label: string; color: string; animate: boolean }> = {
    COMPLETED: {
      label: '완료',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
      animate: false,
    },
    GENERATING: {
      label: '생성중',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
      animate: true,
    },
    SCHEDULED: {
      label: '예약됨',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
      animate: false,
    },
    DRAFT: {
      label: '초안',
      color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
      animate: false,
    },
  }
  return configs[props.status] ?? configs.DRAFT
})
</script>

<template>
  <span
    :class="[
      'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
      config.color,
      config.animate ? 'animate-pulse' : '',
    ]"
  >
    {{ config.label }}
  </span>
</template>
