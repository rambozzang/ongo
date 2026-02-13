<template>
  <Teleport to="body">
    <Transition name="backdrop">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 bg-black/50"
        aria-hidden="true"
        @click="close"
      />
    </Transition>
    <Transition name="sheet">
      <div
        v-if="modelValue"
        ref="sheetRef"
        role="dialog"
        aria-modal="true"
        :aria-label="title || '하단 시트'"
        class="fixed inset-x-0 bottom-0 z-50 max-h-[90vh] overflow-hidden rounded-t-2xl bg-white shadow-xl dark:bg-gray-800"
        @touchstart="onTouchStart"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
        @keydown.escape="close"
      >
        <!-- Drag handle -->
        <div class="flex justify-center pb-2 pt-3">
          <div class="h-1 w-10 rounded-full bg-gray-300 dark:bg-gray-600" aria-hidden="true" />
        </div>

        <!-- Header -->
        <div v-if="title" class="border-b border-gray-200 px-4 pb-3 dark:border-gray-700">
          <h3 class="text-center text-h3 text-gray-900 dark:text-gray-100">{{ title }}</h3>
        </div>

        <!-- Content -->
        <div class="overflow-y-auto px-4 py-4" :style="{ maxHeight: 'calc(90vh - 80px)' }">
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useFocusTrap } from '@/composables/useAccessibility'

const props = defineProps<{
  modelValue: boolean
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const touchStartY = ref(0)
const isDragging = ref(false)
const sheetRef = ref<HTMLElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)

const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(sheetRef)

function close() {
  emit('update:modelValue', false)
}

function onTouchStart(e: TouchEvent) {
  touchStartY.value = e.touches[0].clientY
  isDragging.value = true
}

function onTouchMove(e: TouchEvent) {
  if (!isDragging.value) return
  const diff = e.touches[0].clientY - touchStartY.value
  if (diff > 100) {
    close()
    isDragging.value = false
  }
}

function onTouchEnd() {
  isDragging.value = false
}

watch(() => props.modelValue, async (isOpen) => {
  if (isOpen) {
    previousActiveElement.value = document.activeElement as HTMLElement
    await nextTick()
    activateFocusTrap()
  } else {
    deactivateFocusTrap()
    previousActiveElement.value?.focus()
  }
})
</script>
