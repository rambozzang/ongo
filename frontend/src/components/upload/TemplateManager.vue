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
            <button class="btn-secondary" @click="showCreateModal = true">
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
        <div v-if="filteredTemplates.length === 0" class="flex h-64 flex-col items-center justify-center text-center">
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
            v-for="template in filteredTemplates"
            :key="template.id"
            class="group relative overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 transition-all hover:border-primary-300 dark:hover:border-primary-700 hover:shadow-md"
          >
            <!-- Template Info -->
            <div class="mb-3">
              <h4 class="font-semibold text-gray-900 dark:text-gray-100">{{ template.name }}</h4>
              <p v-if="template.description" class="mt-1 text-sm text-gray-500 dark:text-gray-400 line-clamp-2">
                {{ template.description }}
              </p>
            </div>

            <!-- Tags Preview -->
            <div class="mb-3 flex flex-wrap gap-1.5">
              <span
                v-for="(tag, i) in template.tags.slice(0, 5)"
                :key="i"
                class="inline-flex items-center rounded-full bg-primary-50 dark:bg-primary-900/30 px-2 py-0.5 text-xs text-primary-700 dark:text-primary-400"
              >
                {{ tag }}
              </span>
              <span
                v-if="template.tags.length > 5"
                class="inline-flex items-center rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-xs text-gray-500 dark:text-gray-400"
              >
                +{{ template.tags.length - 5 }}
              </span>
            </div>

            <!-- Metadata -->
            <div class="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
              <span>{{ template.category || '미지정' }}</span>
              <span>•</span>
              <span>{{ formatDate(template.updatedAt) }}</span>
            </div>

            <!-- Actions -->
            <div class="mt-4 flex gap-2">
              <button
                class="flex-1 rounded-lg bg-primary-600 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700"
                @click="handleApplyTemplate(template.id)"
              >
                적용
              </button>
              <button
                class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 p-2 text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                @click="handleEditTemplate(template)"
              >
                <PencilIcon class="h-4 w-4" />
              </button>
              <button
                class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 p-2 text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                @click="handleDuplicateTemplate(template.id)"
              >
                <DocumentDuplicateIcon class="h-4 w-4" />
              </button>
              <button
                v-if="!template.id.startsWith('default-')"
                class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 p-2 text-red-600 dark:text-red-400 transition-colors hover:bg-red-50 dark:hover:bg-red-900/20"
                @click="handleDeleteTemplate(template.id)"
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
  DocumentDuplicateIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { useTemplateStore } from '@/stores/template'
import { useNotificationStore } from '@/stores/notification'
import type { MetadataTemplate } from '@/types/template'
import type { MetadataFormData } from './MetadataForm.vue'
import TemplateEditModal from './TemplateEditModal.vue'

const props = defineProps<{
  currentMetadata?: MetadataFormData
}>()

const emit = defineEmits<{
  close: []
  apply: [metadata: MetadataFormData]
}>()

const templateStore = useTemplateStore()
const notify = useNotificationStore()

const searchQuery = ref('')
const showCreateModal = ref(false)
const editingTemplate = ref<MetadataTemplate | null>(null)

onMounted(() => {
  templateStore.loadTemplates()
})

const filteredTemplates = computed(() => {
  if (!searchQuery.value.trim()) {
    return templateStore.templates
  }

  const query = searchQuery.value.toLowerCase()
  return templateStore.templates.filter((t) => {
    return (
      t.name.toLowerCase().includes(query) ||
      t.description?.toLowerCase().includes(query) ||
      t.tags.some((tag: string) => tag.toLowerCase().includes(query))
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

function handleApplyTemplate(templateId: string) {
  const metadata = templateStore.applyTemplate(templateId, props.currentMetadata?.title)
  if (metadata) {
    emit('apply', metadata)
    emit('close')
    notify.success('템플릿이 적용되었습니다')
  } else {
    notify.error('템플릿을 적용할 수 없습니다')
  }
}

function handleEditTemplate(template: MetadataTemplate) {
  editingTemplate.value = template
}

function handleDuplicateTemplate(templateId: string) {
  const duplicated = templateStore.duplicateTemplate(templateId)
  if (duplicated) {
    notify.success('템플릿이 복사되었습니다')
  } else {
    notify.error('템플릿은 최대 20개까지만 저장할 수 있습니다')
  }
}

function handleDeleteTemplate(templateId: string) {
  if (confirm('이 템플릿을 삭제하시겠습니까?')) {
    if (templateStore.deleteTemplate(templateId)) {
      notify.success('템플릿이 삭제되었습니다')
    } else {
      notify.error('템플릿 삭제에 실패했습니다')
    }
  }
}

function handleSaveCurrentMetadata() {
  if (!props.currentMetadata) return

  editingTemplate.value = {
    id: '',
    name: '',
    description: '',
    titlePattern: props.currentMetadata.title,
    descriptionTemplate: props.currentMetadata.description,
    tags: [...props.currentMetadata.tags],
    category: props.currentMetadata.category,
    visibility: props.currentMetadata.visibility,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
}

function handleSaveTemplate(data: Omit<MetadataTemplate, 'id' | 'createdAt' | 'updatedAt'>) {
  if (editingTemplate.value && editingTemplate.value.id) {
    // Update existing
    if (templateStore.updateTemplate(editingTemplate.value.id, data)) {
      notify.success('템플릿이 수정되었습니다')
      closeEditModal()
    } else {
      notify.error('템플릿 수정에 실패했습니다')
    }
  } else {
    // Create new
    const created = templateStore.createTemplate(data)
    if (created) {
      notify.success('템플릿이 생성되었습니다')
      closeEditModal()
    } else {
      notify.error('템플릿은 최대 20개까지만 저장할 수 있습니다')
    }
  }
}

function closeEditModal() {
  showCreateModal.value = false
  editingTemplate.value = null
}
</script>
