<template>
  <div class="relative overflow-hidden rounded-xl border border-gray-200 bg-gray-900 dark:border-gray-700">
    <!-- Preview Container -->
    <div class="relative mx-auto flex items-center justify-center p-6" style="min-height: 280px">
      <!-- Original Frame -->
      <div
        class="relative border-2 border-dashed border-gray-500"
        :style="originalFrameStyle"
      >
        <!-- Label: Original -->
        <span class="absolute -top-5 left-0 text-[10px] font-medium text-gray-400">
          {{ $t('videoResizer.original') }}
        </span>

        <!-- Grid lines -->
        <div class="absolute inset-0 opacity-20">
          <div class="absolute left-1/3 top-0 h-full w-px bg-gray-400" />
          <div class="absolute left-2/3 top-0 h-full w-px bg-gray-400" />
          <div class="absolute left-0 top-1/3 h-px w-full bg-gray-400" />
          <div class="absolute left-0 top-2/3 h-px w-full bg-gray-400" />
        </div>

        <!-- Crop Overlay -->
        <div
          class="absolute border-2 border-primary-500 bg-primary-500/10 transition-all duration-300"
          :style="cropOverlayStyle"
        >
          <!-- Label: Target -->
          <span class="absolute -bottom-5 right-0 text-[10px] font-medium text-primary-400">
            {{ targetAspectRatio }}
          </span>

          <!-- Corner indicators -->
          <div class="absolute -left-1 -top-1 h-2 w-2 rounded-full bg-primary-500" />
          <div class="absolute -right-1 -top-1 h-2 w-2 rounded-full bg-primary-500" />
          <div class="absolute -bottom-1 -left-1 h-2 w-2 rounded-full bg-primary-500" />
          <div class="absolute -bottom-1 -right-1 h-2 w-2 rounded-full bg-primary-500" />
        </div>

        <!-- Focus Point (Draggable) -->
        <div
          v-if="smartCrop"
          class="absolute z-10 flex h-6 w-6 -translate-x-1/2 -translate-y-1/2 cursor-grab items-center justify-center rounded-full border-2 border-white bg-primary-500 shadow-lg active:cursor-grabbing"
          :style="focusPointStyle"
          @mousedown="startDrag"
          @touchstart.prevent="startDragTouch"
        >
          <div class="h-1.5 w-1.5 rounded-full bg-white" />
        </div>

        <!-- Smart crop indicator lines -->
        <template v-if="smartCrop">
          <div
            class="absolute left-0 h-px w-full bg-yellow-400/40"
            :style="{ top: `${internalFocusPoint.y * 100}%` }"
          />
          <div
            class="absolute top-0 h-full w-px bg-yellow-400/40"
            :style="{ left: `${internalFocusPoint.x * 100}%` }"
          />
        </template>
      </div>
    </div>

    <!-- Info bar -->
    <div class="flex items-center justify-between border-t border-gray-700 bg-gray-800 px-4 py-2 text-xs text-gray-400">
      <span>{{ originalAspectRatio }} → {{ targetAspectRatio }}</span>
      <span v-if="smartCrop" class="flex items-center gap-1 text-yellow-400">
        <svg class="h-3 w-3" fill="currentColor" viewBox="0 0 20 20">
          <path d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z" />
        </svg>
        {{ $t('videoResizer.smartCropActive') }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'

useI18n({ useScope: 'global' })

const props = defineProps<{
  originalAspectRatio: string
  targetAspectRatio: string
  focusPoint: { x: number; y: number }
  smartCrop: boolean
}>()

const emit = defineEmits<{
  'update:focusPoint': [point: { x: number; y: number }]
}>()

const internalFocusPoint = ref({ ...props.focusPoint })
const isDragging = ref(false)
const dragTarget = ref<HTMLElement | null>(null)

function parseRatio(ratio: string): number {
  const [w, h] = ratio.split(':').map(Number)
  return w / h
}

const originalFrameStyle = computed(() => {
  const ratio = parseRatio(props.originalAspectRatio)
  const maxWidth = 240
  const maxHeight = 200
  let width: number
  let height: number

  if (ratio >= maxWidth / maxHeight) {
    width = maxWidth
    height = Math.round(maxWidth / ratio)
  } else {
    height = maxHeight
    width = Math.round(maxHeight * ratio)
  }

  return {
    width: `${width}px`,
    height: `${height}px`,
  }
})

const cropOverlayStyle = computed(() => {
  const origRatio = parseRatio(props.originalAspectRatio)
  const targetRatio = parseRatio(props.targetAspectRatio)

  let widthPct: number
  let heightPct: number

  if (targetRatio > origRatio) {
    widthPct = 100
    heightPct = (origRatio / targetRatio) * 100
  } else {
    heightPct = 100
    widthPct = (targetRatio / origRatio) * 100
  }

  const focusX = props.smartCrop ? internalFocusPoint.value.x : 0.5
  const focusY = props.smartCrop ? internalFocusPoint.value.y : 0.5

  const maxLeft = 100 - widthPct
  const maxTop = 100 - heightPct
  const left = Math.max(0, Math.min(maxLeft, focusX * 100 - widthPct / 2))
  const top = Math.max(0, Math.min(maxTop, focusY * 100 - heightPct / 2))

  return {
    width: `${widthPct}%`,
    height: `${heightPct}%`,
    left: `${left}%`,
    top: `${top}%`,
  }
})

const focusPointStyle = computed(() => ({
  left: `${internalFocusPoint.value.x * 100}%`,
  top: `${internalFocusPoint.value.y * 100}%`,
}))

function startDrag(event: MouseEvent) {
  isDragging.value = true
  dragTarget.value = (event.target as HTMLElement).parentElement
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
}

function startDragTouch(event: TouchEvent) {
  isDragging.value = true
  dragTarget.value = (event.target as HTMLElement).parentElement
  document.addEventListener('touchmove', onDragTouch)
  document.addEventListener('touchend', stopDragTouch)
}

function onDrag(event: MouseEvent) {
  if (!isDragging.value || !dragTarget.value) return
  updateFocusFromPointer(event.clientX, event.clientY)
}

function onDragTouch(event: TouchEvent) {
  if (!isDragging.value || !dragTarget.value || !event.touches[0]) return
  updateFocusFromPointer(event.touches[0].clientX, event.touches[0].clientY)
}

function updateFocusFromPointer(clientX: number, clientY: number) {
  if (!dragTarget.value) return
  const rect = dragTarget.value.getBoundingClientRect()
  const x = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width))
  const y = Math.max(0, Math.min(1, (clientY - rect.top) / rect.height))
  internalFocusPoint.value = { x, y }
  emit('update:focusPoint', { x, y })
}

function stopDrag() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
}

function stopDragTouch() {
  isDragging.value = false
  document.removeEventListener('touchmove', onDragTouch)
  document.removeEventListener('touchend', stopDragTouch)
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('touchmove', onDragTouch)
  document.removeEventListener('touchend', stopDragTouch)
})
</script>
