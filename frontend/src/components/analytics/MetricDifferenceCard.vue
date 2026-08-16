<template>
  <div class="card">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <component
          :is="icon"
          class="h-5 w-5 text-gray-400"
        />
        <h3 class="text-body font-medium text-gray-700 dark:text-gray-300">{{ title }}</h3>
      </div>
    </div>

    <div class="mt-3 space-y-2">
      <!-- Value A -->
      <div class="flex items-center justify-between">
        <span class="text-body-xs text-gray-500 dark:text-gray-400">영상 A</span>
        <span class="font-semibold text-primary-600 dark:text-primary-400">
          {{ formatValue(valueA, unavailableA) }}
        </span>
      </div>

      <!-- Value B -->
      <div class="flex items-center justify-between">
        <span class="text-body-xs text-gray-500 dark:text-gray-400">영상 B</span>
        <span class="font-semibold text-warning-strong">
          {{ formatValue(valueB, unavailableB) }}
        </span>
      </div>

      <!-- Difference -->
      <div class="border-t border-gray-100 dark:border-gray-700 pt-2">
        <div class="flex items-center justify-between">
          <span class="text-caption text-gray-600 dark:text-gray-400">차이</span>
          <div class="flex items-center gap-1">
            <span v-if="differenceUnavailable" class="text-body font-semibold text-gray-500 dark:text-gray-400">
              {{ unavailableLabel }}
            </span>
            <template v-else>
              <component
                :is="differenceIcon"
                class="h-4 w-4"
                :class="differenceColorClass"
              />
              <span class="text-body font-bold" :class="differenceColorClass">
                {{ Math.abs(percentageDifference).toFixed(1) }}%
              </span>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowUpIcon, ArrowDownIcon, MinusIcon } from '@heroicons/vue/24/solid'
import type { Component } from 'vue'

interface Props {
  title: string
  valueA: number | null
  valueB: number | null
  icon: Component
  isPercentage?: boolean
  unavailableA?: boolean
  unavailableB?: boolean
  unavailableLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  isPercentage: false,
  unavailableA: false,
  unavailableB: false,
  unavailableLabel: 'Not available',
})

const differenceUnavailable = computed(() =>
  Boolean(props.unavailableA || props.unavailableB || props.valueA === null || props.valueB === null),
)

// Calculate percentage difference (A compared to B)
const percentageDifference = computed(() => {
  if (differenceUnavailable.value) return 0
  const valueA = props.valueA as number
  const valueB = props.valueB as number
  if (valueB === 0) {
    return valueA > 0 ? 100 : 0
  }
  return ((valueA - valueB) / valueB) * 100
})

// Determine icon and color
const differenceIcon = computed(() => {
  if (Math.abs(percentageDifference.value) < 0.1) return MinusIcon
  return percentageDifference.value > 0 ? ArrowUpIcon : ArrowDownIcon
})

const differenceColorClass = computed(() => {
  if (Math.abs(percentageDifference.value) < 0.1) {
    return 'text-gray-400'
  }
  return percentageDifference.value > 0
    ? 'text-success-strong'
    : 'text-error-strong'
})

// Format value
function formatValue(value: number | null, unavailable: boolean | undefined): string {
  if (unavailable || value === null) return props.unavailableLabel
  if (props.isPercentage) {
    return `${value.toFixed(2)}%`
  }
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}
</script>
