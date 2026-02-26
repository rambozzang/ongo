<script setup lang="ts">
import { ref } from 'vue'
import {
  ChatBubbleLeftRightIcon,
  PaperAirplaneIcon,
  XMarkIcon,
  VideoCameraIcon,
} from '@heroicons/vue/24/outline'
import type { SmartReplySuggestion } from '@/types/smartReply'

const props = defineProps<{
  suggestion: SmartReplySuggestion
}>()

const emit = defineEmits<{
  send: [id: string, text: string]
  dismiss: [id: string]
}>()

const selectedIndex = ref<number | null>(null)
const customReply = ref('')

const platformColors: Record<string, { bg: string; text: string }> = {
  youtube: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  instagram: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  tiktok: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  naverclip: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const sentimentConfig: Record<string, { bg: string; text: string; label: string }> = {
  POSITIVE: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: 'positive' },
  NEGATIVE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: 'negative' },
  NEUTRAL: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300', label: 'neutral' },
}

const toneLabels: Record<string, string> = {
  FRIENDLY: 'friendly',
  PROFESSIONAL: 'professional',
  CASUAL: 'casual',
  GRATEFUL: 'grateful',
  HUMOROUS: 'humorous',
}

const contextLabels: Record<string, string> = {
  COMMENT: 'comment',
  DM: 'dm',
  MENTION: 'mention',
  REVIEW: 'review',
}

const currentReplyText = () => {
  if (selectedIndex.value !== null) {
    return props.suggestion.suggestions[selectedIndex.value]?.text ?? ''
  }
  return customReply.value
}

const handleSend = () => {
  const text = currentReplyText()
  if (!text.trim()) return
  emit('send', props.suggestion.id, text)
}

const handleDismiss = () => {
  emit('dismiss', props.suggestion.id)
}

const selectSuggestion = (index: number) => {
  selectedIndex.value = selectedIndex.value === index ? null : index
  if (selectedIndex.value !== null) {
    customReply.value = ''
  }
}

const handleCustomInput = () => {
  if (customReply.value.trim()) {
    selectedIndex.value = null
  }
}

const formatTime = (iso: string) => {
  try {
    const date = new Date(iso)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMs / 3600000)

    if (diffMins < 1) return '방금 전'
    if (diffMins < 60) return `${diffMins}분 전`
    if (diffHours < 24) return `${diffHours}시간 전`
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch {
    return iso
  }
}

const getPlatformStyle = (platform: string) => platformColors[platform.toLowerCase()] ?? platformColors.tiktok
const getSentimentStyle = (sentiment: string) => sentimentConfig[sentiment] ?? sentimentConfig.NEUTRAL
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header: Author, Platform, Sentiment, Time -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <div class="flex items-center gap-2">
        <ChatBubbleLeftRightIcon class="h-4 w-4 text-gray-400 dark:text-gray-500" />
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ suggestion.originalAuthor }}
        </span>
      </div>

      <span
        :class="[getPlatformStyle(suggestion.platform).bg, getPlatformStyle(suggestion.platform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ suggestion.platform }}
      </span>

      <span
        :class="[getSentimentStyle(suggestion.sentiment).bg, getSentimentStyle(suggestion.sentiment).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ $t(`smartReply.sentiment.${getSentimentStyle(suggestion.sentiment).label}`) }}
      </span>

      <span class="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
        {{ $t(`smartReply.context.${contextLabels[suggestion.context]}`) }}
      </span>

      <span class="ml-auto text-xs text-gray-400 dark:text-gray-500">
        {{ formatTime(suggestion.createdAt) }}
      </span>
    </div>

    <!-- Video title -->
    <div v-if="suggestion.videoTitle" class="mb-3 flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
      <VideoCameraIcon class="h-3.5 w-3.5" />
      <span>{{ suggestion.videoTitle }}</span>
    </div>

    <!-- Original text -->
    <div class="mb-4 rounded-lg bg-gray-50 p-3 dark:bg-gray-800/50">
      <p class="text-sm text-gray-800 dark:text-gray-200">{{ suggestion.originalText }}</p>
    </div>

    <!-- AI suggestions -->
    <div class="mb-4 space-y-2">
      <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('smartReply.aiSuggestions') }}</p>
      <button
        v-for="(s, index) in suggestion.suggestions"
        :key="index"
        class="w-full rounded-lg border p-3 text-left text-sm transition-colors"
        :class="
          selectedIndex === index
            ? 'border-primary-500 bg-primary-50 text-gray-900 dark:border-primary-400 dark:bg-primary-900/20 dark:text-gray-100'
            : 'border-gray-200 bg-white text-gray-700 hover:border-gray-300 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:border-gray-600 dark:hover:bg-gray-800'
        "
        @click="selectSuggestion(index)"
      >
        <div class="flex items-center justify-between">
          <span>{{ s.text }}</span>
          <div class="ml-2 flex shrink-0 items-center gap-2">
            <span class="rounded-full bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400">
              {{ $t(`smartReply.tone.${toneLabels[s.tone]}`) }}
            </span>
            <span class="text-[10px] text-gray-400 dark:text-gray-500">
              {{ Math.round(s.confidence * 100) }}%
            </span>
          </div>
        </div>
      </button>
    </div>

    <!-- Custom reply input -->
    <div class="mb-4">
      <input
        v-model="customReply"
        type="text"
        :placeholder="$t('smartReply.customReplyPlaceholder')"
        class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
        @input="handleCustomInput"
        @keyup.enter="handleSend"
      />
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-end gap-2">
      <button
        class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
        @click="handleDismiss"
      >
        <XMarkIcon class="h-4 w-4" />
        {{ $t('smartReply.dismiss') }}
      </button>
      <button
        class="btn-primary inline-flex items-center gap-1.5 text-sm"
        :disabled="!currentReplyText().trim()"
        @click="handleSend"
      >
        <PaperAirplaneIcon class="h-4 w-4" />
        {{ $t('smartReply.send') }}
      </button>
    </div>
  </div>
</template>
