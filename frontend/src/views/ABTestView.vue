<script setup lang="ts">
import { ref, computed } from 'vue'
import { PlusIcon, Squares2X2Icon, ListBulletIcon } from '@heroicons/vue/24/outline'
import { useABTestStore } from '@/stores/abtest'
import ABTestCard from '@/components/abtest/ABTestCard.vue'
import ABTestCreateModal from '@/components/abtest/ABTestCreateModal.vue'
import ABTestResultsPanel from '@/components/abtest/ABTestResultsPanel.vue'
import SignificanceIndicator from '@/components/abtest/SignificanceIndicator.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import type { TestStatus, TestVariant } from '@/types/abtest'

const store = useABTestStore()

const showCreateModal = ref(false)
const showResultsPanel = ref(false)
const selectedTestId = ref<number | null>(null)
const viewMode = ref<'grid' | 'list'>('grid')

const selectedTest = computed(() => {
  if (selectedTestId.value === null) return null
  return store.tests.find(t => t.id === selectedTestId.value) || null
})

const handleCreateTest = (data: {
  name: string
  videoTitle: string
  videoId: string
  type: 'thumbnail' | 'title' | 'description'
  variants: TestVariant[]
  duration: number
  sampleSize: number
  confidence: number
  status: 'draft' | 'running'
}) => {
  const now = new Date().toISOString()
  store.createTest({
    name: data.name,
    videoTitle: data.videoTitle,
    videoId: data.videoId,
    type: data.type,
    status: data.status,
    variants: data.variants,
    startDate: data.status === 'running' ? now : now,
    endDate: null,
    duration: data.duration,
    sampleSize: data.sampleSize,
    confidence: data.confidence
  })
  showCreateModal.value = false
}

const handleStartTest = (id: number) => {
  store.startTest(id)
}

const handleStopTest = (id: number) => {
  store.stopTest(id)
}

const handleDeleteTest = (id: number) => {
  store.deleteTest(id)
}

const handleViewResults = (id: number) => {
  selectedTestId.value = id
  showResultsPanel.value = true
}

const handleApplyWinner = (_testId: number) => {
  alert('우승 변형이 비디오에 적용되었습니다!')
  showResultsPanel.value = false
}

const setFilter = (filter: TestStatus | 'all') => {
  store.setFilter(filter)
}

