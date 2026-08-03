<template>
  <div :class="[isTablet ? '' : 'space-y-4', 'relative min-h-full py-5 text-content']">
    <!-- Mobile Header -->
    <template v-if="!isTablet">
      <div>
        <h1 class="text-title font-bold text-gray-900 dark:text-gray-100">
          {{ $t('abTest.title') }}
        </h1>
        <p class="mt-0.5 text-body-xs text-gray-500 dark:text-gray-400">
          {{ $t('abTest.description') }}
        </p>
      </div>

      <PageGuide
        :title="$t('abTest.pageGuideTitle')"
        :items="($tm('abTest.pageGuideMobile') as string[])"
      />

      <!-- Credit Display -->
      <div
        class="flex items-center gap-2 rounded-lg border px-3 py-2 text-body-xs"
        :class="isLow
          ? 'border-error bg-error-subtle'
          : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
      >
        <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-error-strong' : 'text-primary-600'" />
        <span class="text-gray-600 dark:text-gray-300">{{ $t('abTest.remaining') }}</span>
        <span class="font-bold" :class="isLow ? 'text-error-strong' : 'text-primary-600'">
          {{ balance.toLocaleString() }}
        </span>
      </div>

      <!-- Summary Cards (Mobile) -->
      <div class="grid grid-cols-2 gap-3">
        <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
          <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.totalTests') }}</div>
          <div class="mt-1 text-h2 font-bold text-gray-900 dark:text-white">{{ summary?.totalTests ?? tests.length }}</div>
        </div>
        <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
          <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.activeTests') }}</div>
          <div class="mt-1 text-h2 font-bold text-info-strong">{{ activeTests.length }}</div>
        </div>
        <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
          <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.completedTests') }}</div>
          <div class="mt-1 text-h2 font-bold text-success-strong">{{ completedTests.length }}</div>
        </div>
        <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
          <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.avgCtrImprovement') }}</div>
          <div class="mt-1 text-h2 font-bold text-success-strong">+{{ (summary?.avgCtrImprovement ?? 0).toFixed(1) }}%</div>
        </div>
      </div>
    </template>

    <!-- Desktop/Tablet Header -->
    <template v-else>
      <PageHeader :title="$t('abTest.title')" :description="$t('abTest.description')">
        <template #actions>
          <div
            class="flex items-center gap-2 rounded-lg border px-4 py-2 text-body"
            :class="isLow
              ? 'border-error bg-error-subtle'
              : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
          >
            <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-error-strong' : 'text-primary-600'" />
            <span class="text-gray-600 dark:text-gray-300">{{ $t('abTest.remaining') }}</span>
            <span class="font-bold" :class="isLow ? 'text-error-strong' : 'text-primary-600'">
              {{ balance.toLocaleString() }}
            </span>
          </div>
        </template>
      </PageHeader>

      <PageGuide
        :title="$t('abTest.pageGuideTitle')"
        :items="($tm('abTest.pageGuide') as string[])"
      />

      <!-- Summary Cards (Desktop) -->
      <div class="page-grid page-grid--metrics mb-8">
        <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
          <div class="flex items-center justify-between">
            <div>
              <div class="mb-1 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.totalTests') }}</div>
              <div class="text-display font-bold text-gray-900 dark:text-white">{{ summary?.totalTests ?? tests.length }}</div>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30">
              <BeakerIcon class="h-6 w-6 text-primary-600 dark:text-primary-400" />
            </div>
          </div>
        </div>

        <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
          <div class="flex items-center justify-between">
            <div>
              <div class="mb-1 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.activeTests') }}</div>
              <div class="text-display font-bold text-info-strong">{{ activeTests.length }}</div>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-info-subtle">
              <div class="h-3 w-3 animate-pulse rounded-full bg-info"></div>
            </div>
          </div>
        </div>

        <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
          <div class="flex items-center justify-between">
            <div>
              <div class="mb-1 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.completedTests') }}</div>
              <div class="text-display font-bold text-success-strong">{{ completedTests.length }}</div>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-success-subtle">
              <CheckCircleIcon class="h-6 w-6 text-success-strong" />
            </div>
          </div>
        </div>

        <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
          <div class="flex items-center justify-between">
            <div>
              <div class="mb-1 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.avgCtrImprovement') }}</div>
              <div class="text-display font-bold text-success-strong">
                +{{ (summary?.avgCtrImprovement ?? 0).toFixed(1) }}%
              </div>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-success-subtle">
              <ArrowTrendingUpIcon class="h-6 w-6 text-success-strong" />
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Tab Navigation -->
    <OTabs v-model="activeTab" :tabs="tabs" :class="isTablet ? 'mb-6' : 'mb-4'" />

    <!-- Tab Content -->
    <div :class="isTablet ? 'mt-6' : ''">
      <!-- Active Tests -->
      <div v-if="activeTab === 'active'">
        <!-- 검색 · 정렬 · 일괄 작업 -->
        <ListToolbar
          v-if="!isActiveSourceEmpty"
          v-model="activeQuery"
          v-model:sort-key="activeSortKey"
          v-model:sort-dir="activeSortDir"
          :sort-options="activeSortOptions"
          :selected-count="selectedActiveCount"
          :total-count="visibleActiveCount"
          :search-placeholder="$t('abTest.searchPlaceholder')"
          :search-label="$t('abTest.searchLabel')"
          @clear-selection="clearActiveSelection"
        >
          <template #bulk-actions>
            <button
              type="button"
              class="btn-danger inline-flex items-center gap-1.5"
              :disabled="bulkDeleting"
              @click="askBulkDelete(selectedActiveTests, clearActiveSelection)"
            >
              <TrashIcon class="h-4 w-4" aria-hidden="true" />
              {{ $t('list.bulkDelete') }}
            </button>
          </template>
        </ListToolbar>

        <!-- 전체 선택 -->
        <div v-if="!isActiveSourceEmpty && visibleActiveCount > 0" class="mb-3 flex items-center gap-2">
          <input
            id="abtest-active-select-all"
            type="checkbox"
            :class="CHECKBOX_CLASS"
            :checked="allActiveSelected"
            :indeterminate="someActiveSelected"
            @change="toggleAllActive"
          />
          <label for="abtest-active-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
            {{ $t('list.selectAll', { count: visibleActiveCount }) }}
          </label>
        </div>

        <AsyncState
          :loading="processing && isActiveSourceEmpty"
          :empty="isActiveSourceEmpty"
          skeleton="card"
          :skeleton-count="2"
          :empty-icon="BeakerIcon"
          :empty-title="$t('abTest.emptyTitle')"
          :empty-description="$t('abTest.emptyActiveDesc')"
          :empty-action-label="$t('abTest.createFirstTest')"
          :retryable="false"
          @empty-action="store.setActiveTab('create')"
        >
          <!-- 검색 결과만 비었을 때 -->
          <EmptyState
            v-if="isActiveResultEmpty"
            :icon="MagnifyingGlassIcon"
            :title="$t('list.noResultsTitle')"
            :description="$t('list.noResultsDescription')"
            :action-label="$t('list.resetFilters')"
            @action="resetActiveFilters"
          />

          <div v-else :class="isTablet ? 'page-grid page-grid--split' : 'space-y-4'">
            <div
              v-for="test in filteredActiveTests"
              :key="test.id"
              class="flex items-start gap-3"
            >
              <input
                type="checkbox"
                :class="[CHECKBOX_CLASS, 'mt-7']"
                :checked="isActiveSelected(test.id)"
                :aria-label="$t('list.selectItem', { name: test.videoTitle })"
                @change="toggleActiveSelection(test.id)"
              />
              <AbTestCard
                :test="test"
                class="min-w-0 flex-1"
                @select="handleSelectTest"
                @start="handleStartTest"
                @pause="handlePauseTest"
                @apply-winner="handleApplyWinner"
              />
            </div>
          </div>
        </AsyncState>
      </div>

      <!-- Completed Tests -->
      <div v-if="activeTab === 'completed'">
        <!-- 검색 · 정렬 · 일괄 작업 -->
        <ListToolbar
          v-if="!isCompletedSourceEmpty"
          v-model="completedQuery"
          v-model:sort-key="completedSortKey"
          v-model:sort-dir="completedSortDir"
          :sort-options="completedSortOptions"
          :selected-count="selectedCompletedCount"
          :total-count="visibleCompletedCount"
          :search-placeholder="$t('abTest.searchPlaceholder')"
          :search-label="$t('abTest.searchLabel')"
          @clear-selection="clearCompletedSelection"
        >
          <template #bulk-actions>
            <button
              type="button"
              class="btn-danger inline-flex items-center gap-1.5"
              :disabled="bulkDeleting"
              @click="askBulkDelete(selectedCompletedTests, clearCompletedSelection)"
            >
              <TrashIcon class="h-4 w-4" aria-hidden="true" />
              {{ $t('list.bulkDelete') }}
            </button>
          </template>
        </ListToolbar>

        <!-- 전체 선택 -->
        <div v-if="!isCompletedSourceEmpty && visibleCompletedCount > 0" class="mb-3 flex items-center gap-2">
          <input
            id="abtest-completed-select-all"
            type="checkbox"
            :class="CHECKBOX_CLASS"
            :checked="allCompletedSelected"
            :indeterminate="someCompletedSelected"
            @change="toggleAllCompleted"
          />
          <label for="abtest-completed-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
            {{ $t('list.selectAll', { count: visibleCompletedCount }) }}
          </label>
        </div>

        <AsyncState
          :loading="processing && isCompletedSourceEmpty"
          :empty="isCompletedSourceEmpty"
          skeleton="card"
          :skeleton-count="2"
          :empty-icon="CheckCircleIcon"
          :empty-title="$t('abTest.emptyTitle')"
          :empty-description="$t('abTest.emptyCompletedDesc')"
          :retryable="false"
        >
          <!-- 검색 결과만 비었을 때 -->
          <EmptyState
            v-if="isCompletedResultEmpty"
            :icon="MagnifyingGlassIcon"
            :title="$t('list.noResultsTitle')"
            :description="$t('list.noResultsDescription')"
            :action-label="$t('list.resetFilters')"
            @action="resetCompletedFilters"
          />

          <div v-else :class="isTablet ? 'page-grid page-grid--split' : 'space-y-4'">
            <div
              v-for="test in filteredCompletedTests"
              :key="test.id"
              class="flex items-start gap-3"
            >
              <input
                type="checkbox"
                :class="[CHECKBOX_CLASS, 'mt-7']"
                :checked="isCompletedSelected(test.id)"
                :aria-label="$t('list.selectItem', { name: test.videoTitle })"
                @change="toggleCompletedSelection(test.id)"
              />
              <AbTestCard
                :test="test"
                class="min-w-0 flex-1"
                @select="handleSelectTest"
                @start="handleStartTest"
                @pause="handlePauseTest"
                @apply-winner="handleApplyWinner"
              />
            </div>
          </div>
        </AsyncState>
      </div>

      <!-- Create New Test -->
      <div v-if="activeTab === 'create'">
        <CreateTestForm
          :videos="videos"
          :processing="processing"
          @create="handleCreateTest"
        />
      </div>
    </div>

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showConfirmModal"
      :title="confirmTitle"
      :message="confirmMessage"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="runPendingAction"
      @cancel="pendingAction = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  SparklesIcon,
  BeakerIcon,
  CheckCircleIcon,
  PlusIcon,
  ArrowTrendingUpIcon,
  MagnifyingGlassIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import AsyncState from '@/components/common/AsyncState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import OTabs from '@/components/ui/OTabs.vue'
