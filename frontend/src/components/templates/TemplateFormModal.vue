<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useTemplatesStore } from '@/stores/templates'
import type { TemplateCategory, TemplatePlatform } from '@/types/template'
import TemplatePreview from './TemplatePreview.vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'

interface Props {
  templateId?: number | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  close: []
}>()

const templatesStore = useTemplatesStore()

const formData = ref({
  name: '',
  category: 'title' as TemplateCategory,
  platform: 'ALL' as TemplatePlatform,
  titleTemplate: '',
  descriptionTemplate: '',
  tagsTemplate: [] as string[],
  thumbnailStyle: '',
  isFavorite: false,
})

const tagsInput = ref('')

const isEditMode = computed(() => props.templateId !== null && props.templateId !== undefined)

const availableVariables = [
  { value: '{{video_title}}', label: '영상 제목' },
  { value: '{{date}}', label: '날짜' },
  { value: '{{channel_name}}', label: '채널명' },
  { value: '{{episode_number}}', label: '회차' },
  { value: '{{product_name}}', label: '제품명' },
  { value: '{{location}}', label: '장소' },
  { value: '{{category}}', label: '카테고리' },
  { value: '{{description}}', label: '설명' },
  { value: '{{hashtags}}', label: '해시태그' },
]

const categoryOptions: Array<{ value: TemplateCategory; label: string }> = [
  { value: 'title', label: '제목' },
  { value: 'description', label: '설명' },
  { value: 'tags', label: '태그' },
  { value: 'thumbnail', label: '썸네일' },
  { value: 'full', label: '풀 패키지' },
]

const platformOptions: Array<{ value: TemplatePlatform; label: string }> = [
  { value: 'ALL', label: '전체 플랫폼' },
  { value: 'YOUTUBE', label: '유튜브' },
  { value: 'TIKTOK', label: '틱톡' },
  { value: 'INSTAGRAM', label: '인스타그램' },
  { value: 'NAVER_CLIP', label: '네이버 클립' },
]

const showFields = computed(() => {
  const category = formData.value.category
  return {
    title: category === 'title' || category === 'full',
    description: category === 'description' || category === 'full',
    tags: category === 'tags' || category === 'full',
    thumbnail: category === 'thumbnail' || category === 'full',
  }
})

const previewContent = computed(() => {
  if (formData.value.category === 'title') return formData.value.titleTemplate
  if (formData.value.category === 'description') return formData.value.descriptionTemplate
  if (formData.value.category === 'tags') return formData.value.tagsTemplate.join(', ')
  if (formData.value.category === 'thumbnail') return formData.value.thumbnailStyle
  if (formData.value.category === 'full') {
    return `${formData.value.titleTemplate}\n\n${formData.value.descriptionTemplate}`
  }
  return ''
})

const extractedVariables = computed(() => {
  const allContent = [
    formData.value.titleTemplate,
    formData.value.descriptionTemplate,
    formData.value.tagsTemplate.join(' '),
    formData.value.thumbnailStyle,
  ].join(' ')

  const matches = allContent.match(/\{\{[^}]+\}\}/g) || []
  return [...new Set(matches)]
})

// Load template data if editing
watch(
  () => props.templateId,
  (id) => {
    if (id !== null && id !== undefined) {
      const template = templatesStore.templates.find((t) => t.id === id)
      if (template) {
        formData.value = {
          name: template.name,
          category: template.category,
          platform: template.platform,
          titleTemplate: template.titleTemplate || '',
          descriptionTemplate: template.descriptionTemplate || '',
          tagsTemplate: template.tagsTemplate || [],
          thumbnailStyle: template.thumbnailStyle || '',
          isFavorite: template.isFavorite,
        }
        tagsInput.value = template.tagsTemplate?.join(', ') || ''
      }
    }
  },
  { immediate: true }
)

const insertVariable = (variable: string, field: 'title' | 'description' | 'thumbnail') => {
  if (field === 'title') {
    formData.value.titleTemplate += variable
  } else if (field === 'description') {
    formData.value.descriptionTemplate += variable
  } else if (field === 'thumbnail') {
    formData.value.thumbnailStyle += variable
  }
}

const handleTagsInput = () => {
  if (tagsInput.value.trim()) {
    formData.value.tagsTemplate = tagsInput.value
      .split(',')
      .map((tag) => tag.trim())
      .filter((tag) => tag.length > 0)
  }
}

