<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  PlusIcon,
  ViewColumnsIcon,
  ClockIcon,
  ExclamationTriangleIcon,
  CheckCircleIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import BoardColumnView from '@/components/collaborationboard/BoardColumnView.vue'
import TaskCreateModal from '@/components/collaborationboard/TaskCreateModal.vue'
import { useCollaborationBoardStore } from '@/stores/collaborationBoard'
import type { BoardColumnType, BoardTask, CreateBoardTaskRequest } from '@/types/collaborationBoard'

const store = useCollaborationBoardStore()
const { columns, summary, isLoading, totalTasks } = storeToRefs(store)

const showCreateModal = ref(false)
const showDetailModal = ref(false)
const selectedTask = ref<BoardTask | null>(null)
const createColumnTarget = ref<BoardColumnType>('IDEA')

const columnOrder: BoardColumnType[] = ['IDEA', 'SCRIPTING', 'FILMING', 'EDITING', 'REVIEW', 'SCHEDULED', 'PUBLISHED']

onMounted(() => {
  store.fetchBoard()
  store.fetchSummary()
})

function openCreateModal(columnType?: string) {
  createColumnTarget.value = (columnType as BoardColumnType) || 'IDEA'
  showCreateModal.value = true
}

async function handleCreateTask(request: CreateBoardTaskRequest) {
  await store.createTask(request)
  showCreateModal.value = false
}

function handleClickTask(id: number) {
  for (const col of columns.value) {
    const task = col.tasks.find((t) => t.id === id)
    if (task) {
      selectedTask.value = task
      showDetailModal.value = true
      break
    }
  }
}

function handleDeleteTask(id: number) {
  if (confirm('이 작업을 삭제하시겠습니까?')) {
    store.deleteTask(id)
    if (selectedTask.value?.id === id) {
      showDetailModal.value = false
      selectedTask.value = null
    }
  }
}

function handleMoveTask(id: number, direction: 'left' | 'right') {
  // 현재 task가 어느 컬럼에 있는지 찾기
  for (const col of columns.value) {
    const task = col.tasks.find((t) => t.id === id)
    if (task) {
      const currentIdx = columnOrder.indexOf(task.column)
      const targetIdx = direction === 'left' ? currentIdx - 1 : currentIdx + 1
      if (targetIdx >= 0 && targetIdx < columnOrder.length) {
        store.moveTask(id, columnOrder[targetIdx])
      }
      break
    }
  }
}