import AbTestCard from '@/components/abtest/AbTestCard.vue'
import CreateTestForm from '@/components/abtest/CreateTestForm.vue'
import { useAbTestStore } from '@/stores/abtest'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import { useListControls, type FieldAccessor, type ListSortOption } from '@/composables/useListControls'
import type { AbTest, AbTestStatus, AbTestType, VariantLabel } from '@/types/abtest'

const { t } = useI18n({ useScope: 'global' })
const store = useAbTestStore()
const notification = useNotification()
const { balance, isLow, fetchBalance } = useCredit()

const isTablet = useMediaQuery('(min-width: 768px)')

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const {
  tests,
  videos,
  summary,
  activeTests,
  completedTests,
  activeTab,
  processing,
} = storeToRefs(store)

const tabs = computed(() => [
  { key: 'active' as const, label: t('abTest.tabActive'), icon: BeakerIcon },
  { key: 'completed' as const, label: t('abTest.tabCompleted'), icon: CheckCircleIcon },
  { key: 'create' as const, label: t('abTest.tabCreate'), icon: PlusIcon },
])

// ─── 검색·정렬 축 ─────────────────────────────────────────
const TYPE_LABEL_KEY: Record<AbTestType, string> = {
  THUMBNAIL: 'abTest.typeThumbnail',
  TITLE: 'abTest.typeTitle',
  DESCRIPTION: 'abTest.typeDescription',
  TAGS: 'abTest.typeTags',
}

