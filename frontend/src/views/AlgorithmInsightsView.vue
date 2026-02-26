<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ChartBarSquareIcon,
  TrophyIcon,
  ExclamationTriangleIcon,
  BellAlertIcon,
  ArrowPathIcon,
} from '@heroicons/vue/24/outline'
import { useAlgorithmInsightsStore } from '@/stores/algorithmInsights'
import AlgorithmScoreCard from '@/components/algorithminsights/AlgorithmScoreCard.vue'
import InsightFactorRow from '@/components/algorithminsights/InsightFactorRow.vue'
import AlgorithmChangeCard from '@/components/algorithminsights/AlgorithmChangeCard.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useAlgorithmInsightsStore()
const { insights, scores, changes, summary, isLoading } = storeToRefs(store)

const platformFilter = ref<string>('ALL')

const platformOptions = [
  { value: 'ALL', label: '전체' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
]

const filteredInsights = computed(() => {
  if (platformFilter.value === 'ALL') return insights.value
  return insights.value.filter((i) => i.platform === platformFilter.value)
})

const filteredChanges = computed(() => {
  if (platformFilter.value === 'ALL') return changes.value
  return changes.value.filter((c) => c.platform === platformFilter.value)
})

const handlePlatformFilter = (platform: string) => {
  platformFilter.value = platform
  if (platform === 'ALL') {
    store.fetchInsights()
  } else {
    store.fetchInsights(platform)
  }
}

onMounted(() => {
  store.fetchInsights()
  store.fetchScores()
  store.fetchChanges()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            플랫폼 알고리즘 인사이트
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          각 플랫폼 알고리즘의 주요 요소를 분석하고 최적화 전략을 확인하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChartBarSquareIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 점수</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgOverallScore }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <TrophyIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">최고 플랫폼</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.bestPlatform || '-' }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ExclamationTriangleIcon class="h-5 w-5 text-orange-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">최약 요소</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.worstFactor || '-' }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <BellAlertIcon class="h-5 w-5 text-red-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">최근 변경</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.recentChanges }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Platform Score Cards -->
      <div class="mb-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          플랫폼별 알고리즘 점수
        </h2>
        <div v-if="scores.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <AlgorithmScoreCard
            v-for="s in scores"
            :key="s.platform"
            :score="s"
          />
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ChartBarSquareIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">플랫폼 점수 데이터가 없습니다</p>
        </div>
      </div>

      <!-- Platform Filter -->
      <div class="mb-4 flex items-center gap-3">
        <ArrowPathIcon class="h-5 w-5 text-gray-400" />
        <div class="flex gap-2">
          <button
            v-for="opt in platformOptions"
            :key="opt.value"
            @click="handlePlatformFilter(opt.value)"
            :class="[
              'rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
              platformFilter === opt.value
                ? 'bg-blue-600 text-white dark:bg-blue-500'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700',
            ]"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <!-- Algorithm Factors -->
      <div class="mb-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          알고리즘 요소 분석
        </h2>
        <div v-if="filteredInsights.length > 0" class="space-y-3">
          <InsightFactorRow
            v-for="insight in filteredInsights"
            :key="insight.id"
            :insight="insight"
          />
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ChartBarSquareIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">알고리즘 요소 데이터가 없습니다</p>
        </div>
      </div>

      <!-- Recent Algorithm Changes -->
      <div>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          최근 알고리즘 변경
        </h2>
        <div v-if="filteredChanges.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <AlgorithmChangeCard
            v-for="change in filteredChanges"
            :key="change.id"
            :change="change"
          />
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <BellAlertIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">최근 알고리즘 변경 사항이 없습니다</p>
        </div>
      </div>
    </template>
  </div>
</template>
