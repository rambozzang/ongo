<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  PlusIcon,
  ChartBarSquareIcon,
  MagnifyingGlassIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import CompetitorCard from '@/components/competitorAnalysis/CompetitorCard.vue'
import ContentGapTable from '@/components/competitorAnalysis/ContentGapTable.vue'
import TrendingTopicCard from '@/components/competitorAnalysis/TrendingTopicCard.vue'
import { useCompetitorAnalysisStore } from '@/stores/competitorAnalysis'
import type { AnalysisPeriod, MetricTrend, PlatformType } from '@/types/competitorAnalysis'

const { t } = useI18n({ useScope: 'global' })
const store = useCompetitorAnalysisStore()
const isTablet = useMediaQuery('(min-width: 768px)')

const {
  comparison,
  contentGaps,
  trendingTopics,
  selectedPeriod,
  activeTab,
  processing,
} = storeToRefs(store)

// --- Tabs ---
const tabs = computed(() => [
  { key: 'overview' as const, label: t('competitorAnalysis.tabs.overview'), icon: ChartBarSquareIcon },
  { key: 'gaps' as const, label: t('competitorAnalysis.tabs.gaps'), icon: MagnifyingGlassIcon },
  { key: 'trends' as const, label: t('competitorAnalysis.tabs.trends'), icon: ArrowTrendingUpIcon },
])

// --- Period Selector ---
const periods = computed<{ value: AnalysisPeriod; label: string }[]>(() => [
  { value: '7d', label: t('competitorAnalysis.period.7d') },
  { value: '30d', label: t('competitorAnalysis.period.30d') },
  { value: '90d', label: t('competitorAnalysis.period.90d') },
  { value: '1y', label: t('competitorAnalysis.period.1y') },
])

function handlePeriodChange(period: AnalysisPeriod) {
  store.setPeriod(period)
  store.fetchComparison()
}

// --- Add Competitor Modal ---
const showAddModal = ref(false)
const newCompetitorName = ref('')
const newPlatformUrls = ref<{ platform: PlatformType; url: string }[]>([
  { platform: 'YOUTUBE', url: '' },
])

function addPlatformRow() {
  newPlatformUrls.value.push({ platform: 'YOUTUBE', url: '' })
}

function removePlatformRow(index: number) {
  newPlatformUrls.value.splice(index, 1)
}

async function handleAddCompetitor() {
  if (!newCompetitorName.value.trim()) return
  const validUrls = newPlatformUrls.value.filter((p) => p.url.trim())
  if (validUrls.length === 0) return

  await store.addCompetitor(newCompetitorName.value.trim(), validUrls)
  showAddModal.value = false
  newCompetitorName.value = ''
  newPlatformUrls.value = [{ platform: 'YOUTUBE', url: '' }]
  store.fetchComparison()
}

function handleCloseModal() {
  showAddModal.value = false
  newCompetitorName.value = ''
  newPlatformUrls.value = [{ platform: 'YOUTUBE', url: '' }]
}

// --- Remove Competitor ---
async function handleRemoveCompetitor(competitorId: number) {
  await store.removeCompetitor(competitorId)
  store.fetchComparison()
}

// --- Overview: My Metrics trend helpers ---
function trendIcon(trend: MetricTrend) {
  if (trend === 'UP') return ArrowTrendingUpIcon
  if (trend === 'DOWN') return ArrowTrendingDownIcon
  return MinusIcon
}

function trendColor(trend: MetricTrend): string {
  if (trend === 'UP') return 'text-green-600 dark:text-green-400'
  if (trend === 'DOWN') return 'text-red-600 dark:text-red-400'
  return 'text-gray-400 dark:text-gray-500'
}

function formatNumber(num: number): string {
  if (num >= 1_000_000) return `${(num / 1_000_000).toFixed(1)}M`
  if (num >= 1_000) return `${(num / 1_000).toFixed(1)}K`
  return num.toLocaleString()
}

// --- Content Gaps ---
function handleCreateContent(_gapId: number) {
  // Navigate to content creation or open modal (placeholder)
}

// --- Platform options for add modal ---
const platformOptions: { value: PlatformType; label: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER', label: 'Naver' },
]

