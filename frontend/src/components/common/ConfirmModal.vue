<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      :aria-describedby="messageId"
    >
      <div class="fixed inset-0 bg-black/50" aria-hidden="true" @click="cancel" />
      <div ref="modalRef" class="glass-elevated relative w-full max-w-md rounded-xl p-6">
        <h3 :id="titleId" class="mb-2 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ title }}</h3>
        <p :id="messageId" class="mb-6 text-sm text-gray-600 dark:text-gray-300">{{ message }}</p>
        <div class="flex justify-end gap-3">
          <button class="btn-secondary" @click="cancel">
            {{ cancelText }}
          </button>
          <button :class="danger ? 'btn-danger' : 'btn-primary'" @click="confirm">
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, useId } from 'vue'
import { useFocusTrap } from '@/composables/useAccessibility'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    message: string
    confirmText?: string
    cancelText?: string
    danger?: boolean
  }>(),
  {
    confirmText: '확인',
    cancelText: '취소',
    danger: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

const id = useId()
const titleId = `confirm-modal-title-${id}`
const messageId = `confirm-modal-message-${id}`

const modalRef = ref<HTMLElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)

const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(modalRef)

function confirm() {
  emit('confirm')
  emit('update:modelValue', false)
}

function cancel() {
  emit('cancel')
  emit('update:modelValue', false)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    cancel()
  }
}

watch(() => props.modelValue, async (isOpen) => {
  if (isOpen) {
    previousActiveElement.value = document.activeElement as HTMLElement
    document.addEventListener('keydown', handleKeydown)
    await nextTick()
    activateFocusTrap()
  } else {
    document.removeEventListener('keydown', handleKeydown)
    deactivateFocusTrap()
    previousActiveElement.value?.focus()
  }
})
</script>
