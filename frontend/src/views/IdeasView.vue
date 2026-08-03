<template>
  <div class="min-h-full py-5 text-content tablet:py-6">
    <!-- Header -->
    <PageHeader :title="$t('ideas.title')" :description="$t('ideas.description')">
      <template #actions>
        <button
          class="btn-secondary inline-flex items-center gap-2"
          @click="showAiModal = true"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ $t('ideas.aiGenerate') }}
        </button>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openCreateModal"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('ideas.newIdea') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('ideas.pageGuideTitle')" :items="($tm('ideas.pageGuide') as string[])" />

    <!-- 검색 · 정렬 · 일괄 작업 -->
    <ListToolbar
      v-if="!isSourceEmpty"
      v-model="query"
      v-model:sort-key="sortKey"
      v-model:sort-dir="sortDir"
      :sort-options="sortOptions"
      :selected-count="selectedCount"
      :total-count="visibleCount"
      :search-placeholder="$t('ideas.searchPlaceholder')"
      :search-label="$t('ideas.searchLabel')"
      @clear-selection="clearSelection"
    >
      <template #filters>
        <button
          v-for="priority in priorityFilters"
          :key="priority.value"
          type="button"
          :class="[
            'min-h-10 rounded-lg px-3 py-2 text-body font-medium transition-colors',
            selectedPriority === priority.value
              ? 'border border-accent bg-accent text-accent-on'
              : 'border border-line-control bg-surface-card text-content-secondary hover:border-line-hover hover:bg-surface-raised hover:text-content'
          ]"
          @click="setPriorityFilter(priority.value)"
        >
          {{ priority.label }}
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
        id="ideas-select-all"
        type="checkbox"
        :class="CHECKBOX_CLASS"
        :checked="allSelected"
        :indeterminate="someSelected"
        @change="toggleAll"
      />
      <label for="ideas-select-all" class="cursor-pointer text-body-sm text-content-secondary">
        {{ $t('list.selectAll', { count: visibleCount }) }}
      </label>
    </div>

    <AsyncState
      :loading="ideasStore.loading && isSourceEmpty"
      :empty="isSourceEmpty"
      skeleton="card"
      :skeleton-count="4"
      :empty-icon="LightBulbIcon"
      :empty-title="$t('ideas.emptyTitle')"
      :empty-description="$t('ideas.emptyDescription')"
      :empty-action-label="$t('ideas.createFirstIdea')"
      :retryable="false"
      @empty-action="openCreateModal"
    >
      <!-- 검색·필터 결과만 비었을 때 -->
      <EmptyState
        v-if="isResultEmpty"
        :icon="MagnifyingGlassIcon"
        :title="$t('list.noResultsTitle')"
        :description="$t('list.noResultsDescription')"
        :action-label="$t('list.resetFilters')"
        @action="resetSearchAndFilters"
      />

      <!-- Kanban Board -->
      <div v-else class="page-grid page-grid--dense h-[calc(100vh-16rem)]">
        <IdeaColumn
          v-for="column in columns"
          :key="column.status"
          :title="column.title"
          :status="column.status"
          :ideas="ideasByStatus[column.status]"
          :color="column.color"
          @drop="handleDrop"
        >
          <div
            v-for="idea in ideasByStatus[column.status]"
            :key="idea.id"
            class="flex items-start gap-2"
          >
            <input
              type="checkbox"
              :class="[CHECKBOX_CLASS, 'mt-4']"
              :checked="isSelected(idea.id)"
              :aria-label="$t('list.selectItem', { name: idea.title })"
              @change="toggle(idea.id)"
            />
            <IdeaCard
              :idea="idea"
              class="min-w-0 flex-1"
              @click="openEditModal(idea)"
              @delete="handleDelete(idea.id)"
            />
          </div>
        </IdeaColumn>
      </div>
    </AsyncState>

    <!-- Modal -->
    <IdeaFormModal
      :is-open="isModalOpen"
      :idea="selectedIdea"
      @close="closeModal"
      @submit="handleCreate"
      @update="handleUpdate"
      @delete="handleDelete"
    />

    <!-- AI 아이디어 생성 모달 -->
    <BaseModal
      v-model="showAiModal"
      :title="$t('ideas.aiGenerate')"
      max-width="sm"
    >
      <div class="space-y-4">
        <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('ideas.aiGenerateDesc') }}</p>
        <div>
          <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">
            {{ $t('ideas.selectCategory') }}
          </label>
          <input
            v-model="aiCategory"
            type="text"
            :placeholder="$t('ideas.selectCategory')"
            class="input-field"
          />
        </div>
        <p class="text-caption text-gray-500 dark:text-gray-400">
          {{ $t('ideas.credits') }}
        </p>
        <button
          class="btn-primary inline-flex w-full items-center justify-center gap-2"
          :disabled="aiGenerating"
          @click="handleAiGenerate"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ aiGenerating ? $t('ideas.generating') : $t('ideas.aiGenerate') }}
        </button>
      </div>
    </BaseModal>

    <!-- 아이디어 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('ideas.deleteTitle')"
      :message="$t('ideas.deleteMessage')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmDelete"
      @cancel="deleteTargetId = null"
    />

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showBulkDeleteModal"
      :title="$t('ideas.bulkDeleteTitle')"
      :message="$t('ideas.bulkDeleteMessage', { count: selectedCount })"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="handleBulkDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useIdeasStore } from '@/stores/ideas'
