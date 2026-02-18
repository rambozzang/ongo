<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
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

const templatesStore = useTemplatesStore()
const { filteredTemplates, searchText, categoryFilter, sortBy, showFavoritesOnly } =
  storeToRefs(templatesStore)

const showCreateModal = ref(false)
const editingTemplateId = ref<number | null>(null)

const categories: Array<{ value: TemplateCategory | 'all'; label: string }> = [
  { value: 'all', label: '전체' },
  { value: 'title', label: '제목' },
  { value: 'description', label: '설명' },
  { value: 'tags', label: '태그' },
  { value: 'thumbnail', label: '썸네일' },
  { value: 'full', label: '풀 패키지' },
]

const sortOptions: Array<{ value: 'latest' | 'usage' | 'name'; label: string }> = [
  { value: 'latest', label: '최신순' },
  { value: 'usage', label: '사용순' },
  { value: 'name', label: '이름순' },
]

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
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-4 sm:p-6 lg:p-8">
    <div class="max-w-7xl mx-auto">
      <!-- Header -->
      <div class="mb-8">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 class="text-3xl font-bold text-gray-900 dark:text-white">콘텐츠 템플릿</h1>
            <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">
              자주 사용하는 메타데이터 템플릿을 저장하고 빠르게 적용하세요
            </p>
          </div>
          <button
            @click="handleCreateNew"
            class="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 text-white font-medium rounded-lg transition-colors"
          >
            <PlusIcon class="w-5 h-5 mr-2" />
            새 템플릿
          </button>
        </div>
      </div>

      <PageGuide title="템플릿" :items="[
        '새 템플릿 버튼으로 제목·설명·태그·썸네일 등 메타데이터 조합을 템플릿으로 저장하세요',
        '카테고리 탭(제목/설명/태그/썸네일/풀패키지)으로 원하는 유형의 템플릿만 필터링하세요',
        '검색과 정렬(최신순/사용순/이름순)으로 템플릿을 빠르게 찾을 수 있습니다',
        '영상 업로드 시 저장된 템플릿을 적용하면 메타데이터 입력 시간을 크게 단축할 수 있습니다',
        '자주 사용하는 템플릿에 즐겨찾기를 설정하면 상단에 우선 표시됩니다',
      ]" />

      <!-- Filters and Search -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 mb-6">
        <!-- Category Tabs -->
        <div class="flex flex-wrap gap-2 mb-4">
          <button
            v-for="cat in categories"
            :key="cat.value"
            @click="categoryFilter = cat.value"
            :class="[
              'px-4 py-2 rounded-lg font-medium text-sm transition-colors',
              categoryFilter === cat.value
                ? 'bg-blue-600 dark:bg-blue-500 text-white'
                : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600',
            ]"
          >
            {{ cat.label }}
          </button>
        </div>

        <!-- Search and Controls -->
        <div class="flex flex-col sm:flex-row gap-4">
          <!-- Search -->
          <div class="flex-1 relative">
            <MagnifyingGlassIcon
              class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400"
            />
            <input
              v-model="searchText"
              type="text"
              placeholder="템플릿 검색..."
              class="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
            />
          </div>

          <!-- Sort -->
          <div class="flex items-center gap-2">
            <AdjustmentsHorizontalIcon class="w-5 h-5 text-gray-500 dark:text-gray-400" />
            <select
              v-model="sortBy"
              class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
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
              'inline-flex items-center px-4 py-2 rounded-lg font-medium text-sm transition-colors',
              showFavoritesOnly
                ? 'bg-yellow-100 dark:bg-yellow-900 text-yellow-700 dark:text-yellow-300'
                : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600',
            ]"
          >
            <StarIcon
              :class="[
                'w-5 h-5 mr-2',
                showFavoritesOnly ? 'fill-yellow-500 text-yellow-500' : '',
              ]"
            />
            즐겨찾기
          </button>
        </div>
      </div>

      <!-- Templates Grid -->
      <div
        v-if="filteredTemplates.length > 0"
        class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
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
        class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-12 text-center"
      >
        <div class="max-w-md mx-auto">
          <div class="w-16 h-16 bg-gray-100 dark:bg-gray-700 rounded-full flex items-center justify-center mx-auto mb-4">
            <AdjustmentsHorizontalIcon class="w-8 h-8 text-gray-400" />
          </div>
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-2">
            템플릿이 없습니다
          </h3>
          <p class="text-gray-600 dark:text-gray-400 mb-6">
            검색 조건을 변경하거나 새 템플릿을 만들어보세요
          </p>
          <button
            @click="handleCreateNew"
            class="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 text-white font-medium rounded-lg transition-colors"
          >
            <PlusIcon class="w-5 h-5 mr-2" />
            템플릿 만들기
          </button>
        </div>
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
