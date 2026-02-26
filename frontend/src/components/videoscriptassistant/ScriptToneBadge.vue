<script setup lang="ts">
defineProps<{
  tone: 'CASUAL' | 'PROFESSIONAL' | 'HUMOROUS' | 'EDUCATIONAL' | 'DRAMATIC'
}>()

const toneConfig: Record<string, { bg: string; text: string; label: string }> = {
  CASUAL: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: 'Casual' },
  PROFESSIONAL: { bg: 'bg-indigo-100 dark:bg-indigo-900/30', text: 'text-indigo-700 dark:text-indigo-300', label: 'Professional' },
  HUMOROUS: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: 'Humorous' },
  EDUCATIONAL: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: 'Educational' },
  DRAMATIC: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: 'Dramatic' },
}
</script>

<template>
  <span
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
    :class="[toneConfig[tone]?.bg, toneConfig[tone]?.text]"
  >
    {{ toneConfig[tone]?.label ?? tone }}
  </span>
</template>
