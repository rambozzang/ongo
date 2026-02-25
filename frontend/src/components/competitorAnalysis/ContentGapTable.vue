<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useMediaQuery } from '@vueuse/core'
import { PlusCircleIcon } from '@heroicons/vue/24/outline'
import type { ContentGap } from '@/types/competitorAnalysis'

defineProps<{
  gaps: ContentGap[]
}>()

const emit = defineEmits<{
  (e: 'createContent', gapId: number): void
}>()

const { t } = useI18n({ useScope: 'global' })
const isTablet = useMediaQuery('(min-width: 768px)')

function formatNumber(num: number): string {
  if (num >= 1_000_000) return `${(num / 1_000_000).toFixed(1)}M`
  if (num >= 1_000) return `${(num / 1_000).toFixed(1)}K`
  return num.toLocaleString()
}

function difficultyColor(difficulty: 'LOW' | 'MEDIUM' | 'HIGH'): string {
  switch (difficulty) {
    case 'LOW':
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
    case 'MEDIUM':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'HIGH':
      return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'
  }
}

function opportunityColor(opportunity: 'LOW' | 'MEDIUM' | 'HIGH'): string {
  switch (opportunity) {
    case 'LOW':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
    case 'MEDIUM':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400'
    case 'HIGH':
      return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400'
  }
}
</script>

<template>
  <div>
    <!-- Desktop: table view -->
    <div v-if="isTablet" class="overflow-x-auto">
      <table v-if="gaps.length > 0" class="w-full text-left text-sm">
        <thead>
          <tr class="border-b border-gray-200 text-xs uppercase text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <th class="whitespace-nowrap px-3 py-3 font-medium">
              {{ t('competitorAnalysis.contentGaps.keyword') }}
            </th>
            <th class="whitespace-nowrap px-3 py-3 text-right font-medium">
              {{ t('competitorAnalysis.contentGaps.competitorCount') }}
            </th>
            <th class="whitespace-nowrap px-3 py-3 text-right font-medium">
              {{ t('competitorAnalysis.contentGaps.avgViews') }}
            </th>
            <th class="whitespace-nowrap px-3 py-3 font-medium">
              {{ t('competitorAnalysis.contentGaps.difficulty') }}
            </th>
            <th class="whitespace-nowrap px-3 py-3 font-medium">
              {{ t('competitorAnalysis.contentGaps.opportunity') }}
            </th>
            <th class="whitespace-nowrap px-3 py-3 font-medium">
              {{ t('competitorAnalysis.contentGaps.suggestedTitle') }}
            </th>
            <th class="whitespace-nowrap px-3 py-3 font-medium">
              {{ t('competitorAnalysis.contentGaps.action') }}
            </th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
          <tr
            v-for="gap in gaps"
            :key="gap.id"
            class="transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50"
          >
            <td class="whitespace-nowrap px-3 py-3 font-medium text-gray-900 dark:text-white">
              {{ gap.keyword }}
            </td>
            <td class="whitespace-nowrap px-3 py-3 text-right text-gray-600 dark:text-gray-300">
              {{ gap.competitorCount }}
            </td>
            <td class="whitespace-nowrap px-3 py-3 text-right text-gray-600 dark:text-gray-300">
              {{ formatNumber(gap.avgViews) }}
            </td>
            <td class="px-3 py-3">
              <span
                :class="[
                  'inline-block rounded-full px-2 py-0.5 text-xs font-medium',
                  difficultyColor(gap.difficulty),
                ]"
              >
                {{ t(`competitorAnalysis.difficulty.${gap.difficulty}`) }}
              </span>
            </td>
            <td class="px-3 py-3">
              <span
                :class="[
                  'inline-block rounded-full px-2 py-0.5 text-xs font-medium',
                  opportunityColor(gap.opportunity),
                ]"
              >
                {{ t(`competitorAnalysis.opportunity.${gap.opportunity}`) }}
              </span>
            </td>
            <td class="max-w-[200px] truncate px-3 py-3 text-sm text-gray-500 dark:text-gray-400">
              {{ gap.suggestedTitle || '-' }}
            </td>
            <td class="px-3 py-3">
              <button
                class="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20"
                @click="emit('createContent', gap.id)"
              >
                <PlusCircleIcon class="h-4 w-4" />
                {{ t('competitorAnalysis.contentGaps.createContent') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Empty state for desktop -->
      <div v-else class="py-12 text-center">
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ t('competitorAnalysis.contentGaps.noGaps') }}
        </p>
        <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
          {{ t('competitorAnalysis.contentGaps.noGapsDesc') }}
        </p>
      </div>
    </div>

    <!-- Mobile: card view -->
    <div v-else>
      <div v-if="gaps.length > 0" class="space-y-3">
        <div
          v-for="gap in gaps"
          :key="gap.id"
          class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
        >
          <div class="mb-2 flex items-start justify-between">
            <h4 class="text-sm font-semibold text-gray-900 dark:text-white">
              {{ gap.keyword }}
            </h4>
            <div class="flex gap-1">
              <span
                :class="[
                  'inline-block rounded-full px-2 py-0.5 text-[10px] font-medium',
                  difficultyColor(gap.difficulty),
                ]"
              >
                {{ t(`competitorAnalysis.difficulty.${gap.difficulty}`) }}
              </span>
              <span
                :class="[
                  'inline-block rounded-full px-2 py-0.5 text-[10px] font-medium',
                  opportunityColor(gap.opportunity),
                ]"
              >
                {{ t(`competitorAnalysis.opportunity.${gap.opportunity}`) }}
              </span>
            </div>
          </div>

          <div class="mb-3 space-y-1 text-xs text-gray-500 dark:text-gray-400">
            <div class="flex justify-between">
              <span>{{ t('competitorAnalysis.contentGaps.competitorCount') }}</span>
              <span class="font-medium text-gray-700 dark:text-gray-300">{{ gap.competitorCount }}</span>
            </div>
            <div class="flex justify-between">
              <span>{{ t('competitorAnalysis.contentGaps.avgViews') }}</span>
              <span class="font-medium text-gray-700 dark:text-gray-300">{{ formatNumber(gap.avgViews) }}</span>
            </div>
            <div v-if="gap.suggestedTitle" class="pt-1">
              <span class="text-gray-400 dark:text-gray-500">{{ t('competitorAnalysis.contentGaps.suggestedTitle') }}:</span>
              <p class="mt-0.5 text-gray-700 dark:text-gray-300">{{ gap.suggestedTitle }}</p>
            </div>
          </div>

          <button
            class="inline-flex w-full items-center justify-center gap-1.5 rounded-lg border border-primary-200 bg-primary-50 px-3 py-2 text-xs font-medium text-primary-700 transition-colors hover:bg-primary-100 dark:border-primary-800 dark:bg-primary-900/20 dark:text-primary-400 dark:hover:bg-primary-900/30"
            @click="emit('createContent', gap.id)"
          >
            <PlusCircleIcon class="h-4 w-4" />
            {{ t('competitorAnalysis.contentGaps.createContent') }}
          </button>
        </div>
      </div>

      <!-- Empty state for mobile -->
      <div v-else class="py-12 text-center">
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ t('competitorAnalysis.contentGaps.noGaps') }}
        </p>
        <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
          {{ t('competitorAnalysis.contentGaps.noGapsDesc') }}
        </p>
      </div>
    </div>
  </div>
</template>
