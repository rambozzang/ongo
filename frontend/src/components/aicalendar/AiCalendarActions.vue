<template>
  <div
    class="sticky bottom-0 z-10 flex flex-col gap-3 border-t border-gray-200 bg-white px-4 py-4 dark:border-gray-700 dark:bg-gray-800 tablet:flex-row tablet:items-center tablet:justify-between"
  >
    <!-- Summary -->
    <div class="flex flex-wrap items-center gap-3 text-sm text-gray-600 dark:text-gray-400">
      <span>
        {{ $t('aiCalendar.actions.totalSlots', { count: totalSlots }) }}
      </span>
      <span class="text-green-600 dark:text-green-400">
        {{ $t('aiCalendar.actions.confirmed', { count: confirmedCount }) }}
      </span>
      <span v-if="creditsUsed > 0" class="text-gray-400 dark:text-gray-500">
        {{ $t('aiCalendar.actions.creditsUsed', { credits: creditsUsed }) }}
      </span>
    </div>

    <!-- Buttons -->
    <div class="flex gap-3">
      <button
        class="btn-secondary inline-flex items-center gap-2"
        :disabled="loading || totalSlots === 0"
        @click="$emit('regenerate-all')"
      >
        <ArrowPathIcon class="h-4 w-4" />
        {{ $t('aiCalendar.actions.regenerateAll') }}
      </button>
      <button
        class="btn-primary inline-flex items-center gap-2"
        :disabled="loading || confirmedCount === 0"
        @click="$emit('apply')"
      >
        <CalendarDaysIcon class="h-4 w-4" />
        {{ $t('aiCalendar.actions.applyToSchedule') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowPathIcon, CalendarDaysIcon } from '@heroicons/vue/24/outline'

defineProps<{
  totalSlots: number
  confirmedCount: number
  creditsUsed: number
  loading?: boolean
}>()

defineEmits<{
  'regenerate-all': []
  'apply': []
}>()
</script>
