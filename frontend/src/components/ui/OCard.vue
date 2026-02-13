<template>
  <component
    :is="as"
    class="rounded-xl border bg-white transition-colors dark:bg-gray-800"
    :class="[variantClass, paddingClass, interactiveClass]"
  >
    <div
      v-if="$slots.header"
      class="border-b border-gray-200 dark:border-gray-700"
      :class="headerPaddingClass"
    >
      <slot name="header" />
    </div>
    <div :class="contentPaddingClass">
      <slot />
    </div>
    <div
      v-if="$slots.footer"
      class="border-t border-gray-200 dark:border-gray-700"
      :class="footerPaddingClass"
    >
      <slot name="footer" />
    </div>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  variant?: 'default' | 'elevated' | 'bordered' | 'interactive'
  padding?: 'none' | 'sm' | 'md' | 'lg'
  as?: string
}>(), {
  variant: 'default',
  padding: 'md',
  as: 'div',
})

const variantClass = computed(() => {
  switch (props.variant) {
    case 'default':
      return 'border-gray-200 shadow-sm dark:border-gray-700'
    case 'elevated':
      return 'border-gray-200 shadow-md dark:border-gray-700'
    case 'bordered':
      return 'border-2 border-gray-300 shadow-none dark:border-gray-600'
    case 'interactive':
      return 'border-gray-200 shadow-sm dark:border-gray-700 card-interactive cursor-pointer'
    default:
      return ''
  }
})

const paddingClass = computed(() => {
  // This is only used when there are no header/footer slots
  if (props.padding === 'none') return ''
  return ''
})

const headerPaddingClass = computed(() => {
  switch (props.padding) {
    case 'none':
      return 'p-0'
    case 'sm':
      return 'px-3 py-2'
    case 'md':
      return 'px-5 py-3'
    case 'lg':
      return 'px-8 py-4'
    default:
      return ''
  }
})

const contentPaddingClass = computed(() => {
  switch (props.padding) {
    case 'none':
      return 'p-0'
    case 'sm':
      return 'p-3'
    case 'md':
      return 'p-5'
    case 'lg':
      return 'p-8'
    default:
      return ''
  }
})

const footerPaddingClass = computed(() => {
  switch (props.padding) {
    case 'none':
      return 'p-0'
    case 'sm':
      return 'px-3 py-2'
    case 'md':
      return 'px-5 py-3'
    case 'lg':
      return 'px-8 py-4'
    default:
      return ''
  }
})

const interactiveClass = computed(() => {
  return props.variant === 'interactive' ? 'card-interactive' : ''
})
</script>