const filterButtons = [
  { value: 'all' as const, label: '전체' },
  { value: 'running' as const, label: '진행 중' },
  { value: 'completed' as const, label: '완료' },
  { value: 'draft' as const, label: '초안' }
]
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Header -->
      <div class="flex items-center justify-between mb-8">
        <div>
          <h1 class="text-3xl font-bold text-gray-900 dark:text-white">A/B 테스트</h1>
          <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
            썸네일, 제목, 설명을 테스트하여 최적의 성과를 찾아보세요
          </p>
        </div>
        <button
          @click="showCreateModal = true"
          class="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
        >
          <PlusIcon class="w-5 h-5" />
          새 테스트
        </button>
      </div>

      <PageGuide title="A/B 테스트" :items="[
        '새 테스트 버튼으로 썸네일 또는 제목의 A/B 테스트를 생성하고, 변형(Variant)을 설정하세요',
        '상태 필터(전체/진행중/완료/초안)와 그리드/리스트 뷰로 테스트를 관리하세요',
        '상단 통계에서 총 테스트 수·진행 중·완료 수를 확인하세요',
        '각 테스트 카드에서 샘플 크기·신뢰도·기간을 확인하고, 시작/중지/결과 보기 버튼으로 테스트를 관리하세요',
        '완료된 테스트의 통계적 유의성 지표를 확인하여 더 성과가 좋은 조합을 선택하세요',
      ]" />

      <!-- Stats bar -->
      <div class="grid grid-cols-3 gap-6 mb-8">
        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">진행 중</div>
              <div class="text-3xl font-bold text-gray-900 dark:text-white">{{ store.activeTests.length }}</div>
            </div>
            <div class="w-12 h-12 bg-green-100 dark:bg-green-900/30 rounded-lg flex items-center justify-center">
              <div class="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">완료</div>
              <div class="text-3xl font-bold text-gray-900 dark:text-white">{{ store.completedTests.length }}</div>
            </div>
            <div class="w-12 h-12 bg-blue-100 dark:bg-blue-900/30 rounded-lg flex items-center justify-center">
              <svg class="w-6 h-6 text-blue-600 dark:text-blue-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">평균 CTR 개선율</div>
              <div class="text-3xl font-bold text-green-600 dark:text-green-400">
                +{{ store.averageCTRImprovement.toFixed(1) }}%
              </div>
            </div>
            <div class="w-12 h-12 bg-green-100 dark:bg-green-900/30 rounded-lg flex items-center justify-center">
              <svg class="w-6 h-6 text-green-600 dark:text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
              </svg>
            </div>
          </div>
        </div>
      </div>

      <!-- Filters and view toggle -->
      <div class="flex items-center justify-between mb-6">
        <div class="flex items-center gap-2">
          <button
            v-for="filter in filterButtons"
            :key="filter.value"
            @click="setFilter(filter.value)"
            :class="[
              'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
              store.activeFilter === filter.value
                ? 'bg-blue-600 text-white'
                : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-gray-600 hover:border-blue-300 dark:hover:border-blue-500'
            ]"
          >
            {{ filter.label }}
          </button>
        </div>

        <div class="flex items-center gap-2">
          <button
            @click="viewMode = 'grid'"
            :class="[
              'p-2 rounded-lg transition-colors',
              viewMode === 'grid'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
            ]"
          >
            <Squares2X2Icon class="w-5 h-5" />
          </button>
          <button
            @click="viewMode = 'list'"
            :class="[
              'p-2 rounded-lg transition-colors',
              viewMode === 'list'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
            ]"
          >
            <ListBulletIcon class="w-5 h-5" />
          </button>
        </div>
      </div>

      <!-- Tests grid/list -->
      <div v-if="store.filteredTests.length > 0" :class="viewMode === 'grid' ? 'grid grid-cols-1 xl:grid-cols-2 gap-6' : 'space-y-4'">
        <ABTestCard
          v-for="test in store.filteredTests"
          :key="test.id"
          :test="test"
          @start="handleStartTest"
          @stop="handleStopTest"
          @delete="handleDeleteTest"
          @view-results="handleViewResults"
        />
      </div>

      <!-- Empty state -->
      <div v-else class="text-center py-12">
        <div class="inline-flex items-center justify-center w-16 h-16 bg-gray-100 dark:bg-gray-700 rounded-full mb-4">
          <svg class="w-8 h-8 text-gray-400 dark:text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
        </div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-2">
          {{ store.activeFilter === 'all' ? '테스트가 없습니다' : `${filterButtons.find(f => f.value === store.activeFilter)?.label} 테스트가 없습니다` }}
        </h3>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
          새로운 A/B 테스트를 생성하여 비디오 성과를 최적화하세요
        </p>
        <button
          @click="showCreateModal = true"
          class="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
        >
          <PlusIcon class="w-5 h-5" />
          첫 테스트 생성
        </button>
      </div>
    </div>

    <!-- Modals -->
    <ABTestCreateModal
      v-if="showCreateModal"
      @close="showCreateModal = false"
      @create="handleCreateTest"
    />

    <ABTestResultsPanel
      v-if="showResultsPanel"
      :test="selectedTest"
      @close="showResultsPanel = false"
      @apply-winner="handleApplyWinner"
    />

    <!-- Statistics Detail Panel -->
    <div v-if="selectedTestId !== null && showResultsPanel" class="mt-6 px-4 sm:px-6 lg:px-8">
      <SignificanceIndicator :test-id="selectedTestId" />
    </div>
  </div>
</template>
