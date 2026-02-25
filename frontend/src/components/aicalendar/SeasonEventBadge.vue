<template>
  <span
    class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium"
    :class="badgeClass"
  >
    <span>{{ icon }}</span>
    <span>{{ useKo ? event.name : event.nameEn }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SeasonEvent } from '@/types/aiCalendar'

const props = defineProps<{
  event: SeasonEvent
}>()

const { locale } = useI18n({ useScope: 'global' })
const useKo = computed(() => locale.value === 'ko')

const icon = computed(() => {
  switch (props.event.type) {
    case 'holiday':
      return '\uD83C\uDF89'
    case 'season':
      return '\uD83C\uDF3F'
    case 'event':
      return '\uD83D\uDCC5'
    default:
      return '\uD83D\uDCC5'
  }
})

const badgeClass = computed(() => {
  switch (props.event.type) {
    case 'holiday':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    case 'season':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'event':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'
  }
})
</script>
