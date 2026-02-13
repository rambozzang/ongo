<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading"
    class="btn-press inline-flex items-center justify-center rounded-lg font-medium shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:focus:ring-offset-gray-900"
    :class="[variantClass, sizeClass, iconClass]"
    @click="handleClick"
  >
    <div v-if="loading" class="mr-2">
      <svg
        class="animate-spin"
        :class="spinnerSizeClass"
        xmlns="http://www.w3.org/2000/svg"
        fill="none"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <circle
          class="opacity-25"
          cx="12"
          cy="12"
          r="10"
          stroke="currentColor"
          stroke-width="4"
        />
        <path
          class="opacity-75"
          fill="currentColor"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
        />
      </svg>
    </div>
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  disabled?: boolean
  icon?: boolean
  type?: 'button' | 'submit' | 'reset'
}>(), {
  variant: 'primary',
  size: 'md',
  loading: false,
  disabled: false,
  icon: false,
  type: 'button',
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const variantClass = computed(() => {
  switch (props.variant) {
    case 'primary':
      return 'bg-primary-600 text-white hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-400'
    case 'secondary':
      return 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700'
    case 'ghost':
      return 'bg-transparent text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800'
    case 'danger':
      return 'bg-red-600 text-white hover:bg-red-700 dark:bg-red-500 dark:hover:bg-red-400'
    default:
      return ''
  }
})

const sizeClass = computed(() => {
  if (props.icon) {
    switch (props.size) {
      case 'sm':
        return 'h-8 w-8'
      case 'md':
        return 'h-9 w-9'
      case 'lg':
        return 'h-11 w-11'
      default:
        return ''
    }
  }

  switch (props.size) {
    case 'sm':
      return 'px-3 py-1.5 text-xs'
    case 'md':
      return 'px-4 py-2 text-sm'
    case 'lg':
      return 'px-6 py-3 text-base'
    default:
      return ''
  }
})

const iconClass = computed(() => {
  return props.icon ? 'p-0' : ''
})

const spinnerSizeClass = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'h-3 w-3'
    case 'md':
      return 'h-4 w-4'
    case 'lg':
      return 'h-5 w-5'
    default:
      return 'h-4 w-4'
  }
})

function handleClick(event: MouseEvent) {
  if (!props.disabled && !props.loading) {
    emit('click', event)
  }
}
</script>
