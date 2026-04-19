<template>
  <!-- AI 답변 추천 패널 (댓글 선택 시 인라인 슬라이드아웃) -->
  <div class="mt-2 rounded-xl border border-primary-200 bg-primary-50 p-4 dark:border-primary-800 dark:bg-primary-900/20">
    <!-- 원문 댓글 -->
    <div class="mb-3">
      <p class="mb-1 text-xs font-medium text-primary-700 dark:text-primary-400">
        {{ $t('commentsView.aiReply.originalComment') }}
      </p>
      <p class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2">{{ comment.content }}</p>
    </div>

    <!-- 톤 선택 -->
    <div class="mb-3">
      <p class="mb-2 text-xs font-medium text-gray-600 dark:text-gray-400">
        {{ $t('commentsView.aiReply.selectTone') }}
      </p>
      <div class="flex gap-2">
        <button
          v-for="tone in toneOptions"
          :key="tone.value"
          class="rounded-full px-3 py-1 text-xs font-medium transition-colors"
          :class="selectedTone === tone.value
            ? 'bg-primary-600 text-white'
            : 'bg-white text-gray-600 border border-gray-300 hover:border-primary-400 dark:bg-gray-800 dark:text-gray-400 dark:border-gray-600'"
          @click="selectedTone = tone.value"
        >
          {{ $t(tone.labelKey) }}
        </button>
      </div>
    </div>

    <!-- 생성 버튼 -->
    <button
      class="btn-primary inline-flex items-center gap-2 text-xs mb-4"
      :disabled="loading"
      @click="handleGenerate"
    >
      <SparklesIcon class="h-3.5 w-3.5" />
      {{ loading ? $t('commentsView.aiReply.generating') : $t('commentsView.aiReply.generate') }}
      <span class="rounded-full bg-white/20 px-1.5 py-0.5 text-xs">
        {{ $t('commentsView.aiReply.credits') }}
      </span>
    </button>

    <!-- AI 답변 후보 -->
    <div v-if="candidates.length > 0" class="space-y-2">
      <div
        v-for="(_candidate, idx) in candidates"
        :key="idx"
        class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800"
      >
        <!-- 편집 가능한 textarea -->
        <textarea
          v-model="editedCandidates[idx]"
          rows="2"
          class="input-field w-full text-sm resize-none mb-2"
          :placeholder="$t('commentsView.aiReply.editPlaceholder')"
        />
        <button
          class="btn-primary text-xs"
          @click="handleUse(editedCandidates[idx])"
        >
          {{ $t('commentsView.aiReply.use') }}
        </button>
      </div>
    </div>

    <!-- 빈 상태 -->
    <div
      v-else-if="!loading"
      class="py-4 text-center text-sm text-gray-500 dark:text-gray-400"
    >
      {{ $t('commentsView.aiReply.noResults') }}
    </div>

    <!-- 로딩 상태 -->
    <div v-if="loading" class="flex items-center justify-center py-6">
      <div class="h-6 w-6 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { SparklesIcon } from '@heroicons/vue/24/outline'
import { storeToRefs } from 'pinia'
import { useCommentsStore } from '@/stores/comments'
import type { Comment } from '@/types/comment'

const props = defineProps<{
  comment: Comment
}>()

const emit = defineEmits<{
  use: [commentId: number, text: string]
}>()

const commentsStore = useCommentsStore()
const { aiReplies, aiReplyLoading } = storeToRefs(commentsStore)

const selectedTone = ref<'FRIENDLY' | 'PROFESSIONAL' | 'HUMOROUS'>('FRIENDLY')
const editedCandidates = ref<string[]>([])

const toneOptions = [
  { value: 'FRIENDLY' as const, labelKey: 'commentsView.aiReply.toneFriendly' },
  { value: 'PROFESSIONAL' as const, labelKey: 'commentsView.aiReply.toneProfessional' },
  { value: 'HUMOROUS' as const, labelKey: 'commentsView.aiReply.toneHumorous' },
]

const loading = computed(() => aiReplyLoading.value[props.comment.id] ?? false)

// candidates는 AiReplyCandidate[] 객체 배열
const candidates = computed(() => aiReplies.value[props.comment.id]?.candidates ?? [])

// 후보가 바뀔 때 편집 버퍼를 generatedReply 텍스트로 초기화
watch(candidates, (newCandidates) => {
  editedCandidates.value = newCandidates.map((c) => c.generatedReply)
}, { immediate: true })

const handleGenerate = async () => {
  await commentsStore.generateAiReply(props.comment.id, selectedTone.value)
}

const handleUse = (text: string) => {
  emit('use', props.comment.id, text)
}
</script>
