<script setup lang="ts">
import { ref } from 'vue'
import {
  BellAlertIcon,
  TrashIcon,
  PlusIcon,
} from '@heroicons/vue/24/outline'
import type { KeywordAlert } from '@/types/socialListening'

defineProps<{
  alerts: KeywordAlert[]
}>()

const emit = defineEmits<{
  (e: 'add', keyword: string): void
  (e: 'toggle', id: number, enabled: boolean): void
  (e: 'delete', id: number): void
}>()

const newKeyword = ref('')

function handleAdd() {
  const keyword = newKeyword.value.trim()
  if (!keyword) return
  emit('add', keyword)
  newKeyword.value = ''
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<template>
  <div class="space-y-4">
    <!-- Alert list -->
    <div
      v-for="alert in alerts"
      :key="alert.id"
      class="flex items-center gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <!-- Toggle switch -->
      <button
        class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
        :class="alert.enabled ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'"
        role="switch"
        :aria-checked="alert.enabled"
        @click="emit('toggle', alert.id, !alert.enabled)"
      >
        <span
          class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
          :class="alert.enabled ? 'translate-x-5' : 'translate-x-0'"
        />
      </button>

      <!-- Keyword and info -->
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <BellAlertIcon class="h-4 w-4 flex-shrink-0 text-gray-400 dark:text-gray-500" />
          <span class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ alert.keyword }}
          </span>
        </div>
        <div class="mt-1 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
          <span>
            {{ $t('socialListening.mentionCount') }}: <strong class="text-gray-700 dark:text-gray-300">{{ alert.mentionCount }}</strong>
          </span>
          <span v-if="alert.lastTriggered">
            {{ $t('socialListening.lastTriggered') }}: {{ formatDate(alert.lastTriggered) }}
          </span>
        </div>
      </div>

      <!-- Delete button -->
      <button
        class="flex-shrink-0 rounded-lg p-2 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
        :title="$t('socialListening.deleteAlert')"
        @click="emit('delete', alert.id)"
      >
        <TrashIcon class="h-4 w-4" />
      </button>
    </div>

    <!-- Empty state -->
    <div
      v-if="alerts.length === 0"
      class="py-8 text-center"
    >
      <div class="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
        <BellAlertIcon class="h-6 w-6 text-gray-400 dark:text-gray-500" />
      </div>
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ $t('socialListening.noAlerts') }}
      </p>
      <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
        {{ $t('socialListening.noAlertsDesc') }}
      </p>
    </div>

    <!-- Add new alert -->
    <div class="rounded-xl border border-dashed border-gray-300 bg-gray-50 p-4 dark:border-gray-600 dark:bg-gray-800/50">
      <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('socialListening.addNewAlert') }}
      </p>
      <div class="flex gap-2">
        <input
          v-model="newKeyword"
          type="text"
          :placeholder="$t('socialListening.keywordPlaceholder')"
          class="flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500"
          @keyup.enter="handleAdd"
        />
        <button
          class="inline-flex items-center gap-1.5 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-primary-500 dark:hover:bg-primary-600"
          :disabled="!newKeyword.trim()"
          @click="handleAdd"
        >
          <PlusIcon class="h-4 w-4" />
          {{ $t('socialListening.add') }}
        </button>
      </div>
    </div>
  </div>
</template>
