<template>
  <div class="space-y-4">
    <!-- Score Circle -->
    <div class="flex items-center gap-4">
      <div class="relative h-16 w-16 flex-shrink-0">
        <svg class="h-16 w-16 -rotate-90" viewBox="0 0 64 64">
          <circle
            cx="32" cy="32" r="28"
            fill="none"
            stroke-width="4"
            class="stroke-gray-200 dark:stroke-gray-700"
          />
          <circle
            cx="32" cy="32" r="28"
            fill="none"
            stroke-width="4"
            stroke-linecap="round"
            :stroke-dasharray="circumference"
            :stroke-dashoffset="strokeOffset"
            :class="scoreStrokeClass"
          />
        </svg>
        <div class="absolute inset-0 flex items-center justify-center">
          <span class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ score }}</span>
        </div>
      </div>
      <div>
        <p class="text-sm font-medium text-gray-900 dark:text-gray-100">
          {{ $t('hashtagStrategy.strategyScore') }}
        </p>
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ scoreLabel }}</p>
      </div>
    </div>

    <!-- Reach Bar -->
    <div>
      <div class="mb-1 flex items-center justify-between">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">
          {{ $t('hashtagStrategy.estimatedReach') }}
        </span>
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(totalReach) }}
        </span>
      </div>
      <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-full rounded-full transition-all duration-500"
          :class="reachBarClass"
          :style="{ width: `${reachPercentage}%` }"
        />
      </div>
    </div>

    <!-- Difficulty Badge -->
    <div class="flex items-center gap-2">
      <span class="text-xs font-medium text-gray-500 dark:text-gray-400">
        {{ $t('hashtagStrategy.difficulty') }}
      </span>
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="difficultyBadgeClass"
      >
        {{ difficultyLabel }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  totalReach: number
  difficulty: string
  score: number
}>()

const { t } = useI18n({ useScope: 'global' })

const circumference = 2 * Math.PI * 28

const strokeOffset = computed(() => {
  return circumference - (props.score / 100) * circumference
})

const scoreStrokeClass = computed(() => {
  if (props.score >= 80) return 'stroke-green-500'
  if (props.score >= 60) return 'stroke-yellow-500'
  if (props.score >= 40) return 'stroke-orange-500'
  return 'stroke-red-500'
})

const scoreLabel = computed(() => {
  if (props.score >= 80) return t('hashtagStrategy.scoreExcellent')
  if (props.score >= 60) return t('hashtagStrategy.scoreGood')
  if (props.score >= 40) return t('hashtagStrategy.scoreFair')
  return t('hashtagStrategy.scorePoor')
})

const maxReach = 5000000
const reachPercentage = computed(() => {
  return Math.min(100, (props.totalReach / maxReach) * 100)
})

const reachBarClass = computed(() => {
  switch (props.difficulty) {
    case 'EASY': return 'bg-green-500'
    case 'MEDIUM': return 'bg-yellow-500'
    case 'HARD': return 'bg-orange-500'
    case 'VERY_HARD': return 'bg-red-500'
    default: return 'bg-primary-500'
  }
})

const difficultyBadgeClass = computed(() => {
  switch (props.difficulty) {
    case 'EASY': return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'MEDIUM': return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'HARD': return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400'
    case 'VERY_HARD': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    default: return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
})

const difficultyLabel = computed(() => {
  switch (props.difficulty) {
    case 'EASY': return t('hashtagStrategy.difficultyEasy')
    case 'MEDIUM': return t('hashtagStrategy.difficultyMedium')
    case 'HARD': return t('hashtagStrategy.difficultyHard')
    case 'VERY_HARD': return t('hashtagStrategy.difficultyVeryHard')
    default: return props.difficulty
  }
})

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toString()
}
</script>
