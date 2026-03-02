<script setup lang="ts">
import { ref, watch, nextTick, useId } from 'vue'
import { useFocusTrap } from '@/composables/useAccessibility'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    maxWidth?: 'sm' | 'md' | 'lg' | 'xl'
  }>(),
  {
    maxWidth: 'md',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const id = useId()
const titleId = `modal-title-${id}`

const panelRef = ref<HTMLElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(panelRef)

const maxWidthClass: Record<string, string> = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
}

function close() {
  emit('update:modelValue', false)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close()
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

<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
    >
      <div class="fixed inset-0 bg-black/50" aria-hidden="true" @click="close" />
      <div
        ref="panelRef"
        class="glass-elevated relative w-full rounded-xl p-6"
        :class="maxWidthClass[maxWidth]"
      >
        <h3 :id="titleId" class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ title }}</h3>
        <slot />
        <div v-if="$slots.footer" class="mt-6 flex justify-end gap-3">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
