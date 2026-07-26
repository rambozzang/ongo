<template>
  <div class="flex items-start gap-3 rounded-lg bg-gray-50 dark:bg-gray-800 p-3">
    <div class="flex-shrink-0">
      <component
        :is="icon"
        class="h-5 w-5"
        :class="iconColorClass"
      />
    </div>
    <p class="text-body text-gray-700 dark:text-gray-300">
      {{ text }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  InformationCircleIcon,
} from '@heroicons/vue/24/outline'

interface Props {
  text: string
  isPositive: boolean | null
}

const props = defineProps<Props>()

const icon = computed(() => {
  if (props.isPositive === null) return InformationCircleIcon
  return props.isPositive ? ArrowTrendingUpIcon : ArrowTrendingDownIcon
})

const iconColorClass = computed(() => {
  if (props.isPositive === null) return 'text-gray-400'
  return props.isPositive
    ? 'text-success-strong'
    : 'text-error-strong'
})
</script>
