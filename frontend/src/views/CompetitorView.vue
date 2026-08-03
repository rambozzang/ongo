<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import OTabs from '@/components/ui/OTabs.vue'
import {
  PlusIcon,
  ArrowPathIcon,
  UsersIcon,
  ChartBarIcon,
  TrophyIcon,
  ArrowTrendingUpIcon,
  SparklesIcon,
  MagnifyingGlassIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { useCompetitorStore } from '@/stores/competitor'
import { useNotificationStore } from '@/stores/notification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import type { Competitor } from '@/types/competitor'
import CompetitorCard from '@/components/competitor/CompetitorCard.vue'
import ComparisonChart from '@/components/competitor/ComparisonChart.vue'
import AddCompetitorModal from '@/components/competitor/AddCompetitorModal.vue'
import TrendingVideoList from '@/components/competitor/TrendingVideoList.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'

type Tab = 'list' | 'comparison' | 'trending'

const { t } = useI18n()
const competitorStore = useCompetitorStore()
const notificationStore = useNotificationStore()
const activeTab = ref<Tab>('list')
const competitorTabs = computed(() => [
  { key: 'list', label: t('competitor.tabList') },
  { key: 'comparison', label: t('competitor.tabComparison') },
  { key: 'trending', label: t('competitor.tabTrending') },
])
const isAddModalOpen = ref(false)
/** 비교 탭의 대상 채널 — 목록의 다중 선택(selectedIds)과는 별개다. */
const selectedCompetitorId = ref<number | null>(null)
const isRefreshing = ref(false)
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)
const showBulkDeleteModal = ref(false)
const bulkDeleting = ref(false)

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const competitorSortOptions = computed<ListSortOption<Competitor>[]>(() => [
  {
    key: 'subscribers',
    label: t('competitor.sortSubscribers'),
    accessor: 'subscriberCount',
    kind: 'number',
    defaultDir: 'desc',
  },
  {
    key: 'avgViews',
    label: t('competitor.sortAvgViews'),
    accessor: 'avgViews',
    kind: 'number',
    defaultDir: 'desc',
  },
  {
    key: 'growth',
    label: t('competitor.sortGrowth'),
    accessor: 'growthRate',
    kind: 'number',
    defaultDir: 'desc',
  },
  { key: 'name', label: t('competitor.sortName'), accessor: 'name', kind: 'string', defaultDir: 'asc' },
])

/** 추적 상태 필터 — ListToolbar의 filters 슬롯에 얹는다. */
const trackingFilter = ref<'all' | 'tracking' | 'untracked'>('all')

const trackingFilters = computed(() => [
  { label: t('competitor.filterAll'), value: 'all' as const },
  { label: t('competitor.filterTracking'), value: 'tracking' as const },
  { label: t('competitor.filterUntracked'), value: 'untracked' as const },
])

const {
  query,
  sortKey,
  sortDir,
  filtered: filteredCompetitors,
  visibleCount,
  isSourceEmpty,
  isResultEmpty,
  resetFilters,
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  toggleAll,
  clearSelection,
} = useListControls<Competitor>(() => competitorStore.competitors, {
  searchFields: ['name', 'platform', 'channelUrl'],
  sortOptions: competitorSortOptions,
  defaultSortKey: 'subscribers',
  filters: computed(() =>
    trackingFilter.value === 'all'
      ? []
      : [
          (competitor: Competitor) =>
            trackingFilter.value === 'tracking' ? competitor.isTracking : !competitor.isTracking,
        ],
  ),
})

const resetSearchAndFilters = () => {
  resetFilters()
  trackingFilter.value = 'all'
}

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
  deleteTargetId.value = id
  showDeleteModal.value = true
}

function confirmRemoveCompetitor() {
  const id = deleteTargetId.value
  deleteTargetId.value = null
  if (id === null) return
  competitorStore.removeCompetitor(id)
  if (selectedCompetitorId.value === id) {
    selectedCompetitorId.value = null
  }
}

