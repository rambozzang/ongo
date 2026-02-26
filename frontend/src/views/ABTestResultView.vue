<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  BeakerIcon,
  XMarkIcon,
  PauseIcon,
  PlayIcon,
  StopIcon,
  ChartBarIcon,
  CheckCircleIcon,
  ArrowTrendingUpIcon,
} from '@heroicons/vue/24/outline'
import { useABTestResultStore } from '@/stores/abTestResult'
import TestResultCard from '@/components/abtestresult/TestResultCard.vue'
import VariantCompare from '@/components/abtestresult/VariantCompare.vue'
import ConfidenceGauge from '@/components/abtestresult/ConfidenceGauge.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { ABTestResult } from '@/types/abTestResult'

const store = useABTestResultStore()
const { results, summary, isLoading } = storeToRefs(store)

// ─── Filter ──────────────────────────────────────
const activeFilter = ref<string>('ALL')
const filterTabs = [
  { value: 'ALL', label: '전체' },
  { value: 'RUNNING', label: '진행중' },
  { value: 'COMPLETED', label: '완료' },
  { value: 'PAUSED', label: '일시중지' },
  { value: 'CANCELLED', label: '취소' },
]

const filteredResults = computed(() => {
  if (activeFilter.value === 'ALL') return results.value
  return results.value.filter((r) => r.status === activeFilter.value)
})

const handleFilterChange = async (value: string) => {
  activeFilter.value = value
  await store.fetchResults(value === 'ALL' ? undefined : value)
}

// ─── Detail Modal ──────────────────────────────────
const showDetailModal = ref(false)
const selectedResult = ref<ABTestResult | null>(null)

const openDetail = (result: ABTestResult) => {
  selectedResult.value = result
  showDetailModal.value = true
}

const closeDetail = () => {
  showDetailModal.value = false
  selectedResult.value = null
}

const handlePause = async () => {
  if (!selectedResult.value) return
  await store.pauseTest(selectedResult.value.id)
  selectedResult.value = results.value.find((r) => r.id === selectedResult.value?.id) ?? null
}

const handleResume = async () => {
  if (!selectedResult.value) return
  await store.resumeTest(selectedResult.value.id)
  selectedResult.value = results.value.find((r) => r.id === selectedResult.value?.id) ?? null
}

const handleStop = async () => {
  if (!selectedResult.value) return
  await store.stopTest(selectedResult.value.id)
  selectedResult.value = results.value.find((r) => r.id === selectedResult.value?.id) ?? null
}

onMounted(() => {
  store.fetchResults()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">A/B 테스트 결과</h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          A/B 테스트 성과를 분석하고 최적의 변형을 확인하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-blue-100 p-2 dark:bg-blue-900/30">
            <BeakerIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">전체 테스트</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.totalTests.toLocaleString('ko-KR') }}</p>
          </div>
        </div>
      </div>

      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-green-100 p-2 dark:bg-green-900/30">
            <ChartBarIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">진행중</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.runningTests.toLocaleString('ko-KR') }}</p>
          </div>
        </div>
      </div>

      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-purple-100 p-2 dark:bg-purple-900/30">
            <CheckCircleIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">완료</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.completedTests.toLocaleString('ko-KR') }}</p>
          </div>
        </div>
      </div>

      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-orange-100 p-2 dark:bg-orange-900/30">
            <ArrowTrendingUpIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 개선율</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.avgImprovement }}%</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Filter Tabs -->
    <div class="mb-6 flex flex-wrap gap-2">
      <button
        v-for="tab in filterTabs"
        :key="tab.value"
        class="rounded-full px-4 py-1.5 text-sm font-medium transition-colors"
        :class="activeFilter === tab.value
          ? 'bg-blue-600 text-white'
          : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'"
        @click="handleFilterChange(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Results Grid -->
    <div v-else-if="filteredResults.length > 0" class="grid gap-4 desktop:grid-cols-2">
      <TestResultCard
        v-for="result in filteredResults"
        :key="result.id"
        :result="result"
        @click="openDetail"
      />
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
    >
      <BeakerIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">테스트 결과가 없습니다</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        A/B 테스트를 생성하고 결과를 확인해보세요
      </p>
    </div>

    <!-- Detail Modal -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showDetailModal && selectedResult"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          @click.self="closeDetail"
        >
          <div class="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
            <!-- Modal header -->
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ selectedResult.testName }}
              </h2>
              <button
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                @click="closeDetail"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <!-- Confidence Gauge -->
            <div class="mb-6 flex justify-center">
              <ConfidenceGauge :confidence="selectedResult.confidence" />
            </div>

            <!-- Variant Compare -->
            <div class="mb-6">
              <h3 class="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-300">변형 비교</h3>
              <VariantCompare :variants="selectedResult.variants" />
            </div>

            <!-- Action buttons -->
            <div class="flex items-center justify-end gap-3 border-t border-gray-200 pt-4 dark:border-gray-700">
              <button
                v-if="selectedResult.status === 'RUNNING'"
                class="inline-flex items-center gap-2 rounded-lg bg-yellow-100 px-4 py-2 text-sm font-medium text-yellow-700 transition-colors hover:bg-yellow-200 dark:bg-yellow-900/30 dark:text-yellow-300 dark:hover:bg-yellow-900/50"
                @click="handlePause"
              >
                <PauseIcon class="h-4 w-4" />
                일시중지
              </button>
              <button
                v-if="selectedResult.status === 'PAUSED'"
                class="inline-flex items-center gap-2 rounded-lg bg-green-100 px-4 py-2 text-sm font-medium text-green-700 transition-colors hover:bg-green-200 dark:bg-green-900/30 dark:text-green-300 dark:hover:bg-green-900/50"
                @click="handleResume"
              >
                <PlayIcon class="h-4 w-4" />
                재개
              </button>
              <button
                v-if="selectedResult.status === 'RUNNING' || selectedResult.status === 'PAUSED'"
                class="inline-flex items-center gap-2 rounded-lg bg-red-100 px-4 py-2 text-sm font-medium text-red-700 transition-colors hover:bg-red-200 dark:bg-red-900/30 dark:text-red-300 dark:hover:bg-red-900/50"
                @click="handleStop"
              >
                <StopIcon class="h-4 w-4" />
                중지
              </button>
              <button
                class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                @click="closeDetail"
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