const STATUS_LABEL_KEY: Record<AbTestStatus, string> = {
  DRAFT: 'abTest.statusDraft',
  RUNNING: 'abTest.statusRunning',
  PAUSED: 'abTest.statusPaused',
  COMPLETED: 'abTest.statusCompleted',
  CANCELLED: 'abTest.statusCancelled',
}

/** 카드에 실제로 보이는 한국어 라벨("썸네일", "진행 중")로도 찾을 수 있게 한다. */
const localizedLabels = (test: AbTest): string[] => [
  t(TYPE_LABEL_KEY[test.type]),
  t(STATUS_LABEL_KEY[test.status]),
]

/** 변형안 문구(제목·설명 후보)까지 검색 대상에 넣는다. */
const variantValues = (test: AbTest): string[] => test.variants.map((variant) => variant.value)

const testSearchFields: FieldAccessor<AbTest>[] = [
  'videoTitle',
  'type',
  'status',
  localizedLabels,
  variantValues,
]

/** 진행 중 > 일시정지 > 초안 순 — "지금 돌고 있는 테스트"가 위로 온다. */
const STATUS_RANK: Record<AbTestStatus, number> = {
  RUNNING: 5,
  PAUSED: 4,
  DRAFT: 3,
  COMPLETED: 2,
  CANCELLED: 1,
}

