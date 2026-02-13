<script setup lang="ts">
import { ref } from 'vue'
import { useTemplatesStore } from '@/stores/templates'
import type { ContentTemplate } from '@/types/template'
import {
  StarIcon,
  DocumentDuplicateIcon,
  PencilIcon,
  TrashIcon,
  CheckIcon,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'

interface Props {
  template: ContentTemplate
}

const props = defineProps<Props>()
const emit = defineEmits<{
  edit: [id: number]
}>()

const templatesStore = useTemplatesStore()
const showHover = ref(false)
const showDeleteConfirm = ref(false)

const categoryLabels: Record<string, string> = {
  title: '제목',
  description: '설명',
  tags: '태그',
  thumbnail: '썸네일',
  full: '풀 패키지',
}

const platformLabels: Record<string, string> = {
  YOUTUBE: '유튜브',
  TIKTOK: '틱톡',
  INSTAGRAM: '인스타그램',
  NAVER_CLIP: '네이버클립',
  ALL: '전체',
}

const categoryColors: Record<string, string> = {
  title: 'bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300',
  description: 'bg-green-100 dark:bg-green-900 text-green-700 dark:text-green-300',
  tags: 'bg-purple-100 dark:bg-purple-900 text-purple-700 dark:text-purple-300',
  thumbnail: 'bg-orange-100 dark:bg-orange-900 text-orange-700 dark:text-orange-300',
  full: 'bg-pink-100 dark:bg-pink-900 text-pink-700 dark:text-pink-300',
}

const getPreviewContent = () => {
  if (props.template.titleTemplate) return props.template.titleTemplate
  if (props.template.descriptionTemplate) {
    return props.template.descriptionTemplate.split('\n').slice(0, 2).join(' ')
  }
  if (props.template.tagsTemplate && props.template.tagsTemplate.length > 0) {
    return props.template.tagsTemplate.slice(0, 5).join(', ')
  }
  if (props.template.thumbnailStyle) return props.template.thumbnailStyle
  return ''
}

const handleToggleFavorite = () => {
  templatesStore.toggleFavorite(props.template.id)
}

const handleDuplicate = () => {
  templatesStore.duplicateTemplate(props.template.id)
}

const handleEdit = () => {
  emit('edit', props.template.id)
}

const handleDelete = () => {
  if (showDeleteConfirm.value) {
    templatesStore.deleteTemplate(props.template.id)
    showDeleteConfirm.value = false
  } else {
    showDeleteConfirm.value = true
  }
}

const handleApply = () => {
  templatesStore.applyTemplate(props.template.id)
  // TODO: Navigate to video upload or show success message
}
</script>

<template>
  <div
    @mouseenter="showHover = true"
    @mouseleave="showHover = false; showDeleteConfirm = false"
    class="relative bg-white dark:bg-gray-800 rounded-lg shadow-sm hover:shadow-md transition-shadow p-5 border border-gray-200 dark:border-gray-700"
  >
    <!-- Hover Overlay -->
    <div
      v-if="showHover"
      class="absolute inset-0 bg-blue-600 dark:bg-blue-500 bg-opacity-90 rounded-lg flex items-center justify-center z-10 transition-opacity"
    >
      <button
        @click="handleApply"
        class="px-6 py-3 bg-white dark:bg-gray-800 text-blue-600 dark:text-blue-400 font-semibold rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
      >
        <CheckIcon class="w-5 h-5 inline-block mr-2" />
        적용
      </button>
    </div>

    <!-- Card Content -->
    <div class="relative">
      <!-- Header -->
      <div class="flex items-start justify-between mb-3">
        <div class="flex-1 min-w-0">
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white truncate">
            {{ template.name }}
          </h3>
        </div>
        <button
          @click="handleToggleFavorite"
          class="ml-2 p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-colors"
        >
          <StarIconSolid
            v-if="template.isFavorite"
            class="w-5 h-5 text-yellow-500"
          />
          <StarIcon
            v-else
            class="w-5 h-5 text-gray-400 dark:text-gray-500"
          />
        </button>
      </div>

      <!-- Badges -->
      <div class="flex flex-wrap gap-2 mb-3">
        <span
          :class="[
            'px-2 py-1 text-xs font-medium rounded',
            categoryColors[template.category],
          ]"
        >
          {{ categoryLabels[template.category] }}
        </span>
        <span
          class="px-2 py-1 text-xs font-medium rounded bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300"
        >
          {{ platformLabels[template.platform] }}
        </span>
      </div>

      <!-- Preview -->
      <div class="mb-3">
        <p class="text-sm text-gray-600 dark:text-gray-400 line-clamp-2">
          {{ getPreviewContent() }}
        </p>
      </div>

      <!-- Variables -->
      <div v-if="template.variables.length > 0" class="mb-3">
        <div class="flex flex-wrap gap-1">
          <span
            v-for="(variable, index) in template.variables.slice(0, 4)"
            :key="index"
            class="px-2 py-0.5 text-xs font-mono bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded"
          >
            {{ variable }}
          </span>
          <span
            v-if="template.variables.length > 4"
            class="px-2 py-0.5 text-xs text-gray-500 dark:text-gray-400"
          >
            +{{ template.variables.length - 4 }}
          </span>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-between pt-3 border-t border-gray-200 dark:border-gray-700">
        <span class="text-sm text-gray-500 dark:text-gray-400">
          사용 {{ template.usageCount }}회
        </span>

        <!-- Actions -->
        <div class="flex items-center gap-1">
          <button
            @click="handleDuplicate"
            class="p-1.5 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-colors"
            title="복사"
          >
            <DocumentDuplicateIcon class="w-4 h-4" />
          </button>
          <button
            @click="handleEdit"
            class="p-1.5 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-colors"
            title="수정"
          >
            <PencilIcon class="w-4 h-4" />
          </button>
          <button
            @click="handleDelete"
            :class="[
              'p-1.5 rounded transition-colors',
              showDeleteConfirm
                ? 'bg-red-100 dark:bg-red-900 text-red-600 dark:text-red-400'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700',
            ]"
            :title="showDeleteConfirm ? '다시 클릭하여 확인' : '삭제'"
          >
            <TrashIcon class="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
