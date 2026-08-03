<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-header page-frame !mb-5 border-b border-line-row pb-4 pt-4 tablet:pb-5 tablet:pt-5">
    <div>
      <div class="flex min-w-0 items-center gap-2.5">
        <h1 class="truncate text-h1 text-content">{{ title }}</h1>
        <slot name="title-suffix" />
      </div>
      <p v-if="description" class="mt-1 max-w-2xl text-body-sm leading-5 text-content-secondary">
        {{ description }}
      </p>
    </div>
    <div v-if="$slots.actions" class="flex flex-wrap items-center gap-1.5">
      <slot name="actions" />
    </div>
  </header>
</template>
