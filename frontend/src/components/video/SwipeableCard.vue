<template>
  <div class="relative overflow-hidden" ref="containerRef">
    <!-- Background Actions -->
    <div class="absolute inset-0 flex items-center justify-between">
      <!-- Left Swipe: Delete (Red) -->
      <div
        class="absolute right-0 top-0 bottom-0 flex items-center justify-end px-6 transition-all"
        :style="{
          width: deleteWidth + 'px',
          backgroundColor: deleteColor,
        }"
      >
        <TrashIcon
          class="h-6 w-6 text-white transition-all"
          :style="{
            transform: `scale(${deleteScale})`,
            opacity: deleteOpacity,
          }"
        />
      </div>

      <!-- Right Swipe: Edit (Blue) -->
      <div
        class="absolute left-0 top-0 bottom-0 flex items-center justify-start px-6 transition-all"
        :style="{
          width: editWidth + 'px',
          backgroundColor: editColor,
        }"
      >
        <PencilSquareIcon
          class="h-6 w-6 text-white transition-all"
          :style="{
            transform: `scale(${editScale})`,
            opacity: editOpacity,
          }"
        />
      </div>
    </div>

    <!-- Card Content -->
    <div
      ref="cardRef"
      class="relative bg-white dark:bg-gray-800 transition-transform"
      :style="{
        transform: `translateX(${offsetX}px)`,
        transitionDuration: isSwiping ? '0ms' : '300ms',
        transitionTimingFunction: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
      }"
    >
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { TrashIcon, PencilSquareIcon } from '@heroicons/vue/24/outline'
import { useSwipe } from '@/composables/useSwipe'

const props = defineProps<{
  videoId: number
}>()

const emit = defineEmits<{
  delete: [id: number]
  edit: [id: number]
  close: []
}>()

const containerRef = ref<HTMLElement | null>(null)
const cardRef = ref<HTMLElement | null>(null)

let closeTimeout: ReturnType<typeof setTimeout> | null = null

const { offsetX, isSwiping, reset } = useSwipe(cardRef, {
  threshold: 80,
  maxSwipeDistance: 120,
  onSwipeLeft: () => {
    // Delete action
    offsetX.value = -120
    scheduleAutoClose()
    emit('delete', props.videoId)
  },
  onSwipeRight: () => {
    // Edit action
    offsetX.value = 120
    scheduleAutoClose()
    emit('edit', props.videoId)
  },
})

// Delete action visual feedback (left swipe)
const deleteWidth = computed(() => Math.max(0, -offsetX.value))
const deleteProgress = computed(() => {
  const progress = Math.abs(offsetX.value) / 80
  return Math.min(progress, 1)
})
const deleteColor = computed(() => {
  const intensity = Math.min(deleteProgress.value * 255, 200)
  return `rgba(${intensity}, 0, 0, ${0.3 + deleteProgress.value * 0.7})`
})
const deleteScale = computed(() => 0.5 + deleteProgress.value * 0.5)
const deleteOpacity = computed(() => deleteProgress.value)

// Edit action visual feedback (right swipe)
const editWidth = computed(() => Math.max(0, offsetX.value))
const editProgress = computed(() => {
  const progress = offsetX.value / 80
  return Math.min(progress, 1)
})
const editColor = computed(() => {
  const intensity = Math.min(editProgress.value * 255, 150)
  return `rgba(0, ${intensity}, 255, ${0.3 + editProgress.value * 0.7})`
})
const editScale = computed(() => 0.5 + editProgress.value * 0.5)
const editOpacity = computed(() => editProgress.value)

// Auto-close after action or timeout
function scheduleAutoClose() {
  if (closeTimeout) clearTimeout(closeTimeout)
  closeTimeout = setTimeout(() => {
    reset()
    emit('close')
  }, 3000)
}

// Close this card when exposed
function close() {
  reset()
  if (closeTimeout) {
    clearTimeout(closeTimeout)
    closeTimeout = null
  }
}

// Expose close method for parent
defineExpose({ close })

onUnmounted(() => {
  if (closeTimeout) {
    clearTimeout(closeTimeout)
  }
})
</script>
