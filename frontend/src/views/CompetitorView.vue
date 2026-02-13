<script setup lang="ts">
import { ref, computed } from 'vue'
import { PlusIcon, ArrowPathIcon, UsersIcon, ChartBarIcon, TrophyIcon, ArrowTrendingUpIcon } from '@heroicons/vue/24/outline'
import { useCompetitorStore } from '@/stores/competitor'
import CompetitorCard from '@/components/competitor/CompetitorCard.vue'
import ComparisonChart from '@/components/competitor/ComparisonChart.vue'
import AddCompetitorModal from '@/components/competitor/AddCompetitorModal.vue'
import TrendingVideoList from '@/components/competitor/TrendingVideoList.vue'

type Tab = 'list' | 'comparison' | 'trending'

const competitorStore = useCompetitorStore()
const activeTab = ref<Tab>('list')
const isAddModalOpen = ref(false)
const selectedCompetitorId = ref<number | null>(null)
const isRefreshing = ref(false)

const selectedCompetitor = computed(() => {
  if (!selectedCompetitorId.value) return null
  return competitorStore.competitors.find(c => c.id === selectedCompetitorId.value)
})

const comparisonData = computed(() => {
  if (!selectedCompetitorId.value) return []
  return competitorStore.getComparison(selectedCompetitorId.value)
})

function handleToggleTracking(id: number) {
  competitorStore.toggleTracking(id)
}

function handleRemoveCompetitor(id: number) {
  if (confirm('정말 이 경쟁 채널을 삭제하시겠습니까?')) {
    competitorStore.removeCompetitor(id)
    if (selectedCompetitorId.value === id) {
      selectedCompetitorId.value = null
    }
  }
}

function handleSelectCompetitor(id: number) {
  selectedCompetitorId.value = id
  activeTab.value = 'comparison'
}

function handleAddCompetitor(data: Parameters<typeof competitorStore.addCompetitor>[0]) {
  competitorStore.addCompetitor(data)
}

