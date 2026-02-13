<template>
  <Teleport to="body">
    <!-- Backdrop -->
    <Transition name="backdrop">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 bg-black/50"
        aria-hidden="true"
        @click="close"
      />
    </Transition>

    <!-- Slide-over Panel -->
    <Transition name="slide-over">
      <div
        v-if="modelValue"
        class="fixed inset-y-0 right-0 z-50 flex w-full flex-col bg-white shadow-xl dark:bg-gray-800 sm:w-96"
        role="dialog"
        aria-modal="true"
        aria-label="위젯 커스터마이즈"
        @keydown.escape="close"
      >
        <!-- Header -->
        <div class="flex items-center justify-between border-b border-gray-200 px-6 py-4 dark:border-gray-700">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            위젯 커스터마이즈
          </h2>
          <button
            class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
            aria-label="모달 닫기"
            @click="close"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- Widget List -->
        <div class="flex-1 overflow-y-auto px-6 py-4">
          <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
            위젯을 드래그하여 순서를 변경하거나, 토글을 눌러 표시/숨김을 설정하세요.
          </p>

          <div class="space-y-2">
            <div
              v-for="(widget, index) in localWidgets"
              :key="widget.id"
              :draggable="true"
              class="flex items-center gap-3 rounded-lg border p-3 transition-all"
              :class="[
                draggedIndex === index
                  ? 'cursor-grabbing opacity-50 border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                  : 'cursor-grab border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800',
                dropTargetIndex === index
                  ? 'ring-2 ring-primary-500 ring-offset-2 dark:ring-offset-gray-800'
                  : '',
              ]"
              @dragstart="onDragStart(index)"
              @dragover.prevent="onDragOver(index)"
              @drop="onDrop(index)"
              @dragend="onDragEnd"
            >
              <!-- Drag Handle -->
              <div class="flex-shrink-0 text-gray-400 dark:text-gray-500">
                <Bars3Icon class="h-5 w-5" />
              </div>

              <!-- Widget Icon & Label -->
              <div class="flex flex-1 items-center gap-2">
                <component
                  :is="iconComponents[widget.icon]"
                  class="h-5 w-5 text-gray-500 dark:text-gray-400"
                />
                <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                  {{ widget.label }}
                </span>
              </div>

              <!-- Toggle Switch -->
              <button
                class="relative h-6 w-11 flex-shrink-0 rounded-full transition-colors"
                :class="widget.visible ? 'bg-primary-500' : 'bg-gray-300 dark:bg-gray-600'"
                @click="toggleWidget(widget.id)"
              >
                <span
                  class="absolute top-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-transform"
                  :class="widget.visible ? 'translate-x-5' : 'translate-x-0.5'"
                />
              </button>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="border-t border-gray-200 px-6 py-4 dark:border-gray-700">
          <button
            class="btn-secondary w-full"
            @click="resetToDefault"
          >
            <ArrowPathIcon class="h-4 w-4" />
            기본값으로 복원
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import {
  XMarkIcon,
  Bars3Icon,
  ArrowPathIcon,
  ChartBarIcon,
  ChartPieIcon,
  FilmIcon,
  CalendarDaysIcon,
  Squares2X2Icon,
} from '@heroicons/vue/24/outline'
import { PresentationChartLineIcon } from '@heroicons/vue/24/solid'
import { useWidgetSettingsStore } from '@/stores/widgetSettings'
import type { WidgetConfig } from '@/stores/widgetSettings'

defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const widgetStore = useWidgetSettingsStore()
const { widgets } = storeToRefs(widgetStore)

// Local copy for drag & drop
const localWidgets = ref<WidgetConfig[]>([])

// Drag & Drop state
const draggedIndex = ref<number | null>(null)
const dropTargetIndex = ref<number | null>(null)

// Icon component mapping
const iconComponents: Record<string, any> = {
  ChartBarIcon,
  ChartLineIcon: PresentationChartLineIcon,
  ChartPieIcon,
  FilmIcon,
  CalendarDaysIcon,
  Squares2X2Icon,
}

// Sync local widgets with store
watch(widgets, (newWidgets) => {
  localWidgets.value = [...newWidgets].sort((a, b) => a.order - b.order)
}, { immediate: true, deep: true })

function close() {
  emit('update:modelValue', false)
}

function toggleWidget(id: string) {
  widgetStore.toggleWidget(id)
}

function onDragStart(index: number) {
  draggedIndex.value = index
}

function onDragOver(index: number) {
  if (draggedIndex.value === null) return
  dropTargetIndex.value = index
}

function onDrop(dropIndex: number) {
  if (draggedIndex.value === null || draggedIndex.value === dropIndex) {
    draggedIndex.value = null
    dropTargetIndex.value = null
    return
  }

  // Reorder local widgets
  const items = [...localWidgets.value]
  const [removed] = items.splice(draggedIndex.value, 1)
  items.splice(dropIndex, 0, removed)

  localWidgets.value = items

  // Save to store
  const newOrder = items.map((w) => w.id)
  widgetStore.reorderWidgets(newOrder)

  draggedIndex.value = null
  dropTargetIndex.value = null
}

function onDragEnd() {
  draggedIndex.value = null
  dropTargetIndex.value = null
}

function resetToDefault() {
  if (confirm('위젯 설정을 기본값으로 되돌리시겠습니까?')) {
    widgetStore.resetToDefault()
  }
}
</script>

<style scoped>
/* Slide-over transitions */
.slide-over-enter-active {
  transition: transform 300ms cubic-bezier(0.32, 0.72, 0, 1);
}
.slide-over-leave-active {
  transition: transform 200ms ease-in;
}
.slide-over-enter-from,
.slide-over-leave-to {
  transform: translateX(100%);
}

/* Accessibility: Respect user's motion preferences */
@media (prefers-reduced-motion: reduce) {
  .slide-over-enter-active,
  .slide-over-leave-active {
    transition: none !important;
  }
  .slide-over-enter-from,
  .slide-over-leave-to {
    transform: none !important;
  }
}
</style>
