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
  success: 'bg-success-subtle text-success-strong',
  warning: 'bg-warning-subtle text-warning-strong',
  danger: 'bg-error-subtle text-error-strong',
}
</script>

<template>
  <div class="card p-4">
    <div class="flex items-center justify-between">
      <p class="text-body text-gray-500 dark:text-gray-400">{{ label }}</p>
      <div
        v-if="icon"
        class="flex h-8 w-8 items-center justify-center rounded-lg"
        :class="colorMap[color ?? 'primary']"
      >
        <component :is="icon" class="h-4 w-4" />
      </div>
    </div>
    <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">{{ value }}</p>
    <div v-if="trend" class="mt-1 flex items-center gap-1 text-body">
      <span :class="trend.positive ? 'text-success-strong' : 'text-error-strong'">
        {{ trend.positive ? '+' : '' }}{{ trend.value }}%
      </span>
    </div>
    <slot />
  </div>
</template>
