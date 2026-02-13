<template>
  <div class="flex flex-col h-full">
    <!-- Column Header -->
    <div class="mb-4">
      <div class="flex items-center justify-between mb-2">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ title }}
        </h2>
        <span class="text-sm text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-700 px-2 py-1 rounded">
          {{ ideas.length }}
        </span>
      </div>
      <div :class="headerColorClass" class="h-1 rounded-full"></div>
    </div>

    <!-- Drop Zone -->
    <div
      :class="[
        'flex-1 rounded-lg border-2 border-dashed transition-colors overflow-y-auto',
        isDragOver
          ? 'border-indigo-500 dark:border-indigo-400 bg-indigo-50 dark:bg-indigo-900/20'
          : 'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50'
      ]"
      @dragover.prevent="handleDragOver"
      @dragleave="handleDragLeave"
      @drop.prevent="handleDrop"
    >
      <div class="p-2 space-y-3">
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ContentIdea, IdeaStatus } from '@/types/idea'

const props = defineProps<{
  title: string
  status: IdeaStatus
  ideas: ContentIdea[]
  color: 'blue' | 'yellow' | 'green' | 'purple'
}>()

const emit = defineEmits<{
  (e: 'drop', ideaId: number, status: IdeaStatus): void
}>()

const isDragOver = ref(false)

const headerColorClass = computed(() => {
  const colors = {
    blue: 'bg-blue-500 dark:bg-blue-400',
    yellow: 'bg-yellow-500 dark:bg-yellow-400',
    green: 'bg-green-500 dark:bg-green-400',
    purple: 'bg-purple-500 dark:bg-purple-400'
  }
  return colors[props.color]
})

const handleDragOver = (event: DragEvent) => {
  event.preventDefault()
  isDragOver.value = true
}

const handleDragLeave = () => {
  isDragOver.value = false
}

const handleDrop = (event: DragEvent) => {
  event.preventDefault()
  isDragOver.value = false

  const ideaId = event.dataTransfer?.getData('ideaId')
  if (ideaId) {
    emit('drop', parseInt(ideaId), props.status)
  }
}
</script>
