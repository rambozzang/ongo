<template>
  <div
    class="inline-flex items-center justify-center rounded-full px-2.5 py-1 text-xs font-bold"
    :class="badgeClass"
  >
    {{ score }}
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  score: number
}>()

const badgeClass = computed(() => {
  if (props.score >= 80) return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
  if (props.score >= 60) return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  if (props.score >= 40) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
  return 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400'
})
</script>
