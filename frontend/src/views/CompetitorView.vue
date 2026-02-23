<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusIcon, ArrowPathIcon, UsersIcon, ChartBarIcon, TrophyIcon, ArrowTrendingUpIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import { useCompetitorStore } from '@/stores/competitor'
import CompetitorCard from '@/components/competitor/CompetitorCard.vue'
import ComparisonChart from '@/components/competitor/ComparisonChart.vue'
import AddCompetitorModal from '@/components/competitor/AddCompetitorModal.vue'
import TrendingVideoList from '@/components/competitor/TrendingVideoList.vue'
import PageGuide from '@/components/common/PageGuide.vue'

type Tab = 'list' | 'comparison' | 'trending'

const { t } = useI18n()
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
  if (confirm(t('competitor.confirmDelete'))) {
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

onMounted(() => {
  competitorStore.fetchCompetitors()
})

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
  <div class="relative">
      <!-- Header -->
      <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ $t('competitor.title') }}
          </h1>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('competitor.description') }}
          </p>
        </div>
        <div class="flex items-center gap-3">
          <button
            @click="handleRefresh"
            :disabled="isRefreshing"
            class="inline-flex items-center gap-2 px-4 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors"
          >
            <ArrowPathIcon
              :class="['w-5 h-5', isRefreshing && 'animate-spin']"
            />
            <span>{{ $t('competitor.refresh') }}</span>
          </button>
          <button
            @click="isAddModalOpen = true"
            class="btn-primary inline-flex items-center gap-2"
          >
            <PlusIcon class="w-5 h-5" />
            <span>{{ $t('competitor.addChannel') }}</span>
          </button>
        </div>
      </div>

      <PageGuide :title="$t('competitor.pageGuideTitle')" :items="($tm('competitor.pageGuide') as string[])" />

        <!-- Overview Cards -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <!-- Tracked channels -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <UsersIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
                <span class="text-sm text-gray-600 dark:text-gray-400">{{ $t('competitor.trackedChannels') }}</span>
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
                <span class="text-sm text-gray-600 dark:text-gray-400">{{ $t('competitor.avgSubscribers') }}</span>
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
                <span class="text-sm text-gray-600 dark:text-gray-400">{{ $t('competitor.myRanking') }}</span>
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
                <span class="text-sm text-gray-600 dark:text-gray-400">{{ $t('competitor.avgGrowthRate') }}</span>
              </div>
            </div>
            <p class="text-2xl font-bold text-gray-900 dark:text-white">
              {{ competitorStore.averageMetrics.avgGrowthRate }}%
            </p>
          </div>
        </div>

      <!-- Tabs -->
      <div class="mb-6 mt-8">
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
              {{ $t('competitor.tabList') }}
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
              {{ $t('competitor.tabComparison') }}
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
              {{ $t('competitor.tabTrending') }}
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
            <p class="mb-4">{{ $t('competitor.emptyList') }}</p>
            <button
              @click="isAddModalOpen = true"
              class="btn-primary inline-flex items-center gap-2"
            >
              <PlusIcon class="w-5 h-5" />
              <span>{{ $t('competitor.addChannelAction') }}</span>
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
              {{ $t('competitor.selectChannel') }}
            </p>
            <button
              @click="activeTab = 'list'"
              class="btn-primary"
            >
              {{ $t('competitor.goToList') }}
            </button>
          </div>
          <div v-else>
            <!-- Competitor selector -->
            <div class="mb-6">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                {{ $t('competitor.comparisonTarget') }}
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
              <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-6">
                {{ $t('competitor.myChannelVs', { name: selectedCompetitor?.name }) }}
              </h2>
              <ComparisonChart
                :comparisons="comparisonData"
                :my-name="$t('competitor.myChannel')"
                :competitor-name="selectedCompetitor?.name"
              />
            </div>
          </div>
        </div>

        <!-- Trending Tab -->
        <div v-if="activeTab === 'trending'">
          <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
            <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-4">
              {{ $t('competitor.trendingVideos') }}
            </h2>
            <TrendingVideoList
              :videos="competitorStore.competitorVideos"
              :competitors="competitorStore.competitors"
            />
          </div>
        </div>
      </div>

      <!-- AI Insight Section -->
      <div v-if="competitorStore.competitors.length > 0" class="mt-8">
        <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
              <SparklesIcon class="w-5 h-5 text-purple-600" />
              {{ $t('competitor.aiInsightTitle') }}
            </h2>
            <button
              @click="competitorStore.fetchInsight()"
              :disabled="competitorStore.insightLoading"
              class="btn-primary text-sm"
            >
              {{ competitorStore.insightLoading ? $t('competitor.aiAnalyzing') : $t('competitor.aiAnalyzeButton') }}
            </button>
          </div>

          <div v-if="competitorStore.aiInsight" class="space-y-4">
            <p class="text-gray-700 dark:text-gray-300">{{ competitorStore.aiInsight.summary }}</p>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="bg-green-50 dark:bg-green-900/20 rounded-lg p-4">
                <h3 class="font-medium text-green-800 dark:text-green-300 mb-2">{{ $t('competitor.strengths') }}</h3>
                <ul class="space-y-1 text-sm text-green-700 dark:text-green-400">
                  <li v-for="(s, i) in competitorStore.aiInsight.strengths" :key="i">- {{ s }}</li>
                </ul>
              </div>
              <div class="bg-red-50 dark:bg-red-900/20 rounded-lg p-4">
                <h3 class="font-medium text-red-800 dark:text-red-300 mb-2">{{ $t('competitor.weaknesses') }}</h3>
                <ul class="space-y-1 text-sm text-red-700 dark:text-red-400">
                  <li v-for="(w, i) in competitorStore.aiInsight.weaknesses" :key="i">- {{ w }}</li>
                </ul>
              </div>
              <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
                <h3 class="font-medium text-blue-800 dark:text-blue-300 mb-2">{{ $t('competitor.opportunities') }}</h3>
                <ul class="space-y-1 text-sm text-blue-700 dark:text-blue-400">
                  <li v-for="(o, i) in competitorStore.aiInsight.opportunities" :key="i">- {{ o }}</li>
                </ul>
              </div>
              <div class="bg-purple-50 dark:bg-purple-900/20 rounded-lg p-4">
                <h3 class="font-medium text-purple-800 dark:text-purple-300 mb-2">{{ $t('competitor.recommendations') }}</h3>
                <ul class="space-y-1 text-sm text-purple-700 dark:text-purple-400">
                  <li v-for="(r, i) in competitorStore.aiInsight.recommendations" :key="i">- {{ r }}</li>
                </ul>
              </div>
            </div>
          </div>

          <div v-else class="text-center py-8 text-gray-500 dark:text-gray-400">
            <SparklesIcon class="w-8 h-8 mx-auto mb-2 text-gray-400" />
            <p>{{ $t('competitor.aiInsightEmpty') }}</p>
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
