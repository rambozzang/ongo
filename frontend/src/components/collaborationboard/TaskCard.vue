<script setup lang="ts">
import { computed } from 'vue'
import {
  PaperClipIcon,
  ChatBubbleLeftIcon,
  CalendarIcon,
  ChevronRightIcon,
  ChevronLeftIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import type { BoardTask, TaskPriority, BoardColumnType } from '@/types/collaborationBoard'

interface Props {
  task: BoardTask
}

interface Emits {
  (e: 'click', id: number): void
  (e: 'delete', id: number): void
  (e: 'move', id: number, direction: 'left' | 'right'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const priorityConfig: Record<TaskPriority, { label: string; borderColor: string; badgeColor: string }> = {
  LOW: {
    label: '낮음',
    borderColor: 'border-l-gray-400',
    badgeColor: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
  },
  MEDIUM: {
    label: '보통',
    borderColor: 'border-l-blue-500',
    badgeColor: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  },
  HIGH: {
    label: '높음',
    borderColor: 'border-l-orange-500',
    badgeColor: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
  },
  URGENT: {
    label: '긴급',
    borderColor: 'border-l-red-500',
    badgeColor: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  },
}

const priority = computed(() => priorityConfig[props.task.priority] ?? priorityConfig.MEDIUM)

const isOverdue = computed(() => {
  if (!props.task.dueDate) return false
  return new Date(props.task.dueDate) < new Date()
})

const formattedDueDate = computed(() => {
  if (!props.task.dueDate) return ''
  try {
    const date = new Date(props.task.dueDate)
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
  } catch {
    return props.task.dueDate
  }
})

const columnOrder: BoardColumnType[] = ['IDEA', 'SCRIPTING', 'FILMING', 'EDITING', 'REVIEW', 'SCHEDULED', 'PUBLISHED']

const canMoveLeft = computed(() => {
  const idx = columnOrder.indexOf(props.task.column)
  return idx > 0
})

const canMoveRight = computed(() => {
  const idx = columnOrder.indexOf(props.task.column)
  return idx < columnOrder.length - 1
})

function getInitials(name: string): string {
  return name.charAt(0).toUpperCase()
}
</script>

<template>
  <div
    :class="[
      'bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 shadow-sm hover:shadow-md transition-all duration-200 cursor-pointer border-l-4',
      priority.borderColor,
    ]"
    @click="emit('click', task.id)"
  >
    <div class="p-3">
      <!-- Priority Badge & Move Buttons -->
      <div class="flex items-center justify-between mb-2">
        <span
          :class="['inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium', priority.badgeColor]"
        >
          {{ priority.label }}
        </span>
        <div class="flex items-center gap-0.5" @click.stop>
          <button
            v-if="canMoveLeft"
            @click="emit('move', task.id, 'left')"
            class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            title="이전 단계로 이동"
          >
            <ChevronLeftIcon class="w-3.5 h-3.5" />
          </button>
          <button
            v-if="canMoveRight"
            @click="emit('move', task.id, 'right')"
            class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            title="다음 단계로 이동"
          >
            <ChevronRightIcon class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <!-- Title -->
      <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2 line-clamp-2">
        {{ task.title }}
      </h4>

      <!-- Tags -->
      <div v-if="task.tags.length > 0" class="flex flex-wrap gap-1 mb-2">
        <span
          v-for="tag in task.tags.slice(0, 3)"
          :key="tag"
          class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          {{ tag }}
        </span>
        <span
          v-if="task.tags.length > 3"
          class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          +{{ task.tags.length - 3 }}
        </span>
      </div>

      <!-- Bottom Row: Assignee, Due Date, Attachment/Comment Counts -->
      <div class="flex items-center justify-between mt-2 pt-2 border-t border-gray-100 dark:border-gray-700">
        <div class="flex items-center gap-2">
          <!-- Assignee Avatar -->
          <div
            v-if="task.assigneeName"
            class="w-6 h-6 rounded-full bg-primary-100 dark:bg-primary-900/30 flex items-center justify-center"
            :title="task.assigneeName"
          >
            <span class="text-[10px] font-semibold text-primary-700 dark:text-primary-300">
              {{ getInitials(task.assigneeName) }}
            </span>
          </div>

          <!-- Due Date -->
          <div
            v-if="task.dueDate"
            :class="[
              'flex items-center gap-1 text-[11px]',
              isOverdue ? 'text-red-600 dark:text-red-400 font-medium' : 'text-gray-500 dark:text-gray-400',
            ]"
          >
            <CalendarIcon class="w-3 h-3" />
            <span>{{ formattedDueDate }}</span>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <!-- Attachments -->
          <div v-if="task.attachments > 0" class="flex items-center gap-0.5 text-[11px] text-gray-400 dark:text-gray-500">
            <PaperClipIcon class="w-3 h-3" />
            <span>{{ task.attachments }}</span>
          </div>

          <!-- Comments -->
          <div v-if="task.comments > 0" class="flex items-center gap-0.5 text-[11px] text-gray-400 dark:text-gray-500">
            <ChatBubbleLeftIcon class="w-3 h-3" />
            <span>{{ task.comments }}</span>
          </div>

          <!-- Delete -->
          <button
            @click.stop="emit('delete', task.id)"
            class="p-1 rounded text-gray-300 dark:text-gray-600 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors opacity-0 group-hover:opacity-100"
          >
            <TrashIcon class="w-3 h-3" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
