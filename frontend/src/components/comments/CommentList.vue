<template>
  <!-- 로딩 → 빈 상태 → 댓글 목록 -->
  <AsyncState
    :loading="loading"
    :empty="comments.length === 0"
    skeleton="list"
    :skeleton-count="4"
    :empty-icon="ChatBubbleLeftEllipsisIcon"
    :empty-title="isUnfiltered ? $t('commentsView.emptyComments') : $t('commentsView.emptyFiltered')"
    :empty-description="isUnfiltered ? $t('commentsView.emptyCommentsHint') : $t('commentsView.emptyFilteredHint')"
  >
    <div class="space-y-4">
      <div v-for="comment in comments" :key="comment.id" class="relative">
        <!-- 선택 체크박스 -->
        <div class="absolute left-2 top-3 z-10">
          <input
            type="checkbox"
            :checked="selectedIds.includes(comment.id)"
            :aria-label="$t('commentsView.batch.selectOne')"
            class="h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
            @change="emit('toggle-select', comment.id)"
          />
        </div>
        <div class="pl-8">
          <CommentCard
            :comment="comment"
            :capabilities="capabilities"
            @reply="(id, text) => emit('reply', id, text)"
            @hide="emit('hide', $event)"
            @pin="emit('pin', $event)"
            @delete="emit('delete', $event)"
          />
          <!-- AI 답변 패널 (선택된 댓글에 표시) -->
          <CommentAiReplyPanel
            v-if="activeAiPanelId === comment.id"
            :comment="comment"
            @use="(id, text) => emit('ai-reply-use', id, text)"
          />
          <!-- AI 패널 토글 버튼 -->
          <button
            class="mt-1 ml-1 inline-flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400"
            @click="toggleAiPanel(comment.id)"
          >
            <SparklesIcon class="h-3.5 w-3.5" />
            {{ activeAiPanelId === comment.id ? $t('commentsView.aiReply.panelClose') : $t('commentsView.aiReply.panelOpen') }}
          </button>
        </div>
      </div>
    </div>
  </AsyncState>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ChatBubbleLeftEllipsisIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import type { Comment, CommentCapabilities } from '@/types/comment'
import CommentCard from './CommentCard.vue'
import CommentAiReplyPanel from './CommentAiReplyPanel.vue'
import AsyncState from '@/components/common/AsyncState.vue'

const props = defineProps<{
  comments: Comment[]
  capabilities?: Record<string, CommentCapabilities>
  selectedIds: number[]
  loading: boolean
  /** 필터가 하나도 걸려 있지 않은 상태인지 — 빈 상태 문구 분기 */
  isUnfiltered: boolean
}>()

const emit = defineEmits<{
  reply: [id: number, text: string]
  hide: [id: number]
  pin: [id: number]
  delete: [id: number]
  'toggle-select': [id: number]
  'ai-reply-use': [id: number, text: string]
}>()

const activeAiPanelId = ref<number | null>(null)

const toggleAiPanel = (id: number) => {
  activeAiPanelId.value = activeAiPanelId.value === id ? null : id
}

// 목록이 갱신되어 해당 댓글이 사라지면 패널을 닫는다
watch(
  () => props.comments,
  (list) => {
    if (activeAiPanelId.value !== null && !list.some((c) => c.id === activeAiPanelId.value)) {
      activeAiPanelId.value = null
    }
  },
)
</script>
