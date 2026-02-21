<template>
  <div class="mt-3 space-y-2 border-l-2 border-primary-200 pl-4 dark:border-primary-800">
    <textarea
      v-model="replyText"
      placeholder="답글을 입력하세요..."
      rows="3"
      class="input"
      :disabled="submitting"
    />

    <!-- AI 답글 추천 -->
    <div v-if="showAiSuggestions && aiSuggestions.length > 0" class="space-y-2">
      <p class="text-xs font-medium text-gray-600 dark:text-gray-400">AI 추천 답글 (클릭하여 선택)</p>
      <div
        v-for="(suggestion, idx) in aiSuggestions"
        :key="idx"
        class="cursor-pointer rounded-lg border border-gray-200 p-2.5 text-sm transition-colors hover:border-primary-400 hover:bg-primary-50 dark:border-gray-700 dark:hover:border-primary-600 dark:hover:bg-primary-900/20"
        @click="selectAiSuggestion(suggestion.reply)"
      >
        <span class="mb-1 inline-block rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-300">
          {{ toneLabel(suggestion.tone) }}
        </span>
        <p class="mt-1 text-gray-700 dark:text-gray-300">{{ suggestion.reply }}</p>
      </div>
    </div>

    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ replyText.length }}/500</span>
        <button
          v-if="commentContent"
          type="button"
          class="inline-flex items-center gap-1 rounded-lg border border-indigo-200 px-2.5 py-1 text-xs font-medium text-indigo-600 transition-colors hover:bg-indigo-50 disabled:opacity-50 dark:border-indigo-800 dark:text-indigo-400 dark:hover:bg-indigo-900/30"
          :disabled="generatingAi || submitting"
          @click="generateAiReply"
        >
          <svg v-if="generatingAi" class="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <svg v-else class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z" />
          </svg>
          {{ generatingAi ? 'AI 생성 중...' : 'AI 답글' }}
        </button>
      </div>
      <div class="flex gap-2">
        <button
          type="button"
          class="rounded-lg px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
          :disabled="submitting"
          @click="emit('cancel')"
        >
          취소
        </button>
        <button
          type="button"
          class="btn-primary"
          :disabled="!replyText.trim() || submitting || replyText.length > 500"
          @click="handleSubmit"
        >
          {{ submitting ? '답글 작성 중...' : '답글 달기' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { aiApi } from '@/api/ai'

const props = defineProps<{
  commentContent?: string
  channelTone?: string
}>()

const emit = defineEmits<{
  submit: [text: string]
  cancel: []
}>()

const replyText = ref('')
const submitting = ref(false)
const generatingAi = ref(false)
const aiSuggestions = ref<{ tone: string; reply: string }[]>([])
const showAiSuggestions = ref(false)

const toneLabel = (tone: string) => {
  const labels: Record<string, string> = {
    polite: '정중한',
    friendly: '친근한',
    humorous: '유머러스한',
  }
  return labels[tone.toLowerCase()] || tone
}

const generateAiReply = async () => {
  if (!props.commentContent) return
  generatingAi.value = true
  try {
    const result = await aiApi.generateReply({
      comment: props.commentContent,
      channelTone: props.channelTone || 'friendly',
    })
    aiSuggestions.value = result.replies
    showAiSuggestions.value = true
  } catch {
    // 크레딧 부족 등 에러 시 무시
  } finally {
    generatingAi.value = false
  }
}

const selectAiSuggestion = (reply: string) => {
  replyText.value = reply
  showAiSuggestions.value = false
}

const handleSubmit = async () => {
  if (!replyText.value.trim()) return

  submitting.value = true
  try {
    emit('submit', replyText.value)
    replyText.value = ''
    aiSuggestions.value = []
    showAiSuggestions.value = false
  } finally {
    submitting.value = false
  }
}
</script>
