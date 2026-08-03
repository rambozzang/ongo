<template>
  <div class="flex min-h-0 h-full flex-col">
    <!-- Column Header -->
    <div class="mb-4">
      <div class="flex items-center justify-between mb-2">
        <h2 class="text-h3 text-content">
          {{ title }}
        </h2>
        <span class="rounded-md bg-surface-raised px-2 py-1 font-mono text-body-xs text-content-secondary">
          {{ ideas.length }}
        </span>
      </div>
      <div :class="headerColorClass" class="h-1 rounded-full"></div>
    </div>

    <!-- Drop Zone -->
    <div
      :class="[
        'min-h-0 flex-1 overflow-y-auto rounded-[11px] border border-dashed transition-colors',
        isDragOver
          ? 'border-accent bg-accent-dim'
          : 'border-line-control bg-surface-input'
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
