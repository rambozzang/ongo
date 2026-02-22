<template>
  <button
    :aria-label="isKorean ? 'Switch to English' : '한국어로 변경'"
    class="rounded-lg border border-gray-200 bg-gray-100 px-2 py-1.5 text-xs font-semibold transition-colors hover:bg-gray-200 dark:border-gray-700 dark:bg-gray-800 dark:hover:bg-gray-700"
    :class="isKorean ? 'text-gray-600 dark:text-gray-300' : 'text-primary-600 dark:text-primary-400'"
    @click="toggleLocale"
  >
    {{ isKorean ? 'EN' : '한' }}
  </button>
</template>

<script setup lang="ts">
import { useLocale } from '@/composables/useLocale'

const { isKorean, toggleLocale } = useLocale()
</script>
