<script setup lang="ts">
import {
  PlusIcon,
} from '@heroicons/vue/24/outline'
import TaskCard from './TaskCard.vue'
import type { BoardColumn } from '@/types/collaborationBoard'

interface Props {
  column: BoardColumn
}

interface Emits {
  (e: 'add-task', columnType: string): void
  (e: 'click-task', id: number): void
  (e: 'delete-task', id: number): void
  (e: 'move-task', id: number, direction: 'left' | 'right'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()
</script>

<template>
  <div class="flex flex-col w-72 min-w-[18rem] shrink-0 bg-gray-50/80 dark:bg-gray-900/50 rounded-xl border border-gray-200 dark:border-gray-700">
    <!-- Column Header -->
    <div class="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <div
            class="w-3 h-3 rounded-full"
            :style="{ backgroundColor: column.color }"
          />
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ column.label }}
          </h3>
          <span class="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full text-xs font-medium bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
            {{ column.tasks.length }}
          </span>
        </div>
      </div>
      <!-- WIP Limit indicator -->
      <div
        v-if="column.taskLimit && column.tasks.length >= column.taskLimit"
        class="mt-1 text-[10px] text-amber-600 dark:text-amber-400 font-medium"
      >
        WIP 제한 도달 ({{ column.taskLimit }})
      </div>
    </div>

    <!-- Scrollable Task List -->
    <div class="flex-1 overflow-y-auto p-3 space-y-2 max-h-[calc(100vh-280px)]">
      <div
        v-for="task in column.tasks"
        :key="task.id"
        class="group"
      >
        <TaskCard
          :task="task"
          @click="(id: number) => emit('click-task', id)"
          @delete="(id: number) => emit('delete-task', id)"
          @move="(id: number, dir: 'left' | 'right') => emit('move-task', id, dir)"
        />
      </div>

      <!-- Empty column state -->
      <div
        v-if="column.tasks.length === 0"
        class="text-center py-8"
      >
        <p class="text-xs text-gray-400 dark:text-gray-500">
          작업이 없습니다
        </p>
      </div>
    </div>

    <!-- Add Task Button -->
    <div class="p-3 border-t border-gray-200 dark:border-gray-700">
      <button
        @click="emit('add-task', column.type)"
        class="w-full flex items-center justify-center gap-1.5 py-2 rounded-lg text-sm font-medium text-gray-500 dark:text-gray-400 hover:text-primary-600 dark:hover:text-primary-400 hover:bg-white dark:hover:bg-gray-800 border border-dashed border-gray-300 dark:border-gray-600 hover:border-primary-400 dark:hover:border-primary-500 transition-colors"
      >
        <PlusIcon class="w-4 h-4" />
        작업 추가
      </button>
    </div>
  </div>
</template>
