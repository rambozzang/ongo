<template>
  <div
    class="absolute left-0 right-0 z-10 flex items-center"
    :class="position === 'top' ? '-top-1' : '-bottom-1'"
  >
    <div class="h-2 w-2 rounded-full bg-primary-500 dark:bg-primary-400 animate-pulse" />
    <div class="h-0.5 flex-1 bg-primary-500 dark:bg-primary-400 animate-pulse" />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  position?: 'top' | 'bottom'
}>()
</script>
