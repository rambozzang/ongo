<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ChatBubbleLeftRightIcon,
  FaceSmileIcon,
  ChartBarIcon,
  TrophyIcon,
  FunnelIcon,
} from '@heroicons/vue/24/outline'
import { useSentimentAnalyzerStore } from '@/stores/sentimentAnalyzer'
import SentimentResultCard from '@/components/sentimentanalyzer/SentimentResultCard.vue'
import CommentSentimentRow from '@/components/sentimentanalyzer/CommentSentimentRow.vue'
import SentimentChart from '@/components/sentimentanalyzer/SentimentChart.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useSentimentAnalyzerStore()
const { results, currentComments, summary, isLoading } = storeToRefs(store)

const selectedResultId = ref<number | null>(null)
const commentSentimentFilter = ref<string | undefined>(undefined)

const sentimentFilterOptions = [
  { value: undefined, label: '전체' },
  { value: 'POSITIVE', label: '긍정' },
  { value: 'NEUTRAL', label: '중립' },
  { value: 'NEGATIVE', label: '부정' },
]

const handleResultClick = async (id: number) => {
  selectedResultId.value = id
  commentSentimentFilter.value = undefined
  await store.fetchComments(id)
}

const handleFilterChange = async () => {
  if (selectedResultId.value !== null) {
    await store.fetchComments(selectedResultId.value, commentSentimentFilter.value)
  }
}

const closeDetail = () => {
  selectedResultId.value = null
  currentComments.value = []
}

const selectedResult = () => results.value.find((r) => r.id === selectedResultId.value)

onMounted(() => {
  store.fetchResults()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            AI 감정 분석기
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          댓글의 감정을 AI로 분석하여 시청자 반응을 한눈에 파악하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChatBubbleLeftRightIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 분석수</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalAnalyzed.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <FaceSmileIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 긍정률</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgPositiveRate.toFixed(1) }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChartBarIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 점수</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgSentimentScore.toFixed(1) }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <TrophyIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">가장 긍정적 콘텐츠</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.mostPositiveContent || '-' }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Detail View (selected result) -->
    <div v-else-if="selectedResultId !== null && selectedResult()">
      <div class="mb-4">
        <button
          class="rounded-lg px-3 py-1.5 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="closeDetail"
        >
          &larr; 목록으로 돌아가기
        </button>
      </div>

      <!-- Detail Header -->
      <div class="mb-6 rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <h2 class="mb-3 text-lg font-bold text-gray-900 dark:text-gray-100">
          {{ selectedResult()!.contentTitle }}
        </h2>
        <div class="mb-4">
          <SentimentChart
            :positive-count="selectedResult()!.positiveCount"
            :neutral-count="selectedResult()!.neutralCount"
            :negative-count="selectedResult()!.negativeCount"
          />
        </div>
      </div>

      <!-- Comment Filter -->
      <div class="mb-4 flex items-center gap-3">
        <FunnelIcon class="h-5 w-5 text-gray-400" />
        <div class="flex gap-2">
          <button
            v-for="opt in sentimentFilterOptions"
            :key="String(opt.value)"
            :class="[
              'rounded-full px-3 py-1 text-xs font-medium transition-colors',
              commentSentimentFilter === opt.value
                ? 'bg-primary-500 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700',
            ]"
            @click="commentSentimentFilter = opt.value; handleFilterChange()"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <!-- Comment List -->
      <div v-if="currentComments.length > 0" class="space-y-2">
        <CommentSentimentRow
          v-for="comment in currentComments"
          :key="comment.id"
          :comment="comment"
        />
      </div>
      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <ChatBubbleLeftRightIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          댓글이 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          해당 필터에 맞는 댓글이 없습니다
        </p>
      </div>
    </div>

    <!-- Results Grid -->
    <div v-else>
      <div v-if="results.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <SentimentResultCard
          v-for="r in results"
          :key="r.id"
          :result="r"
          @click="handleResultClick"
        />
      </div>

      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <ChatBubbleLeftRightIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          감정 분석 데이터가 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          콘텐츠의 댓글을 분석하면 감정 데이터가 표시됩니다
        </p>
      </div>
    </div>
  </div>
</template>
