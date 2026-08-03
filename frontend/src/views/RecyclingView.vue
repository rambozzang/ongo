<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PlusIcon,
  ArrowPathIcon,
  QueueListIcon,
  CheckBadgeIcon,
  CalendarDaysIcon,
  LightBulbIcon,
  MagnifyingGlassIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { useRecyclingStore } from '@/stores/recycling'
import { useNotificationStore } from '@/stores/notification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import OTabs from '@/components/ui/OTabs.vue'
import RecyclingQueueCard from '@/components/recycling/RecyclingQueueCard.vue'
import RecyclingCreateModal from '@/components/recycling/RecyclingCreateModal.vue'
import RecyclingHistoryComponent from '@/components/recycling/RecyclingHistory.vue'
import type { RecyclingQueue, RecyclingQueueCreateRequest } from '@/types/recycling'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import KpiCard from '@/components/redesign/KpiCard.vue'

const { t } = useI18n()
const recyclingStore = useRecyclingStore()
const notificationStore = useNotificationStore()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const activeTab = ref<'queues' | 'history' | 'suggestions'>('queues')
const isModalOpen = ref(false)
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)
const showBulkDeleteModal = ref(false)
const bulkDeleting = ref(false)

const tabList = computed(() => [
  { key: 'queues', label: t('recycling.tabQueues'), count: recyclingStore.queues.length },
  { key: 'history', label: t('recycling.tabHistory'), count: recyclingStore.history.length },
  { key: 'suggestions', label: t('recycling.tabSuggestions'), count: recyclingStore.suggestions.filter((s: { status: string }) => s.status === 'PENDING').length },
])
const editingQueue = ref<RecyclingQueue | undefined>(undefined)

const activeQueueCount = computed(() => recyclingStore.activeQueues.length)
const totalRecycled = computed(() => recyclingStore.totalRecycledCount)
const nextScheduledLabel = computed(() => {
  const item = recyclingStore.nextScheduledItem
  if (!item) return t('recycling.none')
  const date = new Date(item.scheduledAt)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
})

/**
 * 기존 기본 정렬(활성 큐 우선 → 최근 생성순)을 하나의 비교 축으로 합친 값.
 * 활성 가산점이 어떤 타임스탬프보다도 크므로 "활성 우선, 그 안에서 최신순"이 그대로 유지된다.
 */
const ACTIVE_BOOST = 1e15
const activePriority = (queue: RecyclingQueue): number => {
  const created = new Date(queue.createdAt).getTime()
  return (queue.isActive ? ACTIVE_BOOST : 0) + (Number.isNaN(created) ? 0 : created)
}

const queueSortOptions = computed<ListSortOption<RecyclingQueue>[]>(() => [
  {
    key: 'status',
    label: t('recycling.sortActiveFirst'),
    accessor: activePriority,
    kind: 'number',
    defaultDir: 'desc',
  },
  {
    key: 'nextSchedule',
    label: t('recycling.sortNextSchedule'),
    accessor: 'nextScheduledAt',
    kind: 'date',
    defaultDir: 'asc',
  },
  {
    key: 'videoCount',
    label: t('recycling.sortVideoCount'),
    accessor: 'videoCount',
    kind: 'number',
    defaultDir: 'desc',
  },
  { key: 'name', label: t('recycling.sortName'), accessor: 'name', kind: 'string', defaultDir: 'asc' },
])

/** 활성 상태 필터 — ListToolbar의 filters 슬롯에 얹는다. */
const queueFilter = ref<'all' | 'active' | 'inactive'>('all')

const queueFilters = computed(() => [
  { label: t('recycling.filterAll'), value: 'all' as const },
  { label: t('recycling.filterActive'), value: 'active' as const },
  { label: t('recycling.filterInactive'), value: 'inactive' as const },
])

const {
  query,
  sortKey,
  sortDir,
  filtered: filteredQueues,
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
} = useListControls<RecyclingQueue>(() => recyclingStore.queues, {
  searchFields: ['name', 'platforms', (queue) => queue.filterCriteria.categories ?? []],
  sortOptions: queueSortOptions,
  defaultSortKey: 'status',
  filters: computed(() =>
    queueFilter.value === 'all'
      ? []
      : [(queue: RecyclingQueue) => (queueFilter.value === 'active' ? queue.isActive : !queue.isActive)],
  ),
})

const resetSearchAndFilters = () => {
  resetFilters()
  queueFilter.value = 'all'
}

function openCreateModal() {
  editingQueue.value = undefined
  isModalOpen.value = true
}

function openEditModal(id: number) {
  editingQueue.value = recyclingStore.queues.find((q) => q.id === id)
  isModalOpen.value = true
}

function closeModal() {
  isModalOpen.value = false
  editingQueue.value = undefined
}

function handleSave(data: RecyclingQueueCreateRequest) {
  if (editingQueue.value) {
    recyclingStore.updateQueue(editingQueue.value.id, {
      name: data.name,
      filterCriteria: data.filterCriteria,
      frequency: data.frequency,
      scheduleDays: data.scheduleDays,
      scheduleTime: data.scheduleTime,
      platforms: data.platforms,
      titleVariation: data.titleVariation,
    })
  } else {
    recyclingStore.createQueue(data)
  }
}

