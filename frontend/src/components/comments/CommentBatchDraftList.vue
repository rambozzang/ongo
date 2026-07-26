<template>
  <!-- 일괄 AI 답변 초안 결과 -->
  <div v-if="drafts.length > 0" class="card">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">
        {{ $t('commentsView.aiDraftTitle') }}
      </h3>
      <button
        class="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
        @click="emit('close')"
      >
        {{ $t('commentsView.aiDraftClose') }}
      </button>
    </div>
    <div class="space-y-3">
      <div
        v-for="draft in drafts"
        :key="draft.commentId"
        class="rounded-lg border border-gray-200 dark:border-gray-700 p-3"
      >
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-1 line-clamp-1">
          {{ $t('commentsView.originalComment') }}: {{ draft.commentContent }}
        </p>
        <p class="text-sm text-gray-900 dark:text-gray-100">
          {{ draft.draftReply }}
        </p>
        <button
          class="mt-2 text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400"
          @click="emit('apply', draft.commentId, draft.draftReply)"
        >
          {{ $t('commentsView.aiDraftApply') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AiDraftItem } from '@/types/comment'

defineProps<{
  drafts: AiDraftItem[]
}>()

const emit = defineEmits<{
  apply: [commentId: number, text: string]
  close: []
}>()
</script>
