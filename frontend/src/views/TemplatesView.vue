<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useTemplatesStore } from '@/stores/templates'
import { useNotificationStore } from '@/stores/notification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import TemplateCard from '@/components/templates/TemplateCard.vue'
import TemplateFormModal from '@/components/templates/TemplateFormModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import {
  MagnifyingGlassIcon,
  PlusIcon,
  DocumentDuplicateIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import type { ContentTemplate, TemplateCategory } from '@/types/template'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'

const { t } = useI18n()
const templatesStore = useTemplatesStore()
const notificationStore = useNotificationStore()
const router = useRouter()
// 카테고리·즐겨찾기는 화면을 떠났다 돌아와도 유지되도록 스토어 상태를 그대로 쓴다.
// 검색어와 정렬은 useListControls가 소유한다.
const { categoryFilter } = storeToRefs(templatesStore)

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const showCreateModal = ref(false)
const editingTemplateId = ref<number | null>(null)
const showBulkDeleteModal = ref(false)
const bulkDeleting = ref(false)

onMounted(() => {
  templatesStore.loadTemplates().catch(() => undefined)
})

const categories = computed<Array<{ value: TemplateCategory | 'all'; label: string }>>(() => [
  { value: 'all', label: t('templates.catAll') },
  { value: 'title', label: t('templates.catTitle') },
  { value: 'description', label: t('templates.catDescription') },
  { value: 'tags', label: t('templates.catTags') },
  { value: 'thumbnail', label: t('templates.catThumbnail') },
  { value: 'full', label: t('templates.catFull') },
])

const sortOptions = computed<ListSortOption<ContentTemplate>[]>(() => [
  { key: 'latest', label: t('templates.sortLatest'), accessor: 'updatedAt', kind: 'date', defaultDir: 'desc' },
  { key: 'usage', label: t('templates.sortUsage'), accessor: 'usageCount', kind: 'number', defaultDir: 'desc' },
  { key: 'name', label: t('templates.sortName'), accessor: 'name', kind: 'string', defaultDir: 'asc' },
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
} = useListControls<ContentTemplate>(() => templatesStore.templates, {
  searchFields: ['name', 'titleTemplate', 'descriptionTemplate', 'tagsTemplate'],
  sortOptions,
  defaultSortKey: 'latest',
  filters: computed(() => {
    const predicates: Array<(template: ContentTemplate) => boolean> = []
    if (categoryFilter.value !== 'all') {
      predicates.push((template) => template.category === categoryFilter.value)
    }
    return predicates
  }),
})

const resetSearchAndFilters = () => {
  resetFilters()
  categoryFilter.value = 'all'
}

const handleCreateNew = () => {
  editingTemplateId.value = null
  showCreateModal.value = true
}

const handleEdit = (id: number) => {
  editingTemplateId.value = id
  showCreateModal.value = true
}

const handleApply = (id: number) => {
  void router.push({ name: 'redesign-compose', query: { templateId: String(id) } })
}

const handleCloseModal = () => {
  showCreateModal.value = false
  editingTemplateId.value = null
}

const handleBulkDelete = async () => {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkDeleting.value = true
  try {
    await Promise.all(ids.map((id) => templatesStore.deleteTemplate(id)))
    notificationStore.success(t('templates.bulkDeleteDone', { count: ids.length }))
  } finally {
    bulkDeleting.value = false
    clearSelection()
  }
}
</script>

<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <PageHeader :title="$t('templates.title')" :description="$t('templates.description')">
      <template #actions>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="handleCreateNew"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('templates.newTemplate') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('templates.pageGuideTitle')" :items="($tm('templates.pageGuide') as string[])" />

    <div v-if="templatesStore.loadError" class="flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-4 py-3 text-body-sm text-error-strong" role="alert">
      <span class="min-w-0 flex-1">{{ templatesStore.loadError }}</span>
      <button type="button" class="btn-secondary shrink-0" :disabled="templatesStore.loading" @click="templatesStore.loadTemplates().catch(() => undefined)">
        {{ $t('action.retry') }}
      </button>
    </div>

    <LoadingSpinner v-if="templatesStore.loading" class="py-12" />

    <!-- Category Tabs -->
    <div v-if="!templatesStore.loading && !templatesStore.loadError && !isSourceEmpty" class="mb-4 flex flex-wrap gap-2">
      <button
        v-for="cat in categories"
        :key="cat.value"
        type="button"
        :class="[
          'min-h-9 rounded-lg border px-3 py-2 text-body font-medium transition-colors',
          categoryFilter === cat.value
            ? 'border-accent bg-accent-dim text-accent'
            : 'border-line-control bg-surface-input text-content-secondary hover:bg-surface-raised hover:text-content',
        ]"
        @click="categoryFilter = cat.value"
      >
        {{ cat.label }}
      </button>
    </div>

    <!-- 검색 · 정렬 · 일괄 작업 -->
    <ListToolbar
      v-if="!templatesStore.loading && !templatesStore.loadError && !isSourceEmpty"
      v-model="query"
      v-model:sort-key="sortKey"
      v-model:sort-dir="sortDir"
      :sort-options="sortOptions"
      :selected-count="selectedCount"
      :total-count="visibleCount"
      :search-placeholder="$t('templates.searchPlaceholder')"
      :search-label="$t('templates.searchLabel')"
      @clear-selection="clearSelection"
    >
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
        id="templates-select-all"
        type="checkbox"
        :class="CHECKBOX_CLASS"
        :checked="allSelected"
        :indeterminate="someSelected"
        @change="toggleAll"
      />
      <label for="templates-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
        {{ $t('list.selectAll', { count: visibleCount }) }}
      </label>
    </div>

    <!-- 템플릿이 하나도 없을 때 -->
    <EmptyState
      v-if="isSourceEmpty"
      :icon="DocumentDuplicateIcon"
      :title="$t('templates.emptyTitle')"
      :description="$t('templates.emptyNoneDescription')"
      :action-label="$t('templates.createTemplate')"
      @action="handleCreateNew"
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

    <!-- Templates Grid -->
    <div v-else class="page-grid page-grid--cards">
      <div
        v-for="template in filtered"
        :key="template.id"
        class="flex items-start gap-2"
      >
        <input
          type="checkbox"
          :class="[CHECKBOX_CLASS, 'mt-5']"
          :checked="isSelected(template.id)"
          :aria-label="$t('list.selectItem', { name: template.name })"
          @change="toggle(template.id)"
        />
        <TemplateCard
          :template="template"
          class="min-w-0 flex-1"
          @edit="handleEdit"
          @apply="handleApply"
        />
      </div>
    </div>

    <!-- Create/Edit Modal -->
    <TemplateFormModal
      v-if="showCreateModal"
      :template-id="editingTemplateId"
      @close="handleCloseModal"
    />

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showBulkDeleteModal"
      :title="$t('templates.bulkDeleteTitle')"
      :message="$t('templates.bulkDeleteMessage', { count: selectedCount })"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="handleBulkDelete"
    />
  </div>
</template>
