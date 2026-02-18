<template>
  <Transition name="backdrop">
    <div
      class="fixed inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm tablet:items-center"
      role="dialog"
      aria-modal="true"
      aria-labelledby="template-manager-modal-title"
      @click.self="emit('close')"
    >
      <Transition name="sheet">
        <div
          class="flex max-h-[85vh] w-full flex-col rounded-t-2xl bg-white dark:bg-gray-800 shadow-2xl tablet:max-h-[90vh] tablet:max-w-4xl tablet:rounded-2xl"
          @click.stop
          @keydown.escape="emit('close')"
        >
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-gray-200 dark:border-gray-700 px-6 py-4">
        <div>
          <h3 id="template-manager-modal-title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">메타데이터 템플릿</h3>
          <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">자주 사용하는 메타데이터를 저장하고 빠르게 적용하세요</p>
        </div>
        <button
          class="rounded-lg p-2 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          aria-label="모달 닫기"
          @click="emit('close')"
        >
          <XMarkIcon class="h-6 w-6" />
        </button>
      </div>

      <!-- Search and Actions -->
      <div class="border-b border-gray-200 dark:border-gray-700 px-6 py-4">
        <div class="flex flex-col gap-3 tablet:flex-row tablet:items-center tablet:justify-between">
          <div class="relative flex-1">
            <MagnifyingGlassIcon class="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
            <input
              v-model="searchQuery"
              type="text"
              placeholder="템플릿 검색..."
              class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 py-2 pl-10 pr-4 text-sm text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
          </div>
          <div class="flex gap-2">
            <button class="btn-secondary" @click="openCreateModal">
              <PlusIcon class="h-4 w-4" />
              <span class="hidden tablet:inline">새 템플릿</span>
            </button>
            <button
              v-if="currentMetadata"
              class="btn-primary"
              @click="handleSaveCurrentMetadata"
            >
              <DocumentPlusIcon class="h-4 w-4" />
              <span class="hidden tablet:inline">현재 메타데이터 저장</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Template List -->
      <div class="flex-1 overflow-y-auto px-6 py-4">
        <div v-if="loading" class="flex h-64 items-center justify-center">
          <div class="h-8 w-8 animate-spin rounded-full border-2 border-primary-500 border-t-transparent" />
        </div>

        <div v-else-if="filteredTemplates.length === 0" class="flex h-64 flex-col items-center justify-center text-center">
          <DocumentTextIcon class="mb-3 h-12 w-12 text-gray-300 dark:text-gray-600" />
          <p class="text-sm font-medium text-gray-500 dark:text-gray-400">
            {{ searchQuery ? '검색 결과가 없습니다' : '저장된 템플릿이 없습니다' }}
          </p>
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
            {{ searchQuery ? '다른 검색어를 입력해보세요' : '새 템플릿을 만들어보세요' }}
          </p>
        </div>

        <div v-else class="grid gap-4 tablet:grid-cols-2">
          <div
            v-for="tmpl in filteredTemplates"
            :key="tmpl.id"
            class="group relative overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 transition-all hover:border-primary-300 dark:hover:border-primary-700 hover:shadow-md"
          >
            <!-- Template Info -->
            <div class="mb-3">
              <h4 class="font-semibold text-gray-900 dark:text-gray-100">{{ tmpl.name }}</h4>
              <p v-if="tmpl.titleTemplate" class="mt-1 text-sm text-gray-500 dark:text-gray-400 line-clamp-1">
                {{ tmpl.titleTemplate }}
              </p>
            </div>

            <!-- Tags Preview -->
            <div v-if="tmpl.tags.length > 0" class="mb-3 flex flex-wrap gap-1.5">
              <span
                v-for="(tag, i) in tmpl.tags.slice(0, 5)"
                :key="i"
                class="inline-flex items-center rounded-full bg-primary-50 dark:bg-primary-900/30 px-2 py-0.5 text-xs text-primary-700 dark:text-primary-400"
              >
                {{ tag }}
              </span>
              <span
                v-if="tmpl.tags.length > 5"
                class="inline-flex items-center rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-xs text-gray-500 dark:text-gray-400"
              >
                +{{ tmpl.tags.length - 5 }}
              </span>
            </div>

            <!-- Metadata -->
            <div class="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
              <span>{{ tmpl.category || '미지정' }}</span>
              <span>•</span>
              <span>사용 {{ tmpl.usageCount }}회</span>
              <span v-if="tmpl.updatedAt">•</span>
              <span v-if="tmpl.updatedAt">{{ formatDate(tmpl.updatedAt) }}</span>
            </div>

            <!-- Actions -->
            <div class="mt-4 flex gap-2">
              <button
                class="flex-1 rounded-lg bg-primary-600 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700"
                @click="handleApplyTemplate(tmpl)"
              >
                적용
              </button>
              <button
                class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 p-2 text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                @click="handleEditTemplate(tmpl)"
              >
                <PencilIcon class="h-4 w-4" />
              </button>
              <button
                class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 p-2 text-red-600 dark:text-red-400 transition-colors hover:bg-red-50 dark:hover:bg-red-900/20"
                @click="handleDeleteTemplate(tmpl.id)"
              >
                <TrashIcon class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
        </div>
      </Transition>
    </div>
  </Transition>

  <!-- Create/Edit Modal -->
  <TemplateEditModal
    v-if="showCreateModal || editingTemplate"
    :template="editingTemplate"
    @save="handleSaveTemplate"
    @close="closeEditModal"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  XMarkIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  DocumentPlusIcon,
  DocumentTextIcon,
  PencilIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { useNotificationStore } from '@/stores/notification'
