<script setup lang="ts">
import {
  ClockIcon,
  BoltIcon,
  ChartBarIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'

interface Props {
  triggerType: 'SCHEDULE' | 'EVENT' | 'THRESHOLD' | 'KEYWORD'
}

defineProps<Props>()

const triggerConfig: Record<string, { icon: typeof ClockIcon; label: string; class: string }> = {
  SCHEDULE: {
    icon: ClockIcon,
    label: '예약',
    class: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  },
  EVENT: {
    icon: BoltIcon,
    label: '이벤트',
    class: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  },
  THRESHOLD: {
    icon: ChartBarIcon,
    label: '임계값',
    class: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  },
  KEYWORD: {
    icon: TagIcon,
    label: '키워드',
    class: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  },
}
</script>

<template>
  <span
    :class="triggerConfig[triggerType].class"
    class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium"
  >
    <component :is="triggerConfig[triggerType].icon" class="h-3.5 w-3.5" />
    {{ triggerConfig[triggerType].label }}
  </span>
</template>
