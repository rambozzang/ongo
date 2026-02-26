<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  ChatBubbleBottomCenterTextIcon,
  FunnelIcon,
} from '@heroicons/vue/24/outline'
import { useCommentSummaryStore } from '@/stores/commentSummary'
import CommentSummaryCard from '@/components/commentsummary/CommentSummaryCard.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { TopComment } from '@/types/commentSummary'

const { t } = useI18n({ useScope: 'global' })
const store = useCommentSummaryStore()
const { results, summary, isLoading } = storeToRefs(store)

const selectedPlatform = ref<string>('')
const topCommentsMap = ref<Record<number, TopComment[]>>({})

const platformOptions = [
  { value: '', label: 'commentSummary.allPlatforms' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

const handlePlatformFilter = () => {
  store.fetchResults(selectedPlatform.value || undefined)
}

const handleExpand = async (summaryId: number) => {
  if (!topCommentsMap.value[summaryId]) {
    await store.fetchTopComments(summaryId)
    topCommentsMap.value[summaryId] = [...store.topComments]
  }
}

const getTopComments = (summaryId: number): TopComment[] => {
  return topCommentsMap.value[summaryId] ?? []
}

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
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('commentSummary.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('commentSummary.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <div class="relative">
          <FunnelIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <select
            v-model="selectedPlatform"
            class="rounded-lg border border-gray-200 bg-white py-2 pl-9 pr-8 text-sm text-gray-700 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
            @change="handlePlatformFilter"
          >
            <option
              v-for="opt in platformOptions"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.value ? opt.label : $t(opt.label) }}
            </option>
          </select>
        </div>
      </div>
    </div>

    <PageGuide
      :title="$t('commentSummary.pageGuideTitle')"
      :items="($tm('commentSummary.pageGuide') as string[])"
    />

    <!-- Summary Cards -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-5">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('commentSummary.summaryTotalAnalyzed') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalAnalyzed }}
        </p>
      </div>
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('commentSummary.summaryTotalComments') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalComments.toLocaleString() }}
        </p>
      </div>
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('commentSummary.summaryAvgPositive') }}</p>
        <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
          {{ summary.avgPositive.toFixed(1) }}%
        </p>
      </div>
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('commentSummary.summaryAvgNegative') }}</p>
        <p class="mt-1 text-2xl font-bold text-red-600 dark:text-red-400">
          {{ summary.avgNegative.toFixed(1) }}%
        </p>
      </div>
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('commentSummary.summaryMostDiscussed') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.mostDiscussedTopic }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Results -->
    <div v-else-if="results.length > 0" class="space-y-4">
      <CommentSummaryCard
        v-for="result in results"
        :key="result.id"
        :result="result"
        :top-comments="getTopComments(result.id)"
        @expand="handleExpand"
      />
    </div>

    <!-- Empty state -->
    <div
      v-else
      class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <ChatBubbleBottomCenterTextIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('commentSummary.noResults') }}
      </h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        {{ $t('commentSummary.noResultsHint') }}
      </p>
    </div>
  </div>
</template>
