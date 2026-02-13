<template>
  <div class="w-full">
    <label
      v-if="label"
      :for="inputId"
      class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
    >
      {{ label }}
    </label>
    <div class="relative">
      <div
        v-if="$slots.prefix"
        class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2"
      >
        <slot name="prefix" />
      </div>
      <input
        :id="inputId"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :aria-invalid="!!error"
        :aria-describedby="error ? errorId : undefined"
        class="input-field w-full transition-colors"
        :class="[
          error ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : '',
          $slots.prefix ? 'pl-10' : '',
          clearable || $slots.suffix ? 'pr-10' : '',
        ]"
        @input="onInput"
        @blur="emit('blur')"
        @focus="emit('focus')"
      />
      <div
        v-if="clearable && modelValue && !disabled"
        class="absolute right-3 top-1/2 -translate-y-1/2"
      >
        <button
          type="button"
          class="rounded-full p-0.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          aria-label="입력 지우기"
          @click="handleClear"
        >
          <svg
            class="h-4 w-4"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      </div>
      <div
        v-else-if="$slots.suffix"
        class="absolute right-3 top-1/2 -translate-y-1/2"
      >
        <slot name="suffix" />
      </div>
    </div>
    <p
      v-if="error"
      :id="errorId"
      role="alert"
      class="mt-1.5 text-xs text-red-600 dark:text-red-400"
    >
      {{ error }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue'

const id = useId()
const inputId = `input-${id}`
const errorId = `input-error-${id}`

withDefaults(defineProps<{
  modelValue: string
  type?: 'text' | 'email' | 'password' | 'number' | 'search' | 'url'
  placeholder?: string
  label?: string
  error?: string
  disabled?: boolean
  clearable?: boolean
}>(), {
  type: 'text',
  placeholder: '',
  label: '',
  error: '',
  disabled: false,
  clearable: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  clear: []
  blur: []
  focus: []
}>()

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}

function handleClear() {
  emit('update:modelValue', '')
  emit('clear')
}
</script>
