<script setup lang="ts">
import { computed } from 'vue'
import { TrophyIcon } from '@heroicons/vue/24/outline'

interface Props {
  title: string
  achievedAt: string
  type?: string
}

const props = defineProps<Props>()

const formattedDate = computed(() => {
  return new Date(props.achievedAt).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
})
</script>

<template>
  <div class="inline-flex items-center gap-2 rounded-full border border-yellow-300 bg-gradient-to-r from-yellow-50 to-amber-50 px-3 py-1.5 dark:border-yellow-700 dark:from-yellow-900/30 dark:to-amber-900/30">
    <TrophyIcon class="h-4 w-4 text-yellow-500 dark:text-yellow-400" />
    <span class="text-xs font-semibold text-yellow-800 dark:text-yellow-300">{{ title }}</span>
    <span class="text-xs text-yellow-600 dark:text-yellow-400">{{ formattedDate }}</span>
  </div>
</template>