import { useNotificationStore } from '@/stores/notification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import { ideaApi } from '@/api/idea'
import type { ContentIdea, IdeaStatus, IdeaPriority } from '@/types/idea'
import IdeaCard from '@/components/ideas/IdeaCard.vue'
import IdeaColumn from '@/components/ideas/IdeaColumn.vue'
import IdeaFormModal from '@/components/ideas/IdeaFormModal.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  PlusIcon,
  MagnifyingGlassIcon,
  SparklesIcon,
  TrashIcon,
  LightBulbIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const ideasStore = useIdeasStore()
const notificationStore = useNotificationStore()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const isModalOpen = ref(false)
const selectedIdea = ref<ContentIdea | null>(null)
const selectedPriority = ref<IdeaPriority | 'all'>('all')
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)
const showBulkDeleteModal = ref(false)
const bulkDeleting = ref(false)

// AI 아이디어 생성
const showAiModal = ref(false)
const aiCategory = ref('')
const aiGenerating = ref(false)

const PRIORITY_WEIGHT: Record<IdeaPriority, number> = { high: 3, medium: 2, low: 1 }

const sortOptions = computed<ListSortOption<ContentIdea>[]>(() => [
  { key: 'recent', label: t('ideas.sortRecent'), accessor: 'updatedAt', kind: 'date', defaultDir: 'desc' },
  {
    key: 'priority',
    label: t('ideas.sortPriority'),
    accessor: (idea) => PRIORITY_WEIGHT[idea.priority] ?? 0,
    kind: 'number',
    defaultDir: 'desc',
  },
  { key: 'dueDate', label: t('ideas.sortDueDate'), accessor: 'dueDate', kind: 'date', defaultDir: 'asc' },
  { key: 'title', label: t('ideas.sortTitle'), accessor: 'title', kind: 'string', defaultDir: 'asc' },
])

const {
  query,
  sortKey,
  sortDir,
  filtered,
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
} = useListControls<ContentIdea>(() => ideasStore.ideas, {
  searchFields: ['title', 'description', 'tags'],
  sortOptions,
  defaultSortKey: 'recent',
  filters: computed(() =>
    selectedPriority.value === 'all'
      ? []
      : [(idea: ContentIdea) => idea.priority === selectedPriority.value],
  ),
})

const priorityFilters = computed(() => [
  { label: t('ideas.priority.all'), value: 'all' as const },
  { label: t('ideas.priority.high'), value: 'high' as const },
  { label: t('ideas.priority.medium'), value: 'medium' as const },
  { label: t('ideas.priority.low'), value: 'low' as const },
])

const columns = computed<
  { status: IdeaStatus; title: string; color: 'blue' | 'yellow' | 'green' | 'purple' }[]
>(() => [
  { status: 'idea', title: t('ideas.columns.idea'), color: 'blue' },
  { status: 'planning', title: t('ideas.columns.planning'), color: 'yellow' },
  { status: 'producing', title: t('ideas.columns.producing'), color: 'green' },
  { status: 'completed', title: t('ideas.columns.completed'), color: 'purple' },
])

/** 검색·정렬·필터가 끝난 목록을 칸반 컬럼별로 나눈다. */
const ideasByStatus = computed<Record<IdeaStatus, ContentIdea[]>>(() => ({
  idea: filtered.value.filter(idea => idea.status === 'idea'),
  planning: filtered.value.filter(idea => idea.status === 'planning'),
  producing: filtered.value.filter(idea => idea.status === 'producing'),
  completed: filtered.value.filter(idea => idea.status === 'completed'),
}))

onMounted(() => {
  ideasStore.fetchIdeas()
})

async function handleAiGenerate() {
  aiGenerating.value = true
  try {
    const category = aiCategory.value.trim() || undefined
    await ideaApi.generateAiIdeas(category)
    showAiModal.value = false
    aiCategory.value = ''
    await ideasStore.fetchIdeas()
    notificationStore.success('AI 아이디어가 생성되었습니다')
  } catch {
    notificationStore.error('AI 아이디어 생성에 실패했습니다')
  } finally {
    aiGenerating.value = false
  }
}

const setPriorityFilter = (priority: IdeaPriority | 'all') => {
  selectedPriority.value = priority
}

const resetSearchAndFilters = () => {
  resetFilters()
  selectedPriority.value = 'all'
}

const openCreateModal = () => {
  selectedIdea.value = null
  isModalOpen.value = true
}

const openEditModal = (idea: ContentIdea) => {
  selectedIdea.value = idea
  isModalOpen.value = true
}

const closeModal = () => {
  isModalOpen.value = false
  selectedIdea.value = null
}

const handleCreate = (data: Omit<ContentIdea, 'id' | 'createdAt' | 'updatedAt'>) => {
  ideasStore.addIdea(data)
}

const handleUpdate = (id: number, data: Partial<ContentIdea>) => {
  ideasStore.updateIdea(id, data)
}

const handleDelete = (id: number) => {
  deleteTargetId.value = id
  showDeleteModal.value = true
}

const confirmDelete = () => {
  const id = deleteTargetId.value
  deleteTargetId.value = null
  if (id === null) return
  ideasStore.deleteIdea(id)
}

const handleBulkDelete = async () => {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkDeleting.value = true
  try {
    await Promise.all(ids.map(id => ideasStore.deleteIdea(id)))
    notificationStore.success(t('ideas.bulkDeleteDone', { count: ids.length }))
  } finally {
    bulkDeleting.value = false
    clearSelection()
  }
}

const handleDrop = (ideaId: number, newStatus: IdeaStatus) => {
  ideasStore.moveIdea(ideaId, newStatus)
}
</script>
