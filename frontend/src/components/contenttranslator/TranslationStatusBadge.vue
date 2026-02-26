<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: 'PENDING' | 'TRANSLATING' | 'COMPLETED' | 'FAILED'
}>()

const statusConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    PENDING: {
      label: '대기중',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
    TRANSLATING: {
      label: '번역중',
      color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    },
    COMPLETED: {
      label: '완료',
      color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    },
    FAILED: {
      label: '실패',
      color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
    },
  }
  return configs[props.status] ?? configs.PENDING
})
</script>

<template>
  <span
    :class="statusConfig.color"
    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
  >
    {{ statusConfig.label }}
  </span>
</template>