/** 두 탭이 공유하는 성과 축. */
const performanceSortOptions = (): ListSortOption<AbTest>[] => [
  {
    key: 'confidence',
    label: t('abTest.sortConfidence'),
    accessor: 'confidenceLevel',
    kind: 'number',
    defaultDir: 'desc',
  },
  {
    key: 'impressions',
    label: t('abTest.sortImpressions'),
    accessor: 'totalImpressions',
    kind: 'number',
    defaultDir: 'desc',
  },
  { key: 'title', label: t('abTest.sortTitle'), accessor: 'videoTitle', kind: 'string', defaultDir: 'asc' },
]

const activeSortOptions = computed<ListSortOption<AbTest>[]>(() => [
  {
    key: 'status',
    label: t('abTest.sortStatus'),
    accessor: (test) => STATUS_RANK[test.status],
    kind: 'number',
    defaultDir: 'desc',
  },
  {
    key: 'recent',
    label: t('abTest.sortRecent'),
    accessor: (test) => test.startedAt ?? test.createdAt,
    kind: 'date',
    defaultDir: 'desc',
  },
  ...performanceSortOptions(),
])

const completedSortOptions = computed<ListSortOption<AbTest>[]>(() => [
  // 완료 탭은 전부 COMPLETED라 상태 축이 의미가 없다 — 종료 시각이 기본 축이다.
  {
    key: 'recent',
    label: t('abTest.sortRecent'),
    accessor: (test) => test.endedAt ?? test.createdAt,
    kind: 'date',
    defaultDir: 'desc',
  },
  ...performanceSortOptions(),
])

// ─── 진행 중 테스트 목록 제어 ─────────────────────────────
const {
  query: activeQuery,
  sortKey: activeSortKey,
  sortDir: activeSortDir,
  filtered: filteredActiveTests,
  visibleCount: visibleActiveCount,
  isSourceEmpty: isActiveSourceEmpty,
  isResultEmpty: isActiveResultEmpty,
  resetFilters: resetActiveFilters,
  selectedItems: selectedActiveTests,
  selectedCount: selectedActiveCount,
  allSelected: allActiveSelected,
  someSelected: someActiveSelected,
  isSelected: isActiveSelected,
  toggle: toggleActiveSelection,
  toggleAll: toggleAllActive,
  clearSelection: clearActiveSelection,
} = useListControls<AbTest>(() => activeTests.value, {
  searchFields: testSearchFields,
  sortOptions: activeSortOptions,
  defaultSortKey: 'status',
})