// --- Lifecycle ---
onMounted(async () => {
  await Promise.all([
    store.fetchCompetitors(),
    store.fetchComparison(),
    store.fetchContentGaps(),
    store.fetchTrendingTopics(),
  ])
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('competitorAnalysis.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('competitorAnalysis.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <!-- Period selector -->
        <div class="flex gap-1">
          <button
            v-for="p in periods"
            :key="p.value"
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="
              selectedPeriod === p.value
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                : 'text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700'
            "
            @click="handlePeriodChange(p.value)"
          >
            {{ p.label }}
          </button>
        </div>
        <!-- Add competitor button -->
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="showAddModal = true"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('competitorAnalysis.addCompetitor') }}
        </button>
      </div>
    </div>

    <PageGuide
      :title="$t('competitorAnalysis.pageGuideTitle')"
      :items="($tm('competitorAnalysis.pageGuide') as string[])"
    />

    <!-- Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav :class="isTablet ? '-mb-px flex gap-6' : '-mb-px flex'">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            isTablet
              ? 'inline-flex items-center gap-2 border-b-2 px-1 py-3 text-sm font-medium transition-colors'
              : 'flex-1 border-b-2 px-1 py-3 text-center text-xs font-medium transition-colors',
            activeTab === tab.key
              ? 'border-primary-500 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
          ]"
          @click="store.setActiveTab(tab.key)"
        >
          <component :is="tab.icon" :class="isTablet ? 'h-5 w-5' : 'mx-auto mb-1 h-5 w-5'" />
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Loading state -->
    <div v-if="processing" class="flex items-center justify-center py-12">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-primary-300 border-t-primary-600" />
    </div>

    <!-- Overview Tab -->
    <div v-else-if="activeTab === 'overview'" class="space-y-6">
      <!-- My metrics card -->
      <div
        v-if="comparison"
        class="rounded-lg border-2 border-primary-200 bg-primary-50/50 p-5 dark:border-primary-800 dark:bg-primary-900/10"
      >
        <h3 class="mb-3 text-sm font-semibold text-primary-700 dark:text-primary-400">
          {{ $t('competitorAnalysis.myMetrics') }}
        </h3>
        <div class="grid grid-cols-2 gap-4 tablet:grid-cols-4">
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('competitorAnalysis.views') }}</p>
            <div class="mt-1 flex items-center gap-1.5">
              <span class="text-lg font-bold text-gray-900 dark:text-white">
                {{ formatNumber(comparison.myMetrics.views) }}
              </span>
              <component
                :is="trendIcon(comparison.myMetrics.viewsTrend)"
                :class="['h-4 w-4', trendColor(comparison.myMetrics.viewsTrend)]"
              />
            </div>
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('competitorAnalysis.subscribers') }}</p>
            <div class="mt-1 flex items-center gap-1.5">
              <span class="text-lg font-bold text-gray-900 dark:text-white">
                {{ formatNumber(comparison.myMetrics.subscribers) }}
              </span>
              <component
                :is="trendIcon(comparison.myMetrics.subscribersTrend)"
                :class="['h-4 w-4', trendColor(comparison.myMetrics.subscribersTrend)]"
              />
            </div>
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('competitorAnalysis.engagement') }}</p>
            <div class="mt-1 flex items-center gap-1.5">
              <span class="text-lg font-bold text-gray-900 dark:text-white">
                {{ comparison.myMetrics.engagement.toFixed(1) }}%
              </span>
              <component
                :is="trendIcon(comparison.myMetrics.engagementTrend)"
                :class="['h-4 w-4', trendColor(comparison.myMetrics.engagementTrend)]"
              />
            </div>
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('competitorAnalysis.uploadFrequency') }}</p>
            <div class="mt-1">
              <span class="text-lg font-bold text-gray-900 dark:text-white">
                {{ comparison.myMetrics.uploadFrequency }}{{ $t('competitorAnalysis.uploadsPerWeek') }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Competitor cards grid -->
      <div v-if="comparison && comparison.competitors.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 xl:grid-cols-3">
        <CompetitorCard
          v-for="entry in comparison.competitors"
          :key="entry.competitor.id"
          :competitor="entry.competitor"
          :metrics="{
            views: entry.views,
            viewsTrend: entry.viewsTrend,
            subscribers: entry.subscribers,
            subscribersTrend: entry.subscribersTrend,
            engagement: entry.engagement,
            engagementTrend: entry.engagementTrend,
            uploadFrequency: entry.uploadFrequency,
          }"
          @remove="handleRemoveCompetitor"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else-if="!comparison || comparison.competitors.length === 0"
        class="py-12 text-center"
      >
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <ChartBarSquareIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
        </div>
        <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
          {{ $t('competitorAnalysis.noCompetitors') }}
        </h3>
        <p class="mb-6 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('competitorAnalysis.noCompetitorsDesc') }}
        </p>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="showAddModal = true"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('competitorAnalysis.addCompetitor') }}
        </button>
      </div>
    </div>

    <!-- Content Gaps Tab -->
    <div v-else-if="activeTab === 'gaps'" class="space-y-4">
      <div>
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('competitorAnalysis.contentGaps.title') }}
        </h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('competitorAnalysis.contentGaps.description') }}
        </p>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
        <ContentGapTable
          :gaps="contentGaps"
          @create-content="handleCreateContent"
        />
      </div>
    </div>

    <!-- Trends Tab -->
    <div v-else-if="activeTab === 'trends'" class="space-y-4">
      <div>
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('competitorAnalysis.trends.title') }}
        </h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('competitorAnalysis.trends.description') }}
        </p>
      </div>

      <div v-if="trendingTopics.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 xl:grid-cols-3">
        <TrendingTopicCard
          v-for="topic in trendingTopics"
          :key="topic.keyword"
          :topic="topic"
        />
      </div>

      <!-- Empty state -->
      <div v-else class="py-12 text-center">
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ $t('competitorAnalysis.trends.noTrends') }}
        </p>
        <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
          {{ $t('competitorAnalysis.trends.noTrendsDesc') }}
        </p>
      </div>
    </div>

    <!-- Add Competitor Modal -->
    <Teleport to="body">
      <div
        v-if="showAddModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="handleCloseModal"
      >
        <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white">
              {{ $t('competitorAnalysis.addCompetitorTitle') }}
            </h3>
            <button
              class="rounded-lg p-1 hover:bg-gray-100 dark:hover:bg-gray-700"
              @click="handleCloseModal"
            >
              <XMarkIcon class="h-5 w-5 text-gray-500 dark:text-gray-400" />
            </button>
          </div>

          <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('competitorAnalysis.addCompetitorDesc') }}
          </p>

          <!-- Competitor name -->
          <div class="mb-4">
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('competitorAnalysis.competitorName') }}
            </label>
            <input
              v-model="newCompetitorName"
              type="text"
              :placeholder="$t('competitorAnalysis.competitorNamePlaceholder')"
              class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
            />
          </div>

          <!-- Platform URLs -->
          <div class="mb-4 space-y-3">
            <div
              v-for="(pUrl, index) in newPlatformUrls"
              :key="index"
              class="flex items-start gap-2"
            >
              <select
                v-model="pUrl.platform"
                class="rounded-lg border border-gray-300 bg-white px-2 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
              >
                <option
                  v-for="opt in platformOptions"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </option>
              </select>
              <input
                v-model="pUrl.url"
                type="url"
                :placeholder="$t('competitorAnalysis.platformUrlPlaceholder')"
                class="flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
              />
              <button
                v-if="newPlatformUrls.length > 1"
                class="rounded-lg p-2 text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20"
                @click="removePlatformRow(index)"
              >
                <XMarkIcon class="h-4 w-4" />
              </button>
            </div>
            <button
              class="text-sm font-medium text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
              @click="addPlatformRow"
            >
              + {{ $t('competitorAnalysis.addPlatform') }}
            </button>
          </div>

          <!-- Actions -->
          <div class="flex justify-end gap-3">
            <button
              class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
              @click="handleCloseModal"
            >
              {{ $t('competitorAnalysis.cancel') }}
            </button>
            <button
              class="btn-primary px-4 py-2 text-sm"
              :disabled="processing || !newCompetitorName.trim()"
              @click="handleAddCompetitor"
            >
              {{ processing ? $t('competitorAnalysis.adding') : $t('competitorAnalysis.add') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
