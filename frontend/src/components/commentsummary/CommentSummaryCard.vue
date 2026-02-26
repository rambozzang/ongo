<script setup lang="ts">
import { ref } from 'vue'
import {
  ChatBubbleBottomCenterTextIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'
import type { CommentSummaryResult, TopComment } from '@/types/commentSummary'
import SentimentBar from './SentimentBar.vue'
import TopCommentRow from './TopCommentRow.vue'

const props = defineProps<{
  result: CommentSummaryResult
  topComments: TopComment[]
}>()

const emit = defineEmits<{
  expand: [summaryId: number]
}>()

const expanded = ref(false)

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  NAVER_CLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getPlatformStyle = (platform: string) =>
  platformColors[platform.toUpperCase()] ?? platformColors.YOUTUBE

const formatDate = (dateStr: string) => {
  try {
    return new Date(dateStr).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return dateStr
  }
}

const toggleExpand = () => {
  if (!expanded.value) {
    emit('expand', props.result.id)
  }
  expanded.value = !expanded.value
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header -->
    <div class="p-4">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <ChatBubbleBottomCenterTextIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ result.videoTitle }}
        </h3>
        <span
          :class="[getPlatformStyle(result.platform).bg, getPlatformStyle(result.platform).text]"
          class="rounded-full px-2 py-0.5 text-xs font-medium"
        >
          {{ result.platform }}
        </span>
        <span class="ml-auto text-xs text-gray-400 dark:text-gray-500">
          {{ formatDate(result.analyzedAt) }}
        </span>
      </div>

      <!-- Total comments -->
      <div class="mb-3 flex items-center gap-2">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">
          {{ $t('commentSummary.totalComments') }}:
        </span>
        <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ result.totalComments.toLocaleString() }}
        </span>
      </div>

      <!-- Sentiment bar -->
      <div class="mb-4">
        <SentimentBar
          :positive-pct="result.positivePct"
          :neutral-pct="result.neutralPct"
          :negative-pct="result.negativePct"
        />
      </div>

      <!-- AI Summary -->
      <div class="mb-3 rounded-lg bg-blue-50 p-3 dark:bg-blue-900/10">
        <p class="mb-1 text-xs font-medium text-blue-700 dark:text-blue-400">
          {{ $t('commentSummary.aiSummary') }}
        </p>
        <p class="text-sm text-gray-800 dark:text-gray-200">
          {{ result.aiSummary }}
        </p>
      </div>

      <!-- Topics -->
      <div class="flex flex-wrap items-center gap-1.5">
        <TagIcon class="h-4 w-4 text-gray-400 dark:text-gray-500" />
        <span
          v-for="topic in result.topTopics"
          :key="topic"
          class="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-700 dark:bg-gray-800 dark:text-gray-300"
        >
          {{ topic }}
        </span>
      </div>
    </div>

    <!-- Expand / Collapse -->
    <button
      class="flex w-full items-center justify-center gap-1 border-t border-gray-100 px-4 py-2.5 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-gray-800/50"
      @click="toggleExpand"
    >
      <span>{{ expanded ? $t('commentSummary.hideTopComments') : $t('commentSummary.showTopComments') }}</span>
      <ChevronUpIcon v-if="expanded" class="h-3.5 w-3.5" />
      <ChevronDownIcon v-else class="h-3.5 w-3.5" />
    </button>

    <!-- Top Comments (expandable) -->
    <div
      v-if="expanded"
      class="border-t border-gray-100 p-4 dark:border-gray-700"
    >
      <div v-if="topComments.length > 0" class="space-y-2">
        <TopCommentRow
          v-for="c in topComments"
          :key="c.id"
          :comment="c"
        />
      </div>
      <p v-else class="text-center text-sm text-gray-400 dark:text-gray-500">
        {{ $t('commentSummary.noTopComments') }}
      </p>
    </div>
  </div>
</template>
