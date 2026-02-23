<script setup lang="ts">
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useTemplatesStore } from '@/stores/templates'
import TemplateCard from '@/components/templates/TemplateCard.vue'
import TemplateFormModal from '@/components/templates/TemplateFormModal.vue'
import {
  MagnifyingGlassIcon,
  PlusIcon,
  AdjustmentsHorizontalIcon,
  StarIcon,
} from '@heroicons/vue/24/outline'
import type { TemplateCategory } from '@/types/template'
import PageGuide from '@/components/common/PageGuide.vue'

const { t } = useI18n()
const templatesStore = useTemplatesStore()
const { filteredTemplates, searchText, categoryFilter, sortBy, showFavoritesOnly } =
  storeToRefs(templatesStore)

const showCreateModal = ref(false)
const editingTemplateId = ref<number | null>(null)

const categories = computed<Array<{ value: TemplateCategory | 'all'; label: string }>>(() => [
  { value: 'all', label: t('templates.catAll') },
  { value: 'title', label: t('templates.catTitle') },
  { value: 'description', label: t('templates.catDescription') },
  { value: 'tags', label: t('templates.catTags') },
  { value: 'thumbnail', label: t('templates.catThumbnail') },
  { value: 'full', label: t('templates.catFull') },
])

const sortOptions = computed<Array<{ value: 'latest' | 'usage' | 'name'; label: string }>>(() => [
  { value: 'latest', label: t('templates.sortLatest') },
  { value: 'usage', label: t('templates.sortUsage') },
  { value: 'name', label: t('templates.sortName') },
])

const handleCreateNew = () => {
  editingTemplateId.value = null
  showCreateModal.value = true
}

const handleEdit = (id: number) => {
  editingTemplateId.value = id
  showCreateModal.value = true
}

const handleCloseModal = () => {
  showCreateModal.value = false
  editingTemplateId.value = null
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('templates.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('templates.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="handleCreateNew"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('templates.newTemplate') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('templates.pageGuideTitle')" :items="($tm('templates.pageGuide') as string[])" />

    <!-- Filters and Search -->
    <div class="card mb-6 space-y-4">
      <!-- Category Tabs -->
      <div class="flex flex-wrap gap-2">
        <button
          v-for="cat in categories"
          :key="cat.value"
          @click="categoryFilter = cat.value"
          :class="[
            'px-3 py-1.5 rounded-lg font-medium text-xs transition-colors',
            categoryFilter === cat.value
              ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
              : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600',
          ]"
        >
          {{ cat.label }}
        </button>
      </div>

      <!-- Search and Controls -->
      <div class="flex flex-col gap-3 tablet:flex-row">
        <!-- Search -->
        <div class="relative flex-1">
          <MagnifyingGlassIcon
            class="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400"
          />
          <input
            v-model="searchText"
            type="text"
            :placeholder="$t('templates.searchPlaceholder')"
            class="input w-full pl-10"
          />
        </div>

        <!-- Sort -->
        <div class="flex items-center gap-2">
          <AdjustmentsHorizontalIcon class="h-5 w-5 text-gray-500 dark:text-gray-400" />
          <select
            v-model="sortBy"
            class="input text-sm"
          >
            <option v-for="option in sortOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <!-- Favorites Toggle -->
        <button
          @click="showFavoritesOnly = !showFavoritesOnly"
          :class="[
            'inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors',
            showFavoritesOnly
              ? 'border-amber-500 bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400'
              : 'border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700',
          ]"
        >
          <StarIcon
            :class="[
              'h-4 w-4',
              showFavoritesOnly ? 'fill-yellow-500 text-yellow-500' : '',
            ]"
          />
          {{ $t('templates.favorites') }}
        </button>
      </div>
    </div>

    <!-- Templates Grid -->
    <div
      v-if="filteredTemplates.length > 0"
      class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3"
    >
      <TemplateCard
        v-for="template in filteredTemplates"
        :key="template.id"
        :template="template"
        @edit="handleEdit"
      />
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="card p-12 text-center"
    >
      <div class="mx-auto max-w-md">
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <AdjustmentsHorizontalIcon class="h-8 w-8 text-gray-400" />
        </div>
        <h3 class="mb-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('templates.emptyTitle') }}
        </h3>
        <p class="mb-6 text-gray-600 dark:text-gray-400">
          {{ $t('templates.emptyDescription') }}
        </p>
        <button
          @click="handleCreateNew"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('templates.createTemplate') }}
        </button>
      </div>
    </div>

    <!-- Create/Edit Modal -->
    <TemplateFormModal
      v-if="showCreateModal"
      :template-id="editingTemplateId"
      @close="handleCloseModal"
    />
  </div>
</template>