function handleDelete(id: number) {
  deleteTargetId.value = id
  showDeleteModal.value = true
}

function confirmDelete() {
  const id = deleteTargetId.value
  deleteTargetId.value = null
  if (id === null) return
  recyclingStore.deleteQueue(id)
}

function handleBulkDelete() {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkDeleting.value = true
  try {
    ids.forEach((id) => recyclingStore.deleteQueue(id))
    notificationStore.success(t('recycling.bulkDeleteDone', { count: ids.length }))
  } finally {
    bulkDeleting.value = false
    clearSelection()
  }
}

function handleToggle(id: number) {
  recyclingStore.toggleActive(id)
}

const suggestionTypeLabels = computed<Record<string, string>>(() => ({
  REPOST: t('recycling.typeRepost'),
  CLIP: t('recycling.typeClip'),
  REMIX: t('recycling.typeRemix'),
  UPDATE_METADATA: t('recycling.typeUpdateMetadata'),
}))

async function handleGenerateSuggestions() {
  await recyclingStore.generateSuggestions()
}

async function handleAcceptSuggestion(id: number) {
  await recyclingStore.acceptSuggestion(id)
}

async function handleDismissSuggestion(id: number) {
  await recyclingStore.dismissSuggestion(id)
}

onMounted(() => {
  recyclingStore.fetchSuggestions()
})
</script>

