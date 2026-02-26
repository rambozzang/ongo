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
import type { Portfolio } from '@/types/portfolioBuilder'

const props = defineProps<{
  template: Portfolio['template']
}>()

const config = computed(() => {
  const configs: Record<Portfolio['template'], { label: string; color: string }> = {
    MINIMAL: {
      label: '미니멀',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
    CREATIVE: {
      label: '크리에이티브',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
    },
    PROFESSIONAL: {
      label: '프로페셔널',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    BOLD: {
      label: '볼드',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
  }
  return configs[props.template] ?? configs.MINIMAL
})

const badgeClass = computed(() => config.value.color)
const label = computed(() => config.value.label)
</script>
