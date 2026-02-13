<template>
  <div class="transition-all duration-300">
    <!-- Collapsed View -->
    <div v-if="!expanded" class="flex items-center justify-between gap-2">
      <p class="line-clamp-1 text-sm text-gray-600 dark:text-gray-300">
        {{ truncate(record.result, 100) }}
      </p>
      <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500" />
    </div>

    <!-- Expanded View -->
    <div v-else class="space-y-3">
      <!-- Full Result -->
      <div class="rounded-lg bg-gray-50 dark:bg-gray-900 px-4 py-3">
        <p class="whitespace-pre-wrap text-sm text-gray-700 dark:text-gray-300">
          {{ record.result }}
        </p>
      </div>

      <!-- Actions Footer -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
          <SparklesIcon class="h-4 w-4" />
          <span>{{ record.creditsUsed }} 크레딧 사용</span>
        </div>

        <div class="flex gap-2">
          <button
            class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-200 dark:hover:bg-gray-700"
            @click.stop="handleCopy"
          >
            <ClipboardDocumentIcon class="h-4 w-4" />
            복사
          </button>
          <button
            class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-200 dark:hover:bg-gray-700"
            @click.stop="$emit('collapse')"
          >
            <ChevronUpIcon class="h-4 w-4" />
            접기
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ChevronDownIcon, ChevronUpIcon, ClipboardDocumentIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import type { AiUsageRecord } from '@/stores/aiHistory'

const props = defineProps<{
  record: AiUsageRecord
  expanded: boolean
}>()

const emit = defineEmits<{
  copy: [text: string]
  collapse: []
}>()

function truncate(text: string, length: number): string {
  if (text.length <= length) return text
  return text.slice(0, length) + '...'
}

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.record.result)
    emit('copy', props.record.result)
  } catch {
    // Clipboard API not available
  }
}
</script>
