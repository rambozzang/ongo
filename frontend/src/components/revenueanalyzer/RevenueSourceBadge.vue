<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  source: 'AD' | 'MEMBERSHIP' | 'SUPERCHAT' | 'SPONSORSHIP' | 'MERCHANDISE' | 'AFFILIATE' | string
}>()

const sourceConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    AD: {
      label: '광고',
      color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    },
    MEMBERSHIP: {
      label: '멤버십',
      color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
    },
    SUPERCHAT: {
      label: '슈퍼챗',
      color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    SPONSORSHIP: {
      label: '협찬',
      color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    },
    MERCHANDISE: {
      label: '굿즈',
      color: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-300',
    },
    AFFILIATE: {
      label: '제휴',
      color: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
    },
  }
  return configs[props.source] ?? { label: props.source, color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' }
})
</script>

<template>
  <span
    :class="sourceConfig.color"
    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
  >
    {{ sourceConfig.label }}
  </span>
</template>
