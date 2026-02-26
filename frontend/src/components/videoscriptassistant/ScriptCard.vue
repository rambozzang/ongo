<script setup lang="ts">
import {
  DocumentTextIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import type { VideoScript } from '@/types/videoScriptAssistant'
import ScriptToneBadge from './ScriptToneBadge.vue'

defineProps<{
  script: VideoScript
  selected?: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
}>()

const statusConfig: Record<string, { bg: string; text: string; label: string }> = {
  DRAFT: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-600 dark:text-gray-400', label: '초안' },
  GENERATING: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: '생성 중' },
  COMPLETED: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: '완료' },
  APPLIED: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: '적용됨' },
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border bg-white p-4 shadow-sm transition-all hover:shadow-md dark:bg-gray-900"
    :class="
      selected
        ? 'border-primary-500 ring-1 ring-primary-500 dark:border-primary-400 dark:ring-primary-400'
        : 'border-gray-200 dark:border-gray-700'
    "
    @click="emit('select', script.id)"
  >
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex items-center gap-2">
        <DocumentTextIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 line-clamp-1">
          {{ script.title }}
        </h3>
      </div>
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium"
        :class="[statusConfig[script.status]?.bg, statusConfig[script.status]?.text]"
      >
        {{ statusConfig[script.status]?.label ?? script.status }}
      </span>
    </div>

    <!-- Tone & Word count -->
    <div class="mb-3 flex items-center gap-2">
      <ScriptToneBadge :tone="script.tone" />
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ script.wordCount.toLocaleString() }} / {{ script.targetLength.toLocaleString() }} words
      </span>
    </div>

    <!-- Word count progress bar -->
    <div class="mb-3 h-1.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
      <div
        class="h-full rounded-full bg-primary-500 transition-all duration-500"
        :style="{ width: `${Math.min((script.wordCount / script.targetLength) * 100, 100)}%` }"
      />
    </div>

    <!-- Hook line -->
    <div v-if="script.hookLine" class="mb-3 rounded-lg bg-gray-50 p-2.5 dark:bg-gray-800/50">
      <p class="text-xs text-gray-500 dark:text-gray-400 mb-0.5">Hook</p>
      <p class="text-sm text-gray-800 dark:text-gray-200 line-clamp-2">
        "{{ script.hookLine }}"
      </p>
    </div>

    <!-- Footer -->
    <div class="flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
      <div class="flex items-center gap-1">
        <ClockIcon class="h-3.5 w-3.5" />
        <span>{{ formatDate(script.updatedAt) }}</span>
      </div>
      <span v-if="script.videoTitle" class="truncate max-w-[120px]">
        {{ script.videoTitle }}
      </span>
    </div>
  </div>
</template>
