<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ClockIcon,
  CalendarDaysIcon,
  ChartBarIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'
import { useScheduleOptimizerStore } from '@/stores/scheduleOptimizer'
import { useLocale } from '@/composables/useLocale'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const { t } = useLocale()
const store = useScheduleOptimizerStore()
const { slots, recommendations, summary, loading } = storeToRefs(store)

const platformLabels: Record<string, string> = {
  youtube: 'YouTube',
  tiktok: 'TikTok',
  instagram: 'Instagram',
  naverclip: t('scheduleOptimizer.naverClip'),
}

onMounted(() => {
  store.fetchSlots('youtube')
  store.fetchRecommendations()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('scheduleOptimizer.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('scheduleOptimizer.description') }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <div v-else class="space-y-8">
      <!-- Summary Cards -->
      <div v-if="summary" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <ClockIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.bestTimeSlot') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.bestTimeSlot }}
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.appliedRecommendations') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.appliedCount }}{{ $t('scheduleOptimizer.caseUnit') }}
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <ChartBarIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.avgPerformanceBoost') }}</p>
              <p class="text-xl font-bold text-green-600 dark:text-green-400">
                +{{ summary.avgPerformanceBoost }}%
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-100 dark:bg-orange-900/30">
              <CalendarDaysIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.totalRecommendations') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.totalRecommendations }}{{ $t('scheduleOptimizer.caseUnit') }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Optimal Slots -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('scheduleOptimizer.optimalSlots') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ slots.length }})</span>
        </h2>

        <div v-if="slots.length > 0" class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="slot in slots"
            :key="slot.id"
            class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
          >
            <div class="mb-2 flex items-center justify-between">
              <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ slot.dayOfWeek }} {{ slot.timeSlot }}</span>
              <span class="rounded-full bg-primary-100 px-2 py-0.5 text-xs font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-400">
                {{ $t('scheduleOptimizer.score') }} {{ slot.score }}
              </span>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ platformLabels[slot.platform] ?? slot.platform }}</p>
            <div class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
              <div
                class="h-full rounded-full bg-primary-500 transition-all duration-300"
                :style="{ width: `${slot.score}%` }"
              />
            </div>
          </div>
        </div>
      </section>

      <!-- Recommendations -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('scheduleOptimizer.scheduleRecommendations') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ recommendations.length }})</span>
        </h2>

        <div v-if="recommendations.length > 0" class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="overflow-x-auto">
            <table class="w-full">
              <thead>
                <tr class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800">
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.content') }}</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.suggestedTime') }}</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.platform') }}</th>
                  <th class="px-4 py-3 text-center text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.confidence') }}</th>
                  <th class="px-4 py-3 text-center text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.status') }}</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
                <tr v-for="rec in recommendations" :key="rec.id" class="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                  <td class="px-4 py-3 text-sm text-gray-900 dark:text-gray-100">{{ rec.title }}</td>
                  <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-400">{{ rec.suggestedTime }}</td>
                  <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-400">{{ platformLabels[rec.platform] ?? rec.platform }}</td>
                  <td class="px-4 py-3 text-center">
                    <span
                      class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium"
                      :class="rec.confidence >= 80 ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' : rec.confidence >= 50 ? 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'"
                    >
                      {{ rec.confidence }}%
                    </span>
                  </td>
                  <td class="px-4 py-3 text-center">
                    <span
                      class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium"
                      :class="rec.applied ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'"
                    >
                      {{ rec.applied ? $t('scheduleOptimizer.applied') : $t('scheduleOptimizer.pending') }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ClockIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('scheduleOptimizer.noRecommendations') }}</h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('scheduleOptimizer.noRecommendationsDesc') }}</p>
        </div>
      </section>
    </div>
  </div>
</template>