async function handleRefresh() {
  isRefreshing.value = true
  try {
    await competitorStore.refreshData()
  } finally {
    isRefreshing.value = false
  }
}

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`
  }
  return num.toString()
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Header -->
      <div class="mb-8">
        <div class="flex items-center justify-between mb-6">
          <h1 class="text-3xl font-bold text-gray-900 dark:text-white">
            경쟁사 분석
          </h1>
          <div class="flex items-center space-x-2">
            <button
              @click="handleRefresh"
              :disabled="isRefreshing"
              class="flex items-center space-x-2 px-4 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors"
            >
              <ArrowPathIcon
                :class="['w-5 h-5', isRefreshing && 'animate-spin']"
              />
              <span>새로고침</span>
            </button>
            <button
              @click="isAddModalOpen = true"
              class="flex items-center space-x-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
            >
              <PlusIcon class="w-5 h-5" />
              <span>채널 추가</span>
            </button>
          </div>
        </div>

        <!-- Overview Cards -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <!-- Tracked channels -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <UsersIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
                <span class="text-sm text-gray-600 dark:text-gray-400">추적 중인 채널</span>
              </div>
            </div>
            <p class="text-2xl font-bold text-gray-900 dark:text-white">
              {{ competitorStore.trackedCompetitors.length }}
            </p>
          </div>

          <!-- Average subscribers -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <ChartBarIcon class="w-5 h-5 text-green-600 dark:text-green-400" />
                <span class="text-sm text-gray-600 dark:text-gray-400">평균 구독자</span>
              </div>
            </div>
            <p class="text-2xl font-bold text-gray-900 dark:text-white">
              {{ formatNumber(competitorStore.averageMetrics.avgSubscribers) }}
            </p>
          </div>

          <!-- My ranking -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <TrophyIcon class="w-5 h-5 text-yellow-600 dark:text-yellow-400" />
                <span class="text-sm text-gray-600 dark:text-gray-400">내 순위</span>
              </div>
            </div>
            <p class="text-2xl font-bold text-gray-900 dark:text-white">
              #{{ competitorStore.myRanking }}
            </p>
          </div>

          <!-- Growth rate comparison -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <ArrowTrendingUpIcon class="w-5 h-5 text-purple-600 dark:text-purple-400" />
                <span class="text-sm text-gray-600 dark:text-gray-400">평균 성장률</span>
              </div>
            </div>
            <p class="text-2xl font-bold text-gray-900 dark:text-white">
              {{ competitorStore.averageMetrics.avgGrowthRate }}%
            </p>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <div class="mb-6">
        <div class="border-b border-gray-200 dark:border-gray-700">
          <nav class="-mb-px flex space-x-8">
            <button
              @click="activeTab = 'list'"
              :class="[
                'py-2 px-1 border-b-2 font-medium text-sm transition-colors',
                activeTab === 'list'
                  ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                  : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
              ]"
            >
              채널 목록
            </button>
            <button
              @click="activeTab = 'comparison'"
              :class="[
                'py-2 px-1 border-b-2 font-medium text-sm transition-colors',
                activeTab === 'comparison'
                  ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                  : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
              ]"
            >
              비교 분석
            </button>
            <button
              @click="activeTab = 'trending'"
              :class="[
                'py-2 px-1 border-b-2 font-medium text-sm transition-colors',
                activeTab === 'trending'
                  ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                  : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
              ]"
            >
              트렌드
            </button>
          </nav>
        </div>
      </div>

      <!-- Tab Content -->
      <div>
        <!-- Channel List Tab -->
        <div v-if="activeTab === 'list'">
          <div
            v-if="competitorStore.competitors.length === 0"
            class="text-center py-12 text-gray-500 dark:text-gray-400"
          >
            <p class="mb-4">추가된 경쟁 채널이 없습니다</p>
            <button
              @click="isAddModalOpen = true"
              class="inline-flex items-center space-x-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
            >
              <PlusIcon class="w-5 h-5" />
              <span>채널 추가하기</span>
            </button>
          </div>
          <div
            v-else
            class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
          >
            <CompetitorCard
              v-for="competitor in competitorStore.competitors"
              :key="competitor.id"
              :competitor="competitor"
              :selected="selectedCompetitorId === competitor.id"
              @toggle-tracking="handleToggleTracking"
              @remove="handleRemoveCompetitor"
              @select="handleSelectCompetitor"
            />
          </div>
        </div>

        <!-- Comparison Tab -->
        <div v-if="activeTab === 'comparison'">
          <div v-if="!selectedCompetitorId" class="text-center py-12">
            <p class="text-gray-500 dark:text-gray-400 mb-4">
              비교할 채널을 선택하세요
            </p>
            <button
              @click="activeTab = 'list'"
              class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
            >
              채널 목록으로 이동
            </button>
          </div>
          <div v-else>
            <!-- Competitor selector -->
            <div class="mb-6">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                비교 대상 채널
              </label>
              <select
                v-model="selectedCompetitorId"
                class="w-full sm:w-auto px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option
                  v-for="competitor in competitorStore.competitors"
                  :key="competitor.id"
                  :value="competitor.id"
                >
                  {{ competitor.name }}
                </option>
              </select>
            </div>

            <!-- Comparison chart -->
            <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
              <h2 class="text-xl font-semibold text-gray-900 dark:text-white mb-6">
                내 채널 vs {{ selectedCompetitor?.name }}
              </h2>
              <ComparisonChart
                :comparisons="comparisonData"
                my-name="내 채널"
                :competitor-name="selectedCompetitor?.name"
              />
            </div>
          </div>
        </div>

        <!-- Trending Tab -->
        <div v-if="activeTab === 'trending'">
          <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
            <h2 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">
              인기 영상
            </h2>
            <TrendingVideoList
              :videos="competitorStore.competitorVideos"
              :competitors="competitorStore.competitors"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Add Competitor Modal -->
    <AddCompetitorModal
      :is-open="isAddModalOpen"
      @close="isAddModalOpen = false"
      @add="handleAddCompetitor"
    />
  </div>
</template>
