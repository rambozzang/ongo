<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  XMarkIcon,
  PlusCircleIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'
import type { BoardColumnType, TaskPriority, CreateBoardTaskRequest } from '@/types/collaborationBoard'

interface Props {
  visible: boolean
  initialColumn?: BoardColumnType
}

interface Emits {
  (e: 'close'): void
  (e: 'create', request: CreateBoardTaskRequest): void
}

const props = withDefaults(defineProps<Props>(), {
  initialColumn: 'IDEA',
})
const emit = defineEmits<Emits>()

const title = ref('')
const description = ref('')
const column = ref<BoardColumnType>(props.initialColumn)
const priority = ref<TaskPriority>('MEDIUM')
const assigneeName = ref('')
const dueDate = ref('')
const tagInput = ref('')
const tags = ref<string[]>([])

const columnOptions: { value: BoardColumnType; label: string }[] = [
  { value: 'IDEA', label: '아이디어' },
  { value: 'SCRIPTING', label: '대본 작성' },
  { value: 'FILMING', label: '촬영' },
  { value: 'EDITING', label: '편집' },
  { value: 'REVIEW', label: '검토' },
  { value: 'SCHEDULED', label: '예약됨' },
  { value: 'PUBLISHED', label: '발행 완료' },
]

const priorityOptions: { value: TaskPriority; label: string; color: string }[] = [
  { value: 'LOW', label: '낮음', color: 'text-gray-500' },
  { value: 'MEDIUM', label: '보통', color: 'text-blue-500' },
  { value: 'HIGH', label: '높음', color: 'text-orange-500' },
  { value: 'URGENT', label: '긴급', color: 'text-red-500' },
]

const isValid = computed(() => title.value.trim().length > 0)

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !tags.value.includes(tag)) {
    tags.value.push(tag)
  }
  tagInput.value = ''
}

function removeTag(tag: string) {
  tags.value = tags.value.filter((t) => t !== tag)
}

function handleCreate() {
  if (!isValid.value) return
  const request: CreateBoardTaskRequest = {
    title: title.value.trim(),
    description: description.value.trim() || undefined,
    column: column.value,
    priority: priority.value,
    dueDate: dueDate.value || undefined,
    tags: tags.value.length > 0 ? tags.value : undefined,
  }
  emit('create', request)
  resetForm()
}

function resetForm() {
  title.value = ''
  description.value = ''
  column.value = props.initialColumn
  priority.value = 'MEDIUM'
  assigneeName.value = ''
  dueDate.value = ''
  tagInput.value = ''
  tags.value = []
}

function handleClose() {
  resetForm()
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-200"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="visible"
        class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
        @click="handleClose"
      >
        <Transition
          enter-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          enter-to-class="opacity-100 scale-100"
          leave-active-class="transition-all duration-200"
          leave-from-class="opacity-100 scale-100"
          leave-to-class="opacity-0 scale-95"
        >
          <div
            v-if="visible"
            class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col"
            @click.stop
          >
            <!-- Modal Header -->
            <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
              <div class="flex items-center gap-2">
                <PlusCircleIcon class="w-5 h-5 text-primary-600 dark:text-primary-400" />
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  새 작업 추가
                </h2>
              </div>
              <button
                @click="handleClose"
                class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                <XMarkIcon class="w-5 h-5" />
              </button>
            </div>

            <!-- Modal Body -->
            <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
              <!-- Title -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  제목 <span class="text-red-500">*</span>
                </label>
                <input
                  v-model="title"
                  type="text"
                  placeholder="작업 제목을 입력하세요"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                />
              </div>

              <!-- Description -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  설명
                </label>
                <textarea
                  v-model="description"
                  rows="3"
                  placeholder="작업에 대한 상세 설명을 입력하세요"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400 resize-none"
                />
              </div>

              <!-- Column & Priority Row -->
              <div class="grid grid-cols-2 gap-4">
                <!-- Column -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    단계
                  </label>
                  <select
                    v-model="column"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  >
                    <option
                      v-for="opt in columnOptions"
                      :key="opt.value"
                      :value="opt.value"
                    >
                      {{ opt.label }}
                    </option>
                  </select>
                </div>

                <!-- Priority -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    우선순위
                  </label>
                  <select
                    v-model="priority"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  >
                    <option
                      v-for="opt in priorityOptions"
                      :key="opt.value"
                      :value="opt.value"
                    >
                      {{ opt.label }}
                    </option>
                  </select>
                </div>
              </div>

              <!-- Assignee -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  담당자
                </label>
                <input
                  v-model="assigneeName"
                  type="text"
                  placeholder="담당자 이름"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                />
              </div>

              <!-- Due Date -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  마감일
                </label>
                <input
                  v-model="dueDate"
                  type="date"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                />
              </div>

              <!-- Tags -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  태그
                </label>
                <div class="flex items-center gap-2">
                  <div class="relative flex-1">
                    <TagIcon class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                    <input
                      v-model="tagInput"
                      type="text"
                      placeholder="태그 입력 후 Enter"
                      class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 pl-9 pr-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                      @keydown.enter.prevent="addTag"
                    />
                  </div>
                  <button
                    type="button"
                    @click="addTag"
                    class="px-3 py-2 text-sm rounded-lg border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                  >
                    추가
                  </button>
                </div>
                <div v-if="tags.length > 0" class="flex flex-wrap gap-1.5 mt-2">
                  <span
                    v-for="tag in tags"
                    :key="tag"
                    class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                  >
                    {{ tag }}
                    <button @click="removeTag(tag)" class="hover:text-primary-900 dark:hover:text-primary-100">
                      <XMarkIcon class="w-3 h-3" />
                    </button>
                  </span>
                </div>
              </div>
            </div>

            <!-- Modal Footer -->
            <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
              <button
                @click="handleClose"
                class="px-4 py-2 text-sm font-medium rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                취소
              </button>
              <button
                @click="handleCreate"
                :disabled="!isValid"
                class="btn-primary"
              >
                작업 추가
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
