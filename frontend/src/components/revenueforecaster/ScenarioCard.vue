<template>
  <div
    class="cursor-pointer rounded-xl border-2 bg-white dark:bg-gray-900 p-4 shadow-sm transition-all hover:shadow-md"
    :class="selected
      ? 'border-primary-500 dark:border-primary-400 ring-1 ring-primary-500/20 dark:ring-primary-400/20'
      : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'"
    @click="$emit('select')"
  >
    <!-- Header -->
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <div
          class="flex h-8 w-8 items-center justify-center rounded-lg"
          :class="selected
            ? 'bg-primary-100 dark:bg-primary-900/30'
            : 'bg-gray-100 dark:bg-gray-800'"
        >
          <component
            :is="scenarioIcon"
            class="h-4 w-4"
            :class="selected
              ? 'text-primary-600 dark:text-primary-400'
              : 'text-gray-500 dark:text-gray-400'"
          />
        </div>
        <div>
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ scenario.name }}
          </h3>
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ scenario.description }}
          </p>
        </div>
      </div>
      <div
        v-if="selected"
        class="flex h-5 w-5 items-center justify-center rounded-full bg-primary-500"
      >
        <CheckIcon class="h-3 w-3 text-white" />
      </div>
    </div>

    <!-- Key Parameters -->
    <div class="mb-3 space-y-2">
      <div class="flex items-center justify-between text-xs">
        <span class="text-gray-500 dark:text-gray-400">
          {{ $t('revenueForecaster.scenario.uploadFrequency') }}
        </span>
        <span class="font-medium text-gray-900 dark:text-gray-100">
          {{ $t('revenueForecaster.scenario.perWeek', { count: scenario.uploadFrequency }) }}
        </span>
      </div>
      <div class="flex items-center justify-between text-xs">
        <span class="text-gray-500 dark:text-gray-400">
          {{ $t('revenueForecaster.scenario.avgViews') }}
        </span>
        <span class="font-medium text-gray-900 dark:text-gray-100">
          {{ formatNumber(scenario.avgViewsPerVideo) }}
        </span>
      </div>
      <div class="flex items-center justify-between text-xs">
        <span class="text-gray-500 dark:text-gray-400">
          {{ $t('revenueForecaster.scenario.growthRate') }}
        </span>
        <span class="font-medium text-green-600 dark:text-green-400">
          +{{ scenario.subscriberGrowthRate }}%
        </span>
      </div>
    </div>

    <!-- Divider -->
    <div class="border-t border-gray-100 dark:border-gray-800" />

    <!-- Total Forecast -->
    <div class="mt-3 text-center">
      <p class="text-xs text-gray-500 dark:text-gray-400">
        {{ $t('revenueForecaster.scenario.totalForecast') }}
      </p>
      <p
        class="mt-1 text-2xl font-bold"
        :class="selected
          ? 'text-primary-600 dark:text-primary-400'
          : 'text-gray-900 dark:text-gray-100'"
      >
        {{ formatRevenue(scenario.totalForecast) }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CheckIcon,
  ShieldCheckIcon,
  ChartBarIcon,
  RocketLaunchIcon,
} from '@heroicons/vue/24/outline'
import type { ForecastScenario } from '@/types/revenueForecaster'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  scenario: ForecastScenario
  selected: boolean
}>()

defineEmits<{
  select: []
}>()

const scenarioIcon = computed(() => {
  switch (props.scenario.id) {
    case 1:
      return ShieldCheckIcon
    case 3:
      return RocketLaunchIcon
    default:
      return ChartBarIcon
  }
})

function formatRevenue(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}${t('revenueForecaster.manwon')}`
  }
  return `${value.toLocaleString()}${t('revenueForecaster.won')}`
}

function formatNumber(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}${t('revenueForecaster.manwon')}`
  }
  return value.toLocaleString()
}
</script>
