<template>
  <Teleport to="body">
    <div
      v-if="isOpen"
      class="fixed inset-0 z-50 overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="idea-form-modal-title"
      @click.self="closeModal"
    >
      <!-- Backdrop -->
      <div class="fixed inset-0 bg-black bg-opacity-50 dark:bg-opacity-70 transition-opacity" aria-hidden="true"></div>

      <!-- Modal -->
      <div class="flex min-h-full items-center justify-center p-4">
        <div
          class="relative bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-2xl w-full p-6"
          @click.stop
          @keydown.escape="closeModal"
        >
          <!-- Header -->
          <div class="flex items-center justify-between mb-6">
            <h2 id="idea-form-modal-title" class="text-xl font-semibold text-gray-900 dark:text-gray-100">
              {{ isEditMode ? '아이디어 수정' : '새 아이디어' }}
            </h2>
            <button
              @click="closeModal"
              class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              aria-label="모달 닫기"
            >
              <XMarkIcon class="w-6 h-6" />
            </button>
          </div>

          <!-- Form -->
          <form @submit.prevent="handleSubmit" class="space-y-4">
            <!-- Title -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                제목 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="formData.title"
                type="text"
                required
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent"
                placeholder="콘텐츠 제목을 입력하세요"
              />
            </div>

            <!-- Description -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                설명
              </label>
              <textarea
                v-model="formData.description"
                rows="3"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent"
                placeholder="아이디어 설명을 입력하세요"
              ></textarea>
            </div>

            <!-- Status & Priority -->
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  상태
                </label>
                <select
                  v-model="formData.status"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent"
                >
                  <option value="idea">아이디어</option>
                  <option value="planning">기획 중</option>
                  <option value="producing">제작 중</option>
                  <option value="completed">완료</option>
                </select>
              </div>

              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  우선순위
                </label>
                <select
                  v-model="formData.priority"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent"
                >
                  <option value="low">낮음</option>
                  <option value="medium">보통</option>
                  <option value="high">높음</option>
                </select>
              </div>
            </div>

            <!-- Platforms -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                플랫폼
              </label>
              <div class="grid grid-cols-2 gap-2">
                <label class="flex items-center space-x-2 cursor-pointer">
                  <input
                    type="checkbox"
                    value="YOUTUBE"
                    v-model="formData.platform"
                    class="w-4 h-4 text-indigo-600 border-gray-300 dark:border-gray-600 rounded focus:ring-indigo-500 dark:focus:ring-indigo-400"
                  />
                  <span class="text-sm text-gray-700 dark:text-gray-300">YouTube</span>
                </label>
                <label class="flex items-center space-x-2 cursor-pointer">
                  <input
                    type="checkbox"
                    value="TIKTOK"
                    v-model="formData.platform"
                    class="w-4 h-4 text-indigo-600 border-gray-300 dark:border-gray-600 rounded focus:ring-indigo-500 dark:focus:ring-indigo-400"
                  />
                  <span class="text-sm text-gray-700 dark:text-gray-300">TikTok</span>
                </label>
                <label class="flex items-center space-x-2 cursor-pointer">
                  <input
                    type="checkbox"
                    value="INSTAGRAM"
                    v-model="formData.platform"
                    class="w-4 h-4 text-indigo-600 border-gray-300 dark:border-gray-600 rounded focus:ring-indigo-500 dark:focus:ring-indigo-400"
                  />
                  <span class="text-sm text-gray-700 dark:text-gray-300">Instagram</span>
                </label>
                <label class="flex items-center space-x-2 cursor-pointer">
                  <input
                    type="checkbox"
                    value="NAVER_CLIP"
                    v-model="formData.platform"
                    class="w-4 h-4 text-indigo-600 border-gray-300 dark:border-gray-600 rounded focus:ring-indigo-500 dark:focus:ring-indigo-400"
                  />
                  <span class="text-sm text-gray-700 dark:text-gray-300">Naver Clip</span>
                </label>
              </div>
            </div>

            <!-- Tags -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                태그
              </label>
              <input
                v-model="tagsInput"
                type="text"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent"
                placeholder="태그를 쉼표로 구분하여 입력 (예: 메이크업, 뷰티, 튜토리얼)"
              />
            </div>

            <!-- Due Date -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                마감일
              </label>
              <input
                v-model="formData.dueDate"
                type="date"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent"
              />
            </div>

            <!-- Notes -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                메모
              </label>
              <textarea
                v-model="formData.notes"
                rows="2"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent"
                placeholder="추가 메모를 입력하세요"
              ></textarea>
            </div>

            <!-- Actions -->
            <div class="flex justify-between pt-4">
              <div>
                <button
                  v-if="isEditMode"
                  type="button"
                  @click="handleDelete"
                  class="px-4 py-2 text-sm font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                >
                  삭제
                </button>
              </div>
              <div class="flex gap-2">
                <button
                  type="button"
                  @click="closeModal"
                  class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors"
                >
                  취소
                </button>
                <button
                  type="submit"
                  class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 dark:bg-indigo-500 rounded-lg hover:bg-indigo-700 dark:hover:bg-indigo-600 transition-colors"
                >
                  {{ isEditMode ? '수정' : '생성' }}
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import type { ContentIdea, IdeaStatus, IdeaPriority } from '@/types/idea'

const props = defineProps<{
  isOpen: boolean
  idea?: ContentIdea | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'submit', data: Omit<ContentIdea, 'id' | 'createdAt' | 'updatedAt'>): void
  (e: 'update', id: number, data: Partial<ContentIdea>): void
  (e: 'delete', id: number): void
}>()

const isEditMode = computed(() => !!props.idea)

const formData = ref<{
  title: string
  description: string
  status: IdeaStatus
  priority: IdeaPriority
  platform: ('YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP')[]
  dueDate: string | null
  notes: string
}>({
  title: '',
  description: '',
  status: 'idea',
  priority: 'medium',
  platform: [],
  dueDate: null,
  notes: ''
})

const tagsInput = ref('')

watch(() => props.isOpen, (isOpen) => {
  if (isOpen && props.idea) {
    formData.value = {
      title: props.idea.title,
      description: props.idea.description,
      status: props.idea.status,
      priority: props.idea.priority,
      platform: [...props.idea.platform],
      dueDate: props.idea.dueDate,
      notes: props.idea.notes
    }
    tagsInput.value = props.idea.tags.join(', ')
  } else if (isOpen && !props.idea) {
    formData.value = {
      title: '',
      description: '',
      status: 'idea',
      priority: 'medium',
      platform: [],
      dueDate: null,
      notes: ''
    }
    tagsInput.value = ''
  }
})

const closeModal = () => {
  emit('close')
}

const handleSubmit = () => {
  const tags = tagsInput.value
    .split(',')
    .map(tag => tag.trim())
    .filter(tag => tag.length > 0)

  const data = {
    ...formData.value,
    tags
  }

  if (isEditMode.value && props.idea) {
    emit('update', props.idea.id, data)
  } else {
    emit('submit', data)
  }

  closeModal()
}

const handleDelete = () => {
  if (props.idea && confirm('정말 삭제하시겠습니까?')) {
    emit('delete', props.idea.id)
    closeModal()
  }
}
</script>
