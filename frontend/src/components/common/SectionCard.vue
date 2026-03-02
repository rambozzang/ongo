<script setup lang="ts">
defineProps<{
  title?: string
  compact?: boolean
}>()
</script>

<template>
  <div :class="compact ? 'card-compact' : 'card'">
    <h3 v-if="title" class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ title }}</h3>
    <slot />
  </div>
</template>
