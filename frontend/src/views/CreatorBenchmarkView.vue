<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ChartBarSquareIcon,
  ArrowTrendingUpIcon,
  TrophyIcon,
  ExclamationTriangleIcon,
  ArrowPathIcon,
  UserGroupIcon,
} from '@heroicons/vue/24/outline'
import { useCreatorBenchmarkStore } from '@/stores/creatorBenchmark'
import BenchmarkMetricCard from '@/components/creatorbenchmark/BenchmarkMetricCard.vue'
import PeerComparisonRow from '@/components/creatorbenchmark/PeerComparisonRow.vue'
import PercentileChart from '@/components/creatorbenchmark/PercentileChart.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useCreatorBenchmarkStore()
const { results, peers, summary, isLoading } = storeToRefs(store)

const platformFilter = ref<string>('ALL')

const platformOptions = [
  { value: 'ALL', label: '전체' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
]

const filteredResults = computed(() => {
  if (platformFilter.value === 'ALL') return results.value
  return results.value.filter((r) => r.platform === platformFilter.value)
})

const filteredPeers = computed(() => {
  if (platformFilter.value === 'ALL') return peers.value
  return peers.value.filter((p) => p.platform === platformFilter.value)
})

const percentileItems = computed(() => {
  return filteredResults.value.map((r) => {
    const metricLabels: Record<string, string> = {
      SUBSCRIBERS: '구독자 수',
      AVG_VIEWS: '평균 조회수',
      ENGAGEMENT_RATE: '참여율',
      WATCH_TIME: '시청 시간',
      LIKES: '좋아요',
      COMMENTS: '댓글',
    }
    const formatValue = (value: number): string => {
      if (r.metric === 'ENGAGEMENT_RATE') return `${value}%`
      if (value >= 10000) return `${(value / 10000).toFixed(1)}만`
      if (value >= 1000) return `${(value / 1000).toFixed(1)}K`
      return value.toLocaleString()
    }
    return {
      label: metricLabels[r.metric] || r.metric,
      percentile: r.percentile,
      myValue: formatValue(r.myValue),
    }
  })
})

const handlePlatformFilter = (platform: string) => {
  platformFilter.value = platform
  if (platform === 'ALL') {
    store.fetchResults()
    store.fetchPeers()
  } else {
    store.fetchResults(platform)
    store.fetchPeers(platform)
  }
}

onMounted(() => {
  store.fetchResults()
  store.fetchPeers()
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
            크리에이터 벤치마크
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          동일 카테고리 크리에이터 대비 내 채널의 위치를 확인하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChartBarSquareIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">분석 지표</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalMetrics }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ArrowTrendingUpIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 이상</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.aboveAvgCount }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <TrophyIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">최고 백분위</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          상위 {{ 100 - summary.topPercentile }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ExclamationTriangleIcon class="h-5 w-5 text-orange-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">약점 지표</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.weakestMetric || '-' }}
        </p>
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

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Benchmark Metric Cards -->
      <div class="mb-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          벤치마크 지표
        </h2>
        <div v-if="filteredResults.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <BenchmarkMetricCard
            v-for="r in filteredResults"
            :key="r.id"
            :result="r"
          />
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ChartBarSquareIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">벤치마크 데이터가 없습니다</p>
        </div>
      </div>

      <!-- Percentile Chart -->
      <div class="mb-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          백분위 분포
        </h2>
        <div v-if="percentileItems.length > 0" class="space-y-3">
          <PercentileChart
            v-for="item in percentileItems"
            :key="item.label"
            :label="item.label"
            :percentile="item.percentile"
            :my-value="item.myValue"
          />
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ChartBarSquareIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">백분위 데이터가 없습니다</p>
        </div>
      </div>

      <!-- Peer Comparison Table -->
      <div>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          피어 비교
        </h2>
        <div v-if="filteredPeers.length > 0" class="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <table class="w-full min-w-[600px]">
            <thead>
              <tr class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900">
                <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  크리에이터
                </th>
                <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  플랫폼
                </th>
                <th class="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  구독자
                </th>
                <th class="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  평균 조회수
                </th>
                <th class="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  참여율
                </th>
              </tr>
            </thead>
            <tbody>
              <PeerComparisonRow
                v-for="p in filteredPeers"
                :key="p.id"
                :peer="p"
              />
            </tbody>
          </table>
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <UserGroupIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">피어 비교 데이터가 없습니다</p>
        </div>
      </div>
    </template>
  </div>
</template>
