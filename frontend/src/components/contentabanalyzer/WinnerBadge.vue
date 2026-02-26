<script setup lang="ts">
import { computed } from 'vue'
import { TrophyIcon } from '@heroicons/vue/24/outline'

interface Props {
  winner: 'A' | 'B' | null
}

const props = defineProps<Props>()

const badgeConfig = computed(() => {
  if (props.winner === 'A') {
    return {
      label: 'A',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
      showTrophy: true,
    }
  }
  if (props.winner === 'B') {
    return {
      label: 'B',
      color: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
      showTrophy: true,
    }
  }
  return {
    label: '미정',
    color: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
    showTrophy: false,
  }
})
</script>

<template>
  <span
    :class="['inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold', badgeConfig.color]"
  >
    <TrophyIcon v-if="badgeConfig.showTrophy" class="w-3 h-3" />
    {{ badgeConfig.label }}
  </span>
</template>
