<template>
  <div class="card">
    <h3 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('prediction.competitorTitle') }}
    </h3>
    <p class="mb-4 text-xs text-gray-400 dark:text-gray-500">
      {{ $t('prediction.competitorDescription') }}
    </p>

    <div v-if="comparisons.length === 0" class="flex flex-col items-center justify-center py-12 text-gray-400 dark:text-gray-500">
      <ChartBarSquareIcon class="h-12 w-12 mb-3" />
      <p class="text-sm">{{ $t('prediction.noCompetitorData') }}</p>
    </div>

    <div v-else class="space-y-4">
      <div
        v-for="(comp, idx) in comparisons"
        :key="idx"
        class="rounded-lg border border-gray-200 dark:border-gray-700 p-4"
      >
        <div class="flex items-center justify-between mb-3">
          <span class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate max-w-[60%]">
            {{ comp.competitorTitle }}
          </span>
          <span
            class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium"
            :class="advantageBadgeClass(comp.advantage)"
          >
            <component :is="advantageIcon(comp.advantage)" class="h-3 w-3" />
            {{ advantageLabel(comp.advantage) }}
          </span>
        </div>

        <!-- Bar Comparison -->
        <div class="space-y-3">
          <!-- Views -->
          <div>
            <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400 mb-1">
              <span>{{ $t('prediction.views') }}</span>
              <span>
                {{ formatCompact(comp.yourPredictedViews) }} vs {{ formatCompact(comp.competitorViews) }}
              </span>
            </div>
            <div class="flex gap-1 h-3">
              <div
                class="rounded-l-full bg-primary-500 transition-all duration-500"
                :style="{ width: `${viewsBarWidth(comp, 'yours')}%` }"
              />
              <div
                class="rounded-r-full bg-gray-300 dark:bg-gray-600 transition-all duration-500"
                :style="{ width: `${viewsBarWidth(comp, 'competitor')}%` }"
              />
            </div>
          </div>

          <!-- Likes -->
          <div>
            <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400 mb-1">
              <span>{{ $t('prediction.likes') }}</span>
              <span>
                {{ formatCompact(comp.yourPredictedLikes) }} vs {{ formatCompact(comp.competitorLikes) }}
              </span>
            </div>
            <div class="flex gap-1 h-3">
              <div
                class="rounded-l-full bg-pink-500 transition-all duration-500"
                :style="{ width: `${likesBarWidth(comp, 'yours')}%` }"
              />
              <div
                class="rounded-r-full bg-gray-300 dark:bg-gray-600 transition-all duration-500"
                :style="{ width: `${likesBarWidth(comp, 'competitor')}%` }"
              />
            </div>
          </div>

          <!-- Engagement -->
          <div>
            <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400 mb-1">
              <span>{{ $t('prediction.engagementRate') }}</span>
              <span>
                {{ comp.yourPredictedEngagement }}% vs {{ comp.competitorEngagement }}%
              </span>
            </div>
            <div class="flex gap-1 h-3">
              <div
                class="rounded-l-full bg-green-500 transition-all duration-500"
                :style="{ width: `${engagementBarWidth(comp, 'yours')}%` }"
              />
              <div
                class="rounded-r-full bg-gray-300 dark:bg-gray-600 transition-all duration-500"
                :style="{ width: `${engagementBarWidth(comp, 'competitor')}%` }"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Legend -->
      <div class="flex items-center justify-center gap-6 pt-2 text-xs text-gray-500 dark:text-gray-400">
        <div class="flex items-center gap-1.5">
          <div class="h-2.5 w-2.5 rounded-full bg-primary-500" />
          <span>{{ $t('prediction.yourPrediction') }}</span>
        </div>
        <div class="flex items-center gap-1.5">
          <div class="h-2.5 w-2.5 rounded-full bg-gray-300 dark:bg-gray-600" />
          <span>{{ $t('prediction.competitor') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  ChartBarSquareIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
} from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'
import type { CompetitorComparison } from '@/types/prediction'

defineProps<{
  comparisons: CompetitorComparison[]
}>()

const { t } = useI18n({ useScope: 'global' })

function advantageBadgeClass(advantage: string): string {
  switch (advantage) {
    case 'ahead': return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'behind': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    default: return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
}

function advantageIcon(advantage: string) {
  switch (advantage) {
    case 'ahead': return ArrowTrendingUpIcon
    case 'behind': return ArrowTrendingDownIcon
    default: return MinusIcon
  }
}

function advantageLabel(advantage: string): string {
  switch (advantage) {
    case 'ahead': return t('prediction.ahead')
    case 'behind': return t('prediction.behind')
    default: return t('prediction.similar')
  }
}

function viewsBarWidth(comp: CompetitorComparison, side: 'yours' | 'competitor'): number {
  const total = comp.yourPredictedViews + comp.competitorViews
  if (total === 0) return 50
  return side === 'yours'
    ? (comp.yourPredictedViews / total) * 100
    : (comp.competitorViews / total) * 100
}

function likesBarWidth(comp: CompetitorComparison, side: 'yours' | 'competitor'): number {
  const total = comp.yourPredictedLikes + comp.competitorLikes
  if (total === 0) return 50
  return side === 'yours'
    ? (comp.yourPredictedLikes / total) * 100
    : (comp.competitorLikes / total) * 100
}

function engagementBarWidth(comp: CompetitorComparison, side: 'yours' | 'competitor'): number {
  const total = comp.yourPredictedEngagement + comp.competitorEngagement
  if (total === 0) return 50
  return side === 'yours'
    ? (comp.yourPredictedEngagement / total) * 100
    : (comp.competitorEngagement / total) * 100
}

function formatCompact(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}
</script>
