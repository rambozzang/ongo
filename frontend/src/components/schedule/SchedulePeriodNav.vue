<template>
  <div class="card mb-4 flex items-center justify-between">
    <button
      class="rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
      :aria-label="$t('action.previous')"
      @click="$emit('prev')"
    >
      <ChevronLeftIcon class="h-5 w-5" />
    </button>

    <div class="flex items-center gap-3">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ title }}</h2>
      <button
        class="rounded-md bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        @click="$emit('today')"
      >
        {{ $t('calendar.today') }}
      </button>
    </div>

    <button
      class="rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
      :aria-label="$t('action.next')"
      @click="$emit('next')"
    >
      <ChevronRightIcon class="h-5 w-5" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'

defineProps<{
  title: string
}>()

defineEmits<{
  prev: []
  next: []
  today: []
}>()
</script>
