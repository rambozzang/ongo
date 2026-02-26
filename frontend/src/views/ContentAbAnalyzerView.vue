<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  BeakerIcon,
  CheckCircleIcon,
  ChartBarIcon,
  ScaleIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import AbTestCard from '@/components/contentabanalyzer/AbTestCard.vue'
import VariantCompareBar from '@/components/contentabanalyzer/VariantCompareBar.vue'
import { useContentAbAnalyzerStore } from '@/stores/contentAbAnalyzer'
import type { ContentAbTest } from '@/types/contentAbAnalyzer'

const store = useContentAbAnalyzerStore()

const selectedTest = ref<ContentAbTest | null>(null)
const activeStatusFilter = ref<string>('ALL')

const statusFilters = [
  { key: 'ALL', label: '전체' },
  { key: 'RUNNING', label: '진행중' },
  { key: 'COMPLETED', label: '완료' },
  { key: 'PAUSED', label: '일시정지' },
]

onMounted(() => {
  store.fetchTests()
  store.fetchSummary()
})

/* ---- Summary stats ---- */
const totalTests = computed(() => store.summary?.totalTests ?? 0)
const completedCount = computed(() => store.summary?.completedTests ?? 0)
const avgConfidence = computed(() => store.summary?.avgConfidence ?? 0)
const aWinRate = computed(() => store.summary?.winRateA ?? 0)
const bWinRate = computed(() => store.summary?.winRateB ?? 0)

/* ---- Filtered list ---- */
const filteredTests = computed(() => {
  if (activeStatusFilter.value === 'ALL') return store.tests
  return store.tests.filter((t) => t.status === activeStatusFilter.value)
})

/* ---- Handlers ---- */
function handleSelectTest(id: number) {
  const test = store.tests.find((t) => t.id === id)
  if (test) {
    selectedTest.value = test
  }
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          콘텐츠 A/B 분석기
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          두 가지 콘텐츠 변형을 비교하여 최적의 전략을 찾으세요
        </p>
      </div>
    </div>

    <PageGuide
      title="콘텐츠 A/B 분석기 가이드"
      :items="[
        '두 가지 영상 변형(A/B)의 성과를 실시간으로 비교합니다',
        '신뢰도 90% 이상이면 통계적으로 유의미한 결과입니다',
        '조회수, 좋아요, 댓글, CTR, 시청시간 등 다양한 지표를 비교합니다',
        '테스트 완료 후 우승 변형을 기반으로 콘텐츠 전략을 수립하세요',
      ]"
    />

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else>
      <!-- Summary Cards -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
        <!-- Total Tests -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <BeakerIcon class="w-4 h-4 text-blue-600 dark:text-blue-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">총 테스트</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ totalTests }}건
          </p>
        </div>

        <!-- Completed -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="w-4 h-4 text-green-600 dark:text-green-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">완료</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ completedCount }}건
          </p>
        </div>

        <!-- Average Confidence -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <ChartBarIcon class="w-4 h-4 text-purple-600 dark:text-purple-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 신뢰도</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ avgConfidence.toFixed(1) }}%
          </p>
        </div>

        <!-- A/B Win Rate -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-orange-100 dark:bg-orange-900/30">
              <ScaleIcon class="w-4 h-4 text-orange-600 dark:text-orange-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">A/B 승률</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-sm font-bold text-blue-600 dark:text-blue-400">A {{ aWinRate.toFixed(1) }}%</span>
            <span class="text-gray-400 dark:text-gray-500">/</span>
            <span class="text-sm font-bold text-orange-600 dark:text-orange-400">B {{ bWinRate.toFixed(1) }}%</span>
          </div>
        </div>
      </div>

      <!-- Status Filter Tabs -->
      <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex space-x-6">
          <button
            v-for="filter in statusFilters"
            :key="filter.key"
            @click="activeStatusFilter = filter.key"
            :class="[
              activeStatusFilter === filter.key
                ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400',
              'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium',
            ]"
          >
            {{ filter.label }}
          </button>
        </nav>
      </div>

      <!-- Test Cards List -->
      <section class="mb-6">
        <div v-if="filteredTests.length > 0" class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-4">
          <AbTestCard
            v-for="test in filteredTests"
            :key="test.id"
            :test="test"
            @select="handleSelectTest"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
        >
          <BeakerIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
          <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
            테스트가 없습니다
          </h3>
          <p class="text-sm text-gray-600 dark:text-gray-400">
            A/B 테스트를 생성하여 콘텐츠 성과를 비교해 보세요
          </p>
        </div>
      </section>

      <!-- Selected Test Detail: Variant Compare -->
      <section v-if="selectedTest">
        <div class="mb-4">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            "{{ selectedTest.title }}" 상세 비교
          </h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
            두 변형의 주요 지표를 비교합니다
          </p>
        </div>
        <VariantCompareBar
          :variant-a="selectedTest.variantA"
          :variant-b="selectedTest.variantB"
        />
      </section>
    </template>
  </div>
</template>