// ─── 완료된 테스트 목록 제어 ──────────────────────────────
/**
 * 진행 중 목록과 완전히 독립된 인스턴스다.
 * 하나를 탭 필터로 공유하면 탭을 옮긴 직후 선택이 남아
 * "완료 탭에서 고른 것"이 아닌 테스트까지 일괄 삭제될 수 있다.
 */
const {
  query: completedQuery,
  sortKey: completedSortKey,
  sortDir: completedSortDir,
  filtered: filteredCompletedTests,
  visibleCount: visibleCompletedCount,
  isSourceEmpty: isCompletedSourceEmpty,
  isResultEmpty: isCompletedResultEmpty,
  resetFilters: resetCompletedFilters,
  selectedItems: selectedCompletedTests,
  selectedCount: selectedCompletedCount,
  allSelected: allCompletedSelected,
  someSelected: someCompletedSelected,
  isSelected: isCompletedSelected,
  toggle: toggleCompletedSelection,
  toggleAll: toggleAllCompleted,
  clearSelection: clearCompletedSelection,
} = useListControls<AbTest>(() => completedTests.value, {
  searchFields: testSearchFields,
  sortOptions: completedSortOptions,
  defaultSortKey: 'recent',
})

// ─── 일괄 삭제 ────────────────────────────────────────────
const showConfirmModal = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const pendingAction = shallowRef<(() => void | Promise<void>) | null>(null)
const bulkDeleting = ref(false)

function askConfirm(title: string, message: string, action: () => void | Promise<void>) {
  confirmTitle.value = title
  confirmMessage.value = message
  pendingAction.value = action
  showConfirmModal.value = true
}

function runPendingAction() {
  const action = pendingAction.value
  pendingAction.value = null
  void action?.()
}

/** 진행 중인 테스트가 섞여 있으면 그 사실을 확인 문구에 못 박는다. */
function bulkDeleteMessage(targets: AbTest[]): string {
  const running = targets.filter((test) => test.status === 'RUNNING').length
  return running > 0
    ? t('abTest.bulkDeleteRunningMessage', { count: targets.length, running })
    : t('abTest.bulkDeleteMessage', { count: targets.length })
}

async function bulkDelete(targets: AbTest[], clearSelection: () => void) {
  const ids = targets.map((test) => test.id)
  if (ids.length === 0) return
  bulkDeleting.value = true
  // 스토어의 deleteTest는 실패를 삼키고 error에만 기록하므로,
  // 실제로 몇 건이 사라졌는지는 목록 길이 변화로 판정한다.
  const before = tests.value.length
  try {
    await Promise.all(ids.map((id) => store.deleteTest(id)))
    const removed = before - tests.value.length
    if (removed > 0) notification.success(t('abTest.bulkDeleteDone', { count: removed }))
    if (removed < ids.length) notification.error(t('abTest.deleteFailed'))
    await store.fetchSummary()
  } finally {
    bulkDeleting.value = false
    clearSelection()
  }
}

function askBulkDelete(targets: AbTest[], clearSelection: () => void) {
  if (targets.length === 0) return
  askConfirm(t('abTest.bulkDeleteTitle'), bulkDeleteMessage(targets), () =>
    bulkDelete(targets, clearSelection),
  )
}

function handleSelectTest(testId: number) {
  const found = tests.value.find(item => item.id === testId)
  if (found) store.selectTest(found)
}

async function handleStartTest(testId: number) {
  await store.startTest(testId)
}

async function handlePauseTest(testId: number) {
  await store.pauseTest(testId)
}

async function handleApplyWinner(testId: number) {
  await store.applyWinner(testId)
}

async function handleCreateTest(data: {
  videoId: number
  type: AbTestType
  variants: { label: VariantLabel; value: string }[]
  durationHours: number
}) {
  await store.createTest(data.videoId, data.type, data.variants, data.durationHours)
  store.setActiveTab('active')
  await fetchBalance()
}

onMounted(() => {
  store.fetchTests()
  store.fetchVideos()
  store.fetchSummary()
  fetchBalance()
})
</script>
