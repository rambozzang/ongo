<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header: Icon + Label -->
    <div class="mb-3 flex items-center gap-3">
      <div
        class="flex h-10 w-10 items-center justify-center rounded-lg"
        :class="goalIconBgClass"
      >
        <component :is="goalIcon" class="h-5 w-5" :class="goalIconColorClass" />
      </div>
      <div>
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t(`growthCoach.goalTypes.${goal.type}`) }}
        </h3>
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ $t('growthCoach.goals.progress') }}: {{ goal.progress }}%
        </span>
      </div>
    </div>

    <!-- Progress Bar -->
    <div class="mb-3">
      <div class="h-2.5 w-full rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-2.5 rounded-full transition-all duration-500"
          :class="progressBarColor"
          :style="{ width: `${Math.min(goal.progress, 100)}%` }"
        />
      </div>
    </div>

    <!-- Current / Target -->
    <div class="mb-3 flex items-center justify-between text-sm">
      <div>
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('growthCoach.goals.current') }}</span>
        <p class="font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(goal.currentValue) }}</p>
      </div>
      <div class="text-right">
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('growthCoach.goals.target') }}</span>
        <p class="font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(goal.targetValue) }}</p>
      </div>
    </div>

    <!-- Deadline + Days remaining -->
    <div class="flex items-center gap-2 text-xs">
      <CalendarDaysIcon class="h-4 w-4 text-gray-400 dark:text-gray-500" />
      <span class="text-gray-500 dark:text-gray-400">{{ $t('growthCoach.goals.deadline') }}: {{ goal.deadline }}</span>
      <span
        class="ml-auto rounded-full px-2 py-0.5 text-xs font-medium"
        :class="daysRemainingClass"
      >
        {{ daysRemainingText }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  UsersIcon,
  EyeIcon,
  HeartIcon,
  CurrencyDollarIcon,
  ArrowUpTrayIcon,
  CalendarDaysIcon,
} from '@heroicons/vue/24/outline'
import type { GrowthGoal } from '@/types/growthCoach'

const props = defineProps<{
  goal: GrowthGoal
}>()

const { t } = useI18n({ useScope: 'global' })

const goalIcon = computed(() => {
  switch (props.goal.type) {
    case 'SUBSCRIBERS': return UsersIcon
    case 'VIEWS': return EyeIcon
    case 'ENGAGEMENT': return HeartIcon
    case 'REVENUE': return CurrencyDollarIcon
    case 'UPLOADS': return ArrowUpTrayIcon
    default: return UsersIcon
  }
})

const goalIconBgClass = computed(() => {
  switch (props.goal.type) {
    case 'SUBSCRIBERS': return 'bg-blue-100 dark:bg-blue-900/30'
    case 'VIEWS': return 'bg-purple-100 dark:bg-purple-900/30'
    case 'ENGAGEMENT': return 'bg-pink-100 dark:bg-pink-900/30'
    case 'REVENUE': return 'bg-green-100 dark:bg-green-900/30'
    case 'UPLOADS': return 'bg-orange-100 dark:bg-orange-900/30'
    default: return 'bg-gray-100 dark:bg-gray-800'
  }
})

const goalIconColorClass = computed(() => {
  switch (props.goal.type) {
    case 'SUBSCRIBERS': return 'text-blue-600 dark:text-blue-400'
    case 'VIEWS': return 'text-purple-600 dark:text-purple-400'
    case 'ENGAGEMENT': return 'text-pink-600 dark:text-pink-400'
    case 'REVENUE': return 'text-green-600 dark:text-green-400'
    case 'UPLOADS': return 'text-orange-600 dark:text-orange-400'
    default: return 'text-gray-600 dark:text-gray-400'
  }
})

const progressBarColor = computed(() => {
  if (props.goal.progress < 50) return 'bg-red-500 dark:bg-red-400'
  if (props.goal.progress < 80) return 'bg-yellow-500 dark:bg-yellow-400'
  return 'bg-green-500 dark:bg-green-400'
})

const daysRemaining = computed(() => {
  const now = new Date()
  const deadline = new Date(props.goal.deadline)
  const diff = Math.ceil((deadline.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  return diff
})

const daysRemainingText = computed(() => {
  if (daysRemaining.value >= 0) {
    return `${daysRemaining.value}${t('growthCoach.goals.daysRemaining')}`
  }
  return `${Math.abs(daysRemaining.value)}${t('growthCoach.goals.daysOverdue')}`
})

const daysRemainingClass = computed(() => {
  if (daysRemaining.value < 0) return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
  if (daysRemaining.value <= 7) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
  return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
})

function formatNumber(num: number): string {
  if (num >= 1_000_000) return `${(num / 1_000_000).toFixed(1)}M`
  if (num >= 1_000) return `${(num / 1_000).toFixed(1)}K`
  return num.toLocaleString()
}
</script>