const priorityConfig: Record<string, { label: string; color: string }> = {
  LOW: { label: '낮음', color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' },
  MEDIUM: { label: '보통', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300' },
  HIGH: { label: '높음', color: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300' },
  URGENT: { label: '긴급', color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300' },
}

function formatDate(dateStr: string): string {
  try {
    return new Date(dateStr).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return dateStr
  }
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            협업 보드
          </h1>
          <ViewColumnsIcon class="w-6 h-6 text-primary-600 dark:text-primary-400" />
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          콘텐츠 제작 워크플로우를 칸반 보드로 관리하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="openCreateModal()"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          작업 추가
        </button>
      </div>
    </div>

    <!-- Summary Stats -->
    <div
      v-if="summary"
      class="grid grid-cols-2 tablet:grid-cols-4 gap-4 mb-6"
    >
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <div class="flex items-center gap-2 mb-1">
          <ViewColumnsIcon class="w-4 h-4 text-gray-400" />
          <p class="text-sm text-gray-500 dark:text-gray-400">전체 작업</p>
        </div>
        <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ summary.totalTasks }}</p>
      </div>
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <div class="flex items-center gap-2 mb-1">
          <CheckCircleIcon class="w-4 h-4 text-green-500" />
          <p class="text-sm text-gray-500 dark:text-gray-400">완료됨</p>
        </div>
        <p class="text-2xl font-bold text-green-600 dark:text-green-400">{{ summary.completedTasks }}</p>
      </div>
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <div class="flex items-center gap-2 mb-1">
          <ExclamationTriangleIcon class="w-4 h-4 text-red-500" />
          <p class="text-sm text-gray-500 dark:text-gray-400">기한 초과</p>
        </div>
        <p class="text-2xl font-bold text-red-600 dark:text-red-400">{{ summary.overdueTasks }}</p>
      </div>
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <div class="flex items-center gap-2 mb-1">
          <ClockIcon class="w-4 h-4 text-blue-500" />
          <p class="text-sm text-gray-500 dark:text-gray-400">진행 중</p>
        </div>
        <p class="text-2xl font-bold text-blue-600 dark:text-blue-400">{{ summary.inProgressTasks }}</p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Kanban Board -->
    <div v-else-if="columns.length > 0" class="overflow-x-auto pb-4 -mx-2 px-2">
      <div class="flex gap-4 min-w-max">
        <BoardColumnView
          v-for="col in columns"
          :key="col.type"
          :column="col"
          @add-task="openCreateModal"
          @click-task="handleClickTask"
          @delete-task="handleDeleteTask"
          @move-task="handleMoveTask"
        />
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
    >
      <ViewColumnsIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
      <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        보드가 비어 있습니다
      </h3>
      <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
        첫 번째 작업을 추가하여 콘텐츠 제작 워크플로우를 시작하세요
      </p>
      <button @click="openCreateModal()" class="btn-primary inline-flex items-center gap-2">
        <PlusIcon class="w-5 h-5" />
        작업 추가하기
      </button>
    </div>

    <!-- Create Task Modal -->
    <TaskCreateModal
      :visible="showCreateModal"
      :initial-column="createColumnTarget"
      @close="showCreateModal = false"
      @create="handleCreateTask"
    />

    <!-- Task Detail Modal -->
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
          v-if="showDetailModal && selectedTask"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showDetailModal = false"
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
              v-if="showDetailModal && selectedTask"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Detail Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 truncate flex-1 mr-3">
                  {{ selectedTask.title }}
                </h2>
                <button
                  @click="showDetailModal = false"
                  class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <XMarkIcon class="w-5 h-5" />
                </button>
              </div>

              <!-- Detail Body -->
              <div class="flex-1 overflow-y-auto px-6 py-6 space-y-4">
                <!-- Priority & Status -->
                <div class="flex items-center gap-2">
                  <span
                    :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', priorityConfig[selectedTask.priority]?.color]"
                  >
                    {{ priorityConfig[selectedTask.priority]?.label }}
                  </span>
                  <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
                    {{ columns.find(c => c.type === selectedTask.column)?.label ?? selectedTask.column }}
                  </span>
                </div>

                <!-- Description -->
                <div v-if="selectedTask.description">
                  <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">설명</h3>
                  <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
                    {{ selectedTask.description }}
                  </p>
                </div>

                <!-- Details Grid -->
                <div class="grid grid-cols-2 gap-4">
                  <div v-if="selectedTask.assigneeName">
                    <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">담당자</h3>
                    <div class="flex items-center gap-2">
                      <div class="w-6 h-6 rounded-full bg-primary-100 dark:bg-primary-900/30 flex items-center justify-center">
                        <span class="text-[10px] font-semibold text-primary-700 dark:text-primary-300">
                          {{ selectedTask.assigneeName.charAt(0) }}
                        </span>
                      </div>
                      <span class="text-sm text-gray-900 dark:text-gray-100">{{ selectedTask.assigneeName }}</span>
                    </div>
                  </div>

                  <div v-if="selectedTask.dueDate">
                    <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">마감일</h3>
                    <p class="text-sm text-gray-900 dark:text-gray-100">
                      {{ formatDate(selectedTask.dueDate) }}
                    </p>
                  </div>
                </div>

                <!-- Tags -->
                <div v-if="selectedTask.tags.length > 0">
                  <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">태그</h3>
                  <div class="flex flex-wrap gap-1.5">
                    <span
                      v-for="tag in selectedTask.tags"
                      :key="tag"
                      class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
                    >
                      {{ tag }}
                    </span>
                  </div>
                </div>

                <!-- Counts -->
                <div class="flex items-center gap-6 text-sm text-gray-500 dark:text-gray-400">
                  <span>첨부파일 {{ selectedTask.attachments }}개</span>
                  <span>댓글 {{ selectedTask.comments }}개</span>
                </div>
              </div>

              <!-- Detail Footer -->
              <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
                <button
                  @click="handleDeleteTask(selectedTask!.id)"
                  class="inline-flex items-center gap-1 px-4 py-2 text-sm font-medium rounded-lg text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                >
                  삭제
                </button>
                <button
                  @click="showDetailModal = false"
                  class="btn-primary"
                >
                  닫기
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
