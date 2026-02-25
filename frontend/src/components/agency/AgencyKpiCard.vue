<template>
  <div
    class="group cursor-pointer rounded-xl border border-gray-200 bg-white p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
    @click="$emit('click')"
  >
    <div class="flex items-center justify-between">
      <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ title }}</p>
      <div
        class="flex h-10 w-10 items-center justify-center rounded-lg transition-colors"
        :class="iconBgClass"
      >
        <component :is="icon" class="h-5 w-5" :class="iconColorClass" />
      </div>
    </div>
    <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
      {{ formattedValue }}
    </p>
    <div v-if="change !== undefined" class="mt-2 flex items-center gap-1 text-sm">
      <span :class="change >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'">
        {{ change >= 0 ? '↑' : '↓' }}
        {{ changeType === 'percent' ? `${Math.abs(change)}%` : formatCompact(Math.abs(change)) }}
      </span>
      <span class="text-gray-400 dark:text-gray-500">{{ $t('agency.vsLastWeek') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'

const props = defineProps<{
  title: string
  value: number
  change?: number
  changeType?: 'percent' | 'number'
  icon: Component
  color: 'indigo' | 'blue' | 'green' | 'amber'
}>()

defineEmits<{
  click: []
}>()

const colorMap = {
  indigo: { bg: 'bg-indigo-50 dark:bg-indigo-900/20', icon: 'text-indigo-600 dark:text-indigo-400' },
  blue: { bg: 'bg-blue-50 dark:bg-blue-900/20', icon: 'text-blue-600 dark:text-blue-400' },
  green: { bg: 'bg-green-50 dark:bg-green-900/20', icon: 'text-green-600 dark:text-green-400' },
  amber: { bg: 'bg-amber-50 dark:bg-amber-900/20', icon: 'text-amber-600 dark:text-amber-400' },
}

const iconBgClass = computed(() => colorMap[props.color].bg)
const iconColorClass = computed(() => colorMap[props.color].icon)

function formatCompact(value: number): string {
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B`
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}

const formattedValue = computed(() => formatCompact(props.value))
</script>
