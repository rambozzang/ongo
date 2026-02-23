<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusIcon, Squares2X2Icon, ListBulletIcon } from '@heroicons/vue/24/outline'
import { useABTestStore } from '@/stores/abtest'
import ABTestCard from '@/components/abtest/ABTestCard.vue'
import ABTestCreateModal from '@/components/abtest/ABTestCreateModal.vue'
import ABTestResultsPanel from '@/components/abtest/ABTestResultsPanel.vue'
import SignificanceIndicator from '@/components/abtest/SignificanceIndicator.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import type { TestStatus, TestVariant } from '@/types/abtest'

const { t } = useI18n()
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
  alert(t('abTest.winnerApplied'))
  showResultsPanel.value = false
}

const setFilter = (filter: TestStatus | 'all') => {
  store.setFilter(filter)
}

const filterButtons = [
  { value: 'all' as const, label: t('abTest.filterAll') },
  { value: 'running' as const, label: t('abTest.filterRunning') },
  { value: 'completed' as const, label: t('abTest.filterCompleted') },
  { value: 'draft' as const, label: t('abTest.filterDraft') }
]
</script>

<template>
  <div class="relative">
      <!-- Header -->
      <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('abTest.title') }}</h1>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('abTest.description') }}
          </p>
        </div>
        <div class="flex items-center gap-3">
          <button
            @click="showCreateModal = true"
            class="btn-primary inline-flex items-center gap-2"
          >
            <PlusIcon class="w-5 h-5" />
            {{ $t('abTest.newTest') }}
          </button>
        </div>
      </div>

      <PageGuide :title="$t('abTest.pageGuideTitle')" :items="($tm('abTest.pageGuide') as string[])" />

      <!-- Stats bar -->
      <div class="grid grid-cols-3 gap-6 mb-8">
        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">{{ $t('abTest.running') }}</div>
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
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">{{ $t('abTest.completedStat') }}</div>
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
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">{{ $t('abTest.avgCtrImprovement') }}</div>
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
        <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
          {{ store.activeFilter === 'all' ? $t('abTest.emptyTitle') : `${filterButtons.find(f => f.value === store.activeFilter)?.label} ${$t('abTest.emptyTitleSuffix')}` }}
        </h3>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
          {{ $t('abTest.emptyDesc') }}
        </p>
        <button
          @click="showCreateModal = true"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          {{ $t('abTest.createFirstTest') }}
        </button>
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
    <div v-if="selectedTestId !== null && showResultsPanel" class="mt-6">
      <SignificanceIndicator :test-id="selectedTestId" />
    </div>
  </div>
</template>
