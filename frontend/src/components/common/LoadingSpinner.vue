<template>
  <div class="flex items-center justify-center" :class="containerClass" role="status" aria-label="로딩 중">
    <div
      class="animate-spin rounded-full border-2 border-gray-300 dark:border-gray-600 border-t-primary-600"
      :class="sizeClass"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    size?: 'sm' | 'md' | 'lg'
    fullPage?: boolean
  }>(),
  {
    size: 'md',
    fullPage: false,
  },
)

const sizeClass = computed(() => ({
  sm: 'h-4 w-4',
  md: 'h-8 w-8',
  lg: 'h-12 w-12',
})[props.size])

const containerClass = computed(() => props.fullPage ? 'min-h-[400px]' : '')
</script>