const handleSave = () => {
  handleTagsInput()

  if (!formData.value.name.trim()) {
    alert('템플릿 이름을 입력해주세요')
    return
  }

  const templateData = {
    name: formData.value.name,
    category: formData.value.category,
    platform: formData.value.platform,
    titleTemplate: showFields.value.title ? formData.value.titleTemplate : undefined,
    descriptionTemplate: showFields.value.description ? formData.value.descriptionTemplate : undefined,
    tagsTemplate: showFields.value.tags ? formData.value.tagsTemplate : undefined,
    thumbnailStyle: showFields.value.thumbnail ? formData.value.thumbnailStyle : undefined,
    variables: extractedVariables.value,
    isFavorite: formData.value.isFavorite,
  }

  if (isEditMode.value && props.templateId !== null && props.templateId !== undefined) {
    templatesStore.updateTemplate(props.templateId, templateData)
  } else {
    templatesStore.createTemplate(templateData)
  }

  emit('close')
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <div
    class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4"
    role="dialog"
    aria-modal="true"
    aria-labelledby="template-form-modal-title"
    @click.self="handleClose"
  >
    <div
      class="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-hidden flex flex-col"
      @keydown.escape="handleClose"
    >
      <!-- Header -->
      <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
        <h2 id="template-form-modal-title" class="text-2xl font-bold text-gray-900 dark:text-white">
          {{ isEditMode ? '템플릿 수정' : '새 템플릿 만들기' }}
        </h2>
        <button
          @click="handleClose"
          class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          aria-label="모달 닫기"
        >
          <XMarkIcon class="w-6 h-6" />
        </button>
      </div>

      <!-- Body -->
      <div class="flex-1 overflow-y-auto p-6">
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <!-- Form -->
          <div class="space-y-4">
            <!-- Name -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                템플릿 이름 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="formData.name"
                type="text"
                placeholder="예: 유튜브 브이로그 제목"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
              />
            </div>

            <!-- Category -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                카테고리 <span class="text-red-500">*</span>
              </label>
              <select
                v-model="formData.category"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
              >
                <option v-for="option in categoryOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>

            <!-- Platform -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                플랫폼
              </label>
              <select
                v-model="formData.platform"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
              >
                <option v-for="option in platformOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>

            <!-- Variable Helper -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                변수 삽입
              </label>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="variable in availableVariables"
                  :key="variable.value"
                  @click="insertVariable(variable.value, formData.category === 'thumbnail' ? 'thumbnail' : formData.category === 'description' || formData.category === 'full' ? 'description' : 'title')"
                  class="px-2 py-1 text-xs font-mono bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded hover:bg-blue-100 dark:hover:bg-blue-900/50 transition-colors"
                  :title="variable.label"
                >
                  {{ variable.value }}
                </button>
              </div>
            </div>

            <!-- Title Template -->
            <div v-if="showFields.title">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                제목 템플릿
              </label>
              <input
                v-model="formData.titleTemplate"
                type="text"
                placeholder="예: [{{date}}] {{video_title}} | {{channel_name}}"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
              />
            </div>

            <!-- Description Template -->
            <div v-if="showFields.description">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                설명 템플릿
              </label>
              <textarea
                v-model="formData.descriptionTemplate"
                rows="6"
                placeholder="영상 설명 템플릿을 입력하세요..."
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
              />
            </div>

            <!-- Tags Template -->
            <div v-if="showFields.tags">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                태그 템플릿 (쉼표로 구분)
              </label>
              <input
                v-model="tagsInput"
                @blur="handleTagsInput"
                type="text"
                placeholder="예: 뷰티, 메이크업, {{product_name}}"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
              />
            </div>

            <!-- Thumbnail Style -->
            <div v-if="showFields.thumbnail">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                썸네일 스타일
              </label>
              <textarea
                v-model="formData.thumbnailStyle"
                rows="3"
                placeholder="썸네일 디자인 가이드를 입력하세요..."
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
              />
            </div>

            <!-- Favorite -->
            <div>
              <label class="flex items-center">
                <input
                  v-model="formData.isFavorite"
                  type="checkbox"
                  class="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600"
                />
                <span class="ml-2 text-sm text-gray-700 dark:text-gray-300">즐겨찾기에 추가</span>
              </label>
            </div>
          </div>

          <!-- Preview -->
          <div class="space-y-4">
            <div>
              <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">미리보기</h3>
              <TemplatePreview
                :content="previewContent"
                :category="formData.category"
                :platform="formData.platform"
              />
            </div>

            <!-- Extracted Variables -->
            <div v-if="extractedVariables.length > 0">
              <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">사용된 변수</h4>
              <div class="flex flex-wrap gap-2">
                <span
                  v-for="variable in extractedVariables"
                  :key="variable"
                  class="px-2 py-1 text-xs font-mono bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-400 rounded"
                >
                  {{ variable }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-end gap-3 p-6 border-t border-gray-200 dark:border-gray-700">
        <button
          @click="handleClose"
          class="px-4 py-2 text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-lg font-medium transition-colors"
        >
          취소
        </button>
        <button
          @click="handleSave"
          class="px-4 py-2 bg-blue-600 hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 text-white rounded-lg font-medium transition-colors"
        >
          {{ isEditMode ? '수정' : '저장' }}
        </button>
      </div>
    </div>
  </div>
</template>