async function handleBulkRemove() {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkDeleting.value = true
  try {
    await Promise.all(ids.map((id) => competitorStore.removeCompetitor(id)))
    // 비교 대상이 삭제됐다면 비교 탭이 유령 채널을 가리키지 않도록 비운다.
    if (selectedCompetitorId.value !== null && ids.includes(selectedCompetitorId.value)) {
      selectedCompetitorId.value = null
    }
    notificationStore.success(t('competitor.bulkDeleteDone', { count: ids.length }))
  } catch {
    notificationStore.error(t('competitor.bulkDeleteFailed'))
  } finally {
    bulkDeleting.value = false
    clearSelection()
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
  <div class="relative min-h-full space-y-5 py-5 text-content">
      <!-- Header -->
      <PageHeader :title="$t('competitor.title')" :description="$t('competitor.description')">
        <template #actions>
          <button
            :disabled="isRefreshing"
            class="btn-secondary inline-flex items-center gap-2"
            @click="handleRefresh"
          >
            <ArrowPathIcon
              :class="['w-5 h-5', isRefreshing && 'animate-spin']"
            />
            <span>{{ $t('competitor.refresh') }}</span>
          </button>
          <button
            class="btn-primary inline-flex items-center gap-2"
            @click="isAddModalOpen = true"
          >
            <PlusIcon class="w-5 h-5" />
            <span>{{ $t('competitor.addChannel') }}</span>
          </button>
        </template>
      </PageHeader>

      <PageGuide :title="$t('competitor.pageGuideTitle')" :items="($tm('competitor.pageGuide') as string[])" />

        <!-- Overview Cards -->
        <div class="page-grid page-grid--metrics">
          <!-- Tracked channels -->
          <div class="rounded-[11px] border border-line bg-surface-card p-4">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <UsersIcon class="w-5 h-5 text-info-strong" />
                <span class="text-body text-gray-600 dark:text-gray-400">{{ $t('competitor.trackedChannels') }}</span>
              </div>
            </div>
            <p class="text-h1 font-bold text-gray-900 dark:text-white">
              {{ competitorStore.trackedCompetitors.length }}
            </p>
          </div>

          <!-- Average subscribers -->
          <div class="rounded-[11px] border border-line bg-surface-card p-4">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <ChartBarIcon class="w-5 h-5 text-success-strong" />
                <span class="text-body text-gray-600 dark:text-gray-400">{{ $t('competitor.avgSubscribers') }}</span>
              </div>
            </div>
            <p class="text-h1 font-bold text-gray-900 dark:text-white">
              {{ formatNumber(competitorStore.averageMetrics.avgSubscribers) }}
            </p>
          </div>

          <!-- My ranking -->
          <div class="rounded-[11px] border border-line bg-surface-card p-4">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <TrophyIcon class="w-5 h-5 text-warning-strong" />
                <span class="text-body text-gray-600 dark:text-gray-400">{{ $t('competitor.myRanking') }}</span>
              </div>
            </div>
            <p class="text-h1 font-bold text-gray-900 dark:text-white">
              #{{ competitorStore.myRanking }}
            </p>
          </div>

          <!-- Growth rate comparison -->
          <div class="rounded-[11px] border border-line bg-surface-card p-4">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <ArrowTrendingUpIcon class="w-5 h-5 text-primary-600 dark:text-primary-400" />
                <span class="text-body text-gray-600 dark:text-gray-400">{{ $t('competitor.avgGrowthRate') }}</span>
              </div>
            </div>
            <p class="text-h1 font-bold text-gray-900 dark:text-white">
              {{ competitorStore.averageMetrics.avgGrowthRate }}%
            </p>
          </div>
        </div>

      <!-- Tabs -->
      <OTabs v-model="activeTab" :tabs="competitorTabs" class="mb-6 mt-8" />

      <!-- Tab Content -->
      <div>
        <!-- Channel List Tab -->
        <div v-if="activeTab === 'list'">
          <!-- 검색 · 정렬 · 일괄 작업 -->
          <ListToolbar
            v-if="!isSourceEmpty"
            v-model="query"
            v-model:sort-key="sortKey"
            v-model:sort-dir="sortDir"
            :sort-options="competitorSortOptions"
            :selected-count="selectedCount"
            :total-count="visibleCount"
            :search-placeholder="$t('competitor.searchPlaceholder')"
            :search-label="$t('competitor.searchLabel')"
            @clear-selection="clearSelection"
          >
            <template #filters>
              <button
                v-for="option in trackingFilters"
                :key="option.value"
                type="button"
                :class="[
                  'min-h-10 rounded-lg px-3 py-2 text-body font-medium transition-colors',
                  trackingFilter === option.value
                    ? 'bg-primary-600 text-white dark:bg-primary-500'
                    : 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
                ]"
                @click="trackingFilter = option.value"
              >
                {{ option.label }}
              </button>
            </template>

            <template #bulk-actions>
              <button
                type="button"
                class="btn-danger inline-flex items-center gap-1.5"
                :disabled="bulkDeleting"
                @click="showBulkDeleteModal = true"
              >
                <TrashIcon class="h-4 w-4" aria-hidden="true" />
                {{ $t('list.bulkDelete') }}
              </button>
            </template>
          </ListToolbar>

          <!-- 전체 선택 -->
          <div v-if="!isSourceEmpty && visibleCount > 0" class="mb-3 flex items-center gap-2">
            <input
              id="competitor-select-all"
              type="checkbox"
              :class="CHECKBOX_CLASS"
              :checked="allSelected"
              :indeterminate="someSelected"
              @change="toggleAll"
            />
            <label for="competitor-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
              {{ $t('list.selectAll', { count: visibleCount }) }}
            </label>
          </div>

          <!-- 등록된 채널이 하나도 없을 때 -->
          <EmptyState
            v-if="isSourceEmpty"
            :icon="UsersIcon"
            :title="$t('competitor.emptyList')"
            :description="$t('competitor.emptyListDescription')"
            :action-label="$t('competitor.addChannelAction')"
            @action="isAddModalOpen = true"
          />

          <!-- 검색·필터 결과만 비었을 때 -->
          <EmptyState
            v-else-if="isResultEmpty"
            :icon="MagnifyingGlassIcon"
            :title="$t('list.noResultsTitle')"
            :description="$t('list.noResultsDescription')"
            :action-label="$t('list.resetFilters')"
            @action="resetSearchAndFilters"
          />

          <div
            v-else
            class="page-grid page-grid--cards"
          >
            <div
              v-for="competitor in filteredCompetitors"
              :key="competitor.id"
              class="flex items-start gap-3"
            >
              <input
                type="checkbox"
                :class="[CHECKBOX_CLASS, 'mt-5']"
                :checked="isSelected(competitor.id)"
                :aria-label="$t('list.selectItem', { name: competitor.name })"
                @change="toggle(competitor.id)"
              />
              <CompetitorCard
                :competitor="competitor"
                :selected="selectedCompetitorId === competitor.id"
                class="min-w-0 flex-1"
                @toggle-tracking="handleToggleTracking"
                @remove="handleRemoveCompetitor"
                @select="handleSelectCompetitor"
              />
            </div>
          </div>
        </div>

        <!-- Comparison Tab -->
        <div v-if="activeTab === 'comparison'">
          <div v-if="!selectedCompetitorId" class="text-center py-12">
            <p class="text-gray-500 dark:text-gray-400 mb-4">
              {{ $t('competitor.selectChannel') }}
            </p>
            <button
              class="btn-primary"
              @click="activeTab = 'list'"
            >
              {{ $t('competitor.goToList') }}
            </button>
          </div>
          <div v-else>
            <!-- Competitor selector -->
            <div class="mb-6">
              <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                {{ $t('competitor.comparisonTarget') }}
              </label>
              <select
                v-model="selectedCompetitorId"
                class="input-field mobile:w-auto"
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
            <div class="card">
              <h2 class="text-h2 font-semibold text-gray-900 dark:text-gray-100 mb-6">
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
          <div class="card">
            <h2 class="text-h2 font-semibold text-gray-900 dark:text-gray-100 mb-4">
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
        <div class="card">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-h2 font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
              <SparklesIcon class="w-5 h-5 text-primary-600" />
              {{ $t('competitor.aiInsightTitle') }}
            </h2>
            <button
              :disabled="competitorStore.insightLoading"
              class="btn-primary text-body"
              @click="competitorStore.fetchInsight()"
            >
              {{ competitorStore.insightLoading ? $t('competitor.aiAnalyzing') : $t('competitor.aiAnalyzeButton') }}
            </button>
          </div>

          <div v-if="competitorStore.aiInsight" class="space-y-4">
            <p class="text-gray-700 dark:text-gray-300">{{ competitorStore.aiInsight.summary }}</p>

            <div class="page-grid page-grid--split">
              <div class="bg-success-subtle rounded-lg p-4">
                <h3 class="font-medium text-success-strong mb-2">{{ $t('competitor.strengths') }}</h3>
                <ul class="space-y-1 text-body text-success-strong">
                  <li v-for="(s, i) in competitorStore.aiInsight.strengths" :key="i">- {{ s }}</li>
                </ul>
              </div>
              <div class="bg-error-subtle rounded-lg p-4">
                <h3 class="font-medium text-error-strong mb-2">{{ $t('competitor.weaknesses') }}</h3>
                <ul class="space-y-1 text-body text-error-strong">
                  <li v-for="(w, i) in competitorStore.aiInsight.weaknesses" :key="i">- {{ w }}</li>
                </ul>
              </div>
              <div class="bg-info-subtle rounded-lg p-4">
                <h3 class="font-medium text-info-strong mb-2">{{ $t('competitor.opportunities') }}</h3>
                <ul class="space-y-1 text-body text-info-strong">
                  <li v-for="(o, i) in competitorStore.aiInsight.opportunities" :key="i">- {{ o }}</li>
                </ul>
              </div>
              <div class="bg-primary-50 dark:bg-primary-900/20 rounded-lg p-4">
                <h3 class="font-medium text-primary-700 dark:text-primary-300 mb-2">{{ $t('competitor.recommendations') }}</h3>
                <ul class="space-y-1 text-body text-primary-700 dark:text-primary-400">
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

    <!-- 경쟁 채널 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('competitor.deleteTitle')"
      :message="$t('competitor.confirmDelete')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmRemoveCompetitor"
      @cancel="deleteTargetId = null"
    />

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showBulkDeleteModal"
      :title="$t('competitor.bulkDeleteTitle')"
      :message="$t('competitor.bulkDeleteMessage', { count: selectedCount })"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="handleBulkRemove"
    />
  </div>
</template>
