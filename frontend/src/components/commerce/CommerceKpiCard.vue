<template>
  <div class="card border-t-4" :class="borderColorClass">
    <div class="flex items-start justify-between">
      <div>
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ label }}</p>
        <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ formattedValue }}
        </p>
        <div v-if="growth !== undefined" class="mt-1 flex items-center gap-1 text-xs">
          <span
            :class="growth >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
          >
            {{ growth >= 0 ? '+' : '' }}{{ growth.toFixed(1) }}%
          </span>
          <span class="text-gray-400 dark:text-gray-500">{{ $t('commerce.vsLastPeriod') }}</span>
        </div>
      </div>
      <div class="rounded-lg p-2" :class="iconBgClass">
        <component :is="icon" class="h-6 w-6" :class="iconColorClass" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  label: string
  formattedValue: string
  growth?: number
  icon: Component
  borderColorClass: string
  iconBgClass: string
  iconColorClass: string
}>()
</script>
