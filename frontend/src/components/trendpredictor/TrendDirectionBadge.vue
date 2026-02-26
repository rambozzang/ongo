<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
} from '@heroicons/vue/24/outline'

interface Props {
  direction: 'RISING' | 'STABLE' | 'DECLINING'
}

const props = defineProps<Props>()

const config = computed(() => {
  const configs: Record<string, { label: string; color: string; icon: typeof ArrowTrendingUpIcon }> = {
    RISING: {
      label: '상승',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
      icon: ArrowTrendingUpIcon,
    },
    STABLE: {
      label: '안정',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
      icon: MinusIcon,
    },
    DECLINING: {
      label: '하락',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
      icon: ArrowTrendingDownIcon,
    },
  }
  return configs[props.direction] ?? configs.STABLE
})
</script>

<template>
  <span
    :class="[
      'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium',
      config.color,
    ]"
  >
    <component :is="config.icon" class="w-3.5 h-3.5" />
    {{ config.label }}
  </span>
</template>
