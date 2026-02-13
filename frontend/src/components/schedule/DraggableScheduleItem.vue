<template>
  <div
    :draggable="isDraggable"
    class="group relative transition-all duration-200"
    :class="{
      'opacity-50 rotate-1 border-2 border-dashed border-primary-400 dark:border-primary-500 rounded-lg': isDragging,
      'border-2 border-primary-400 dark:border-primary-500 bg-primary-50/30 dark:bg-primary-900/20 rounded-lg': isDropTarget,
    }"
    @dragstart="handleDragStart"
    @dragover="handleDragOver"
    @dragend="handleDragEnd"
    @drop="handleDrop"
  >
    <!-- Drag handle -->
    <div
      v-if="isDraggable"
      class="absolute left-0 top-0 bottom-0 flex cursor-grab items-center px-1 opacity-0 transition-opacity group-hover:opacity-100 active:cursor-grabbing"
      :class="{ 'opacity-100': isDragging }"
    >
      <div class="flex flex-col gap-0.5">
        <div class="flex gap-0.5">
          <div class="h-1 w-1 rounded-full bg-gray-400 dark:bg-gray-500" />
          <div class="h-1 w-1 rounded-full bg-gray-400 dark:bg-gray-500" />
        </div>
        <div class="flex gap-0.5">
          <div class="h-1 w-1 rounded-full bg-gray-400 dark:bg-gray-500" />
          <div class="h-1 w-1 rounded-full bg-gray-400 dark:bg-gray-500" />
        </div>
        <div class="flex gap-0.5">
          <div class="h-1 w-1 rounded-full bg-gray-400 dark:bg-gray-500" />
          <div class="h-1 w-1 rounded-full bg-gray-400 dark:bg-gray-500" />
        </div>
      </div>
    </div>

    <!-- Drop indicator -->
    <DropIndicator v-if="showDropIndicator" :position="dropPosition" />

    <!-- Content slot -->
    <div :class="{ 'pl-6': isDraggable }">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import DropIndicator from './DropIndicator.vue'

const props = defineProps<{
  item: any
  index: number
  isDraggable?: boolean
  isDragging?: boolean
  isDropTarget?: boolean
  showDropIndicator?: boolean
  dropPosition?: 'top' | 'bottom'
}>()

const emit = defineEmits<{
  dragstart: [event: DragEvent, index: number]
  dragover: [event: DragEvent, index: number]
  dragend: [event: DragEvent]
  drop: [event: DragEvent, index: number]
}>()

function handleDragStart(event: DragEvent) {
  if (!props.isDraggable) return
  emit('dragstart', event, props.index)
}

function handleDragOver(event: DragEvent) {
  if (!props.isDraggable) return
  emit('dragover', event, props.index)
}

function handleDragEnd(event: DragEvent) {
  if (!props.isDraggable) return
  emit('dragend', event)
}

function handleDrop(event: DragEvent) {
  if (!props.isDraggable) return
  emit('drop', event, props.index)
}
</script>