<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <PageHeader :title="$t('recycling.title')" :description="$t('recycling.description')">
      <template #actions>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openCreateModal"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('recycling.newQueue') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('recycling.pageGuideTitle')" :items="($tm('recycling.pageGuide') as string[])" />

    <!-- Stats Cards -->
    <div class="grid gap-2.5 tablet:grid-cols-3 mb-6">
      <KpiCard :label="$t('recycling.activeQueues')" :value="`${activeQueueCount}${$t('recycling.countUnit')}`" :delta-variant="activeQueueCount > 0 ? 'success' : 'muted'" />
      <KpiCard :label="$t('recycling.totalRecycled')" :value="`${totalRecycled}${$t('recycling.caseUnit')}`" />
      <KpiCard :label="$t('recycling.nextScheduled')" :value="nextScheduledLabel" />
      <template v-if="false">
      <div class="card">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-info-subtle">
            <QueueListIcon class="h-5 w-5 text-info-strong" />
          </div>
          <div>
            <p class="text-body text-gray-500 dark:text-gray-400">{{ $t('recycling.activeQueues') }}</p>
            <p class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ activeQueueCount }}{{ $t('recycling.countUnit') }}</p>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-success-subtle">
            <CheckBadgeIcon class="h-5 w-5 text-success-strong" />
          </div>
          <div>
            <p class="text-body text-gray-500 dark:text-gray-400">{{ $t('recycling.totalRecycled') }}</p>
            <p class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ totalRecycled }}{{ $t('recycling.caseUnit') }}</p>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30">
            <CalendarDaysIcon class="h-5 w-5 text-primary-600 dark:text-primary-400" />
          </div>
          <div>
            <p class="text-body text-gray-500 dark:text-gray-400">{{ $t('recycling.nextScheduled') }}</p>
            <p class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ nextScheduledLabel }}</p>
          </div>
        </div>
      </div>
      </template>
    </div>

    <!-- Tabs -->
    <div class="mb-6">
      <OTabs v-model="activeTab" :tabs="tabList" />
    </div>

    <!-- Queues Tab -->
    <div v-if="activeTab === 'queues'">
      <!-- 검색 · 정렬 · 일괄 작업 -->
      <ListToolbar
        v-if="!isSourceEmpty"
        v-model="query"
        v-model:sort-key="sortKey"
        v-model:sort-dir="sortDir"
        :sort-options="queueSortOptions"
        :selected-count="selectedCount"
        :total-count="visibleCount"
        :search-placeholder="$t('recycling.searchPlaceholder')"
        :search-label="$t('recycling.searchLabel')"
        @clear-selection="clearSelection"
      >
        <template #filters>
          <button
            v-for="option in queueFilters"
            :key="option.value"
            type="button"
            :class="[
              'min-h-10 rounded-lg px-3 py-2 text-body font-medium transition-colors',
              queueFilter === option.value
                ? 'bg-primary-600 text-white dark:bg-primary-500'
                : 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
            ]"
            @click="queueFilter = option.value"
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
          id="recycling-select-all"
          type="checkbox"
          :class="CHECKBOX_CLASS"
          :checked="allSelected"
          :indeterminate="someSelected"
          @change="toggleAll"
        />
        <label for="recycling-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
          {{ $t('list.selectAll', { count: visibleCount }) }}
        </label>
      </div>

      <!-- 큐가 하나도 없을 때 -->
      <EmptyState
        v-if="isSourceEmpty"
        :icon="ArrowPathIcon"
        :title="$t('recycling.emptyQueuesTitle')"
        :description="$t('recycling.emptyQueuesDescription')"
        :action-label="$t('recycling.createFirstQueue')"
        @action="openCreateModal"
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

      <div v-else class="page-grid page-grid--cards">
        <div
          v-for="queue in filteredQueues"
          :key="queue.id"
          class="flex items-start gap-3"
        >
          <input
            type="checkbox"
            :class="[CHECKBOX_CLASS, 'mt-6']"
            :checked="isSelected(queue.id)"
            :aria-label="$t('list.selectItem', { name: queue.name })"
            @change="toggle(queue.id)"
          />
          <RecyclingQueueCard
            :queue="queue"
            class="min-w-0 flex-1"
            @edit="openEditModal"
            @delete="handleDelete"
            @toggle="handleToggle"
          />
        </div>
      </div>
    </div>

    <!-- History Tab -->
    <div v-if="activeTab === 'history'">
      <RecyclingHistoryComponent :history="recyclingStore.recentHistory" />
    </div>

    <!-- Suggestions Tab -->
    <div v-if="activeTab === 'suggestions'">
      <div class="mb-4 flex justify-end">
        <button
          :disabled="recyclingStore.suggestionsLoading"
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
          @click="handleGenerateSuggestions"
        >
          <LightBulbIcon class="h-5 w-5" />
          {{ recyclingStore.suggestionsLoading ? $t('recycling.analyzing') : $t('recycling.generateSuggestions') }}
        </button>
      </div>

      <div v-if="recyclingStore.suggestions.length > 0" class="space-y-4">
        <div
          v-for="suggestion in recyclingStore.suggestions"
          :key="suggestion.id"
          class="card"
        >
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <div class="mb-2 flex items-center gap-2">
                <span class="rounded-full bg-primary-100 px-2 py-1 text-body-xs font-semibold text-primary-600 dark:bg-primary-900/30 dark:text-primary-400">
                  {{ suggestionTypeLabels[suggestion.suggestionType] || suggestion.suggestionType }}
                </span>
                <span
                  :class="[
                    'rounded-full px-2 py-1 text-body-xs font-semibold',
                    suggestion.status === 'PENDING'
                      ? 'bg-warning-subtle text-warning-strong'
                      : suggestion.status === 'ACCEPTED'
                        ? 'bg-success-subtle text-success-strong'
                        : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
                  ]"
                >
                  {{ suggestion.status === 'PENDING' ? $t('recycling.statusPending') : suggestion.status === 'ACCEPTED' ? $t('recycling.statusAccepted') : $t('recycling.statusDismissed') }}
                </span>
                <span class="text-body-xs text-gray-500 dark:text-gray-400">
                  {{ $t('recycling.priority') }}: {{ suggestion.priorityScore }}
                </span>
              </div>
              <p class="mb-2 text-body text-gray-700 dark:text-gray-300">{{ suggestion.reason }}</p>
              <div class="flex gap-1">
                <span
                  v-for="platform in suggestion.suggestedPlatforms"
                  :key="platform"
                  class="rounded bg-gray-100 px-2 py-0.5 text-body-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400"
                >
                  {{ platform }}
                </span>
              </div>
            </div>
            <div v-if="suggestion.status === 'PENDING'" class="ml-4 flex gap-2">
              <button
                class="rounded-lg bg-success px-3 py-1.5 text-body font-medium text-white transition-opacity hover:opacity-90"
                @click="handleAcceptSuggestion(suggestion.id)"
              >
                {{ $t('recycling.accept') }}
              </button>
              <button
                class="rounded-lg bg-gray-100 px-3 py-1.5 text-body font-medium text-gray-700 transition-colors hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                @click="handleDismissSuggestion(suggestion.id)"
              >
                {{ $t('recycling.dismiss') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div
        v-else-if="!recyclingStore.suggestionsLoading"
        class="card py-16 text-center"
      >
        <LightBulbIcon class="mx-auto mb-4 h-16 w-16 text-gray-400 dark:text-gray-600" />
        <h3 class="mb-2 text-title font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('recycling.emptySuggestionsTitle') }}
        </h3>
        <p class="mb-6 text-gray-600 dark:text-gray-400">
          {{ $t('recycling.emptySuggestionsDescription') }}
        </p>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="handleGenerateSuggestions"
        >
          <LightBulbIcon class="h-5 w-5" />
          {{ $t('recycling.generateSuggestionsBtn') }}
        </button>
      </div>
    </div>

    <!-- Modal -->
    <RecyclingCreateModal
      :is-open="isModalOpen"
      :queue="editingQueue"
      @close="closeModal"
      @save="handleSave"
    />

    <!-- 큐 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('recycling.deleteConfirmTitle')"
      :message="$t('recycling.deleteConfirmMessage')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmDelete"
      @cancel="deleteTargetId = null"
    />

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showBulkDeleteModal"
      :title="$t('recycling.bulkDeleteTitle')"
      :message="$t('recycling.bulkDeleteMessage', { count: selectedCount })"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="handleBulkDelete"
    />
  </div>
</template>
