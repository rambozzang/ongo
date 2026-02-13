<template>
  <div
    class="animate-pulse rounded"
    :class="[shapeClass, sizeClass]"
    :style="customStyle"
  >
    <div class="h-full w-full rounded bg-gray-200 dark:bg-gray-700" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  type?: 'text' | 'circle' | 'rect' | 'card'
  width?: string
  height?: string
  lines?: number
}>(), {
  type: 'rect',
  lines: 1,
})

const shapeClass = computed(() => {
  switch (props.type) {
    case 'circle': return 'rounded-full'
    case 'card': return 'rounded-xl'
    default: return 'rounded'
  }
})

const sizeClass = computed(() => {
  if (props.width || props.height) return ''
  switch (props.type) {
    case 'text': return 'h-4 w-full'
    case 'circle': return 'h-10 w-10'
    case 'card': return 'h-48 w-full'
    default: return 'h-8 w-full'
  }
})

const customStyle = computed(() => {
  const style: Record<string, string> = {}
  if (props.width) style.width = props.width
  if (props.height) style.height = props.height
  return style
})
</script>
