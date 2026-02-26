<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  HeartIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
} from '@heroicons/vue/24/outline'
import { usePlatformHealthStore } from '@/stores/platformHealth'
import PlatformHealthCard from '@/components/platformhealth/PlatformHealthCard.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { HealthIssue } from '@/types/platformHealth'

const { t } = useI18n({ useScope: 'global' })
const store = usePlatformHealthStore()
const { scores, summary, isLoading } = storeToRefs(store)

const issuesMap = ref<Record<number, HealthIssue[]>>({})

const handleExpand = async (healthScoreId: number) => {
  if (!issuesMap.value[healthScoreId]) {
    await store.fetchIssues(healthScoreId)
    issuesMap.value[healthScoreId] = [...store.issues]
  }
}

const getIssues = (healthScoreId: number): HealthIssue[] => {
  return issuesMap.value[healthScoreId] ?? []
}

const trendLabel = (trend: string): string => {
  if (trend === 'UP') return t('platformHealth.trendUp')
  if (trend === 'DOWN') return t('platformHealth.trendDown')
  return t('platformHealth.trendStable')
}

const trendColor = (trend: string): string => {
  if (trend === 'UP') return 'text-green-600 dark:text-green-400'
  if (trend === 'DOWN') return 'text-red-600 dark:text-red-400'
  return 'text-gray-600 dark:text-gray-400'
}

onMounted(() => {
  store.fetchScores()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('platformHealth.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('platformHealth.description') }}
        </p>
      </div>
    </div>

    <PageGuide
      :title="$t('platformHealth.pageGuideTitle')"
      :items="($tm('platformHealth.pageGuide') as string[])"
    />

    <!-- Summary Cards -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-5">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformHealth.summaryTotalPlatforms') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalPlatforms }}
        </p>
      </div>
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformHealth.summaryAvgScore') }}</p>
        <p
          class="mt-1 text-2xl font-bold"
          :class="summary.avgHealthScore >= 70 ? 'text-green-600 dark:text-green-400' : summary.avgHealthScore >= 50 ? 'text-yellow-600 dark:text-yellow-400' : 'text-red-600 dark:text-red-400'"
        >
          {{ summary.avgHealthScore }}
        </p>
      </div>
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformHealth.summaryHealthiest') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.healthiestPlatform }}
        </p>
      </div>
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformHealth.summaryCriticalIssues') }}</p>
        <p
          class="mt-1 text-2xl font-bold"
          :class="summary.criticalIssues > 0 ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'"
        >
          {{ summary.criticalIssues }}
        </p>
      </div>
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformHealth.summaryOverallTrend') }}</p>
        <p class="mt-1 text-2xl font-bold" :class="trendColor(summary.overallTrend)">
          {{ trendLabel(summary.overallTrend) }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Platform Health Cards -->
    <div v-else-if="scores.length > 0" class="grid gap-4 tablet:grid-cols-2">
      <PlatformHealthCard
        v-for="s in scores"
        :key="s.id"
        :score="s"
        :issues="getIssues(s.id)"
        @expand="handleExpand"
      />
    </div>

    <!-- Empty state -->
    <div
      v-else
      class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <HeartIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('platformHealth.noScores') }}
      </h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        {{ $t('platformHealth.noScoresHint') }}
      </p>
    </div>
  </div>
</template>
