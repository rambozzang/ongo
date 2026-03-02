<script setup lang="ts">
import { type Component } from 'vue'

defineProps<{
  label: string
  value: string | number
  icon?: Component
  trend?: { value: number; positive: boolean }
  color?: 'primary' | 'success' | 'warning' | 'danger'
}>()

const colorMap = {
  primary: 'bg-primary-100 text-primary-600 dark:bg-primary-900/30 dark:text-primary-400',
  success: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
  warning: 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400',
  danger: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
}
</script>

<template>
  <div class="card p-4">
    <div class="flex items-center justify-between">
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ label }}</p>
      <div
        v-if="icon"
        class="flex h-8 w-8 items-center justify-center rounded-lg"
        :class="colorMap[color ?? 'primary']"
      >
        <component :is="icon" class="h-4 w-4" />
      </div>
    </div>
    <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">{{ value }}</p>
    <div v-if="trend" class="mt-1 flex items-center gap-1 text-sm">
      <span :class="trend.positive ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'">
        {{ trend.positive ? '+' : '' }}{{ trend.value }}%
      </span>
    </div>
    <slot />
  </div>
</template>