import { templatesApi } from '@/api/templates'
import type { TemplateResponse, CreateTemplateRequest } from '@/types/template'
import type { MetadataFormData } from './MetadataForm.vue'
import TemplateEditModal from './TemplateEditModal.vue'

const props = defineProps<{
  currentMetadata?: MetadataFormData
}>()

const emit = defineEmits<{
  close: []
  apply: [metadata: MetadataFormData]
}>()

const notify = useNotificationStore()

const templates = ref<TemplateResponse[]>([])
const loading = ref(false)
const searchQuery = ref('')
const showCreateModal = ref(false)
const editingTemplate = ref<TemplateResponse | null>(null)

onMounted(() => {
  fetchTemplates()
})

async function fetchTemplates() {
  loading.value = true
  try {
    const result = await templatesApi.list({ page: 0, size: 100 })
    templates.value = result.templates
  } catch {
    templates.value = []
  } finally {
    loading.value = false
  }
}

const filteredTemplates = computed(() => {
  if (!searchQuery.value.trim()) {
    return templates.value
  }

  const query = searchQuery.value.toLowerCase()
  return templates.value.filter((t) => {
    return (
      t.name.toLowerCase().includes(query) ||
      t.titleTemplate?.toLowerCase().includes(query) ||
      t.descriptionTemplate?.toLowerCase().includes(query) ||
      t.tags.some((tag) => tag.toLowerCase().includes(query))
    )
  })
})

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '방금 전'
  if (diffMins < 60) return `${diffMins}분 전`
  if (diffHours < 24) return `${diffHours}시간 전`
  if (diffDays < 7) return `${diffDays}일 전`

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(date)
}

function handleApplyTemplate(tmpl: TemplateResponse) {
  const currentTitle = props.currentMetadata?.title

  // Replace {title} placeholder
  let finalTitle = tmpl.titleTemplate || ''
  if (currentTitle) {
    finalTitle = finalTitle.replace(/{title}/g, currentTitle)
  }

  let finalDescription = tmpl.descriptionTemplate || ''
  if (currentTitle) {
    finalDescription = finalDescription.replace(/{title}/g, currentTitle)
  }

  // Record usage on server (fire-and-forget)
  templatesApi.use(tmpl.id).catch(() => {})

  emit('apply', {
    title: finalTitle,
    description: finalDescription,
    tags: [...tmpl.tags],
    category: tmpl.category || '',
    visibility: 'PUBLIC',
    thumbnailUrl: '',
  })
  emit('close')
  notify.success('템플릿이 적용되었습니다')
}

function handleEditTemplate(tmpl: TemplateResponse) {
  editingTemplate.value = tmpl
}

function openCreateModal() {
  editingTemplate.value = null
  showCreateModal.value = true
}

async function handleDeleteTemplate(id: number) {
  if (confirm('이 템플릿을 삭제하시겠습니까?')) {
    try {
      await templatesApi.delete(id)
      templates.value = templates.value.filter((t) => t.id !== id)
      notify.success('템플릿이 삭제되었습니다')
    } catch {
      notify.error('템플릿 삭제에 실패했습니다')
    }
  }
}

function handleSaveCurrentMetadata() {
  if (!props.currentMetadata) return

  editingTemplate.value = {
    id: 0,
    name: '',
    titleTemplate: props.currentMetadata.title,
    descriptionTemplate: props.currentMetadata.description,
    tags: [...props.currentMetadata.tags],
    category: props.currentMetadata.category,
    platform: null,
    usageCount: 0,
    createdAt: null,
    updatedAt: null,
  }
}

async function handleSaveTemplate(data: CreateTemplateRequest) {
  if (editingTemplate.value && editingTemplate.value.id > 0) {
    // Update existing
    try {
      await templatesApi.update(editingTemplate.value.id, {
        name: data.name,
        titleTemplate: data.titleTemplate,
        descriptionTemplate: data.descriptionTemplate,
        tags: data.tags,
        category: data.category,
      })
      notify.success('템플릿이 수정되었습니다')
      closeEditModal()
      await fetchTemplates()
    } catch {
      notify.error('템플릿 수정에 실패했습니다')
    }
  } else {
    // Create new
    try {
      await templatesApi.create(data)
      notify.success('템플릿이 생성되었습니다')
      closeEditModal()
      await fetchTemplates()
    } catch {
      notify.error('템플릿 생성에 실패했습니다')
    }
  }
}

function closeEditModal() {
  showCreateModal.value = false
  editingTemplate.value = null
}
</script>
