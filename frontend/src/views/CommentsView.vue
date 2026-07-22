<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="$t('commentsView.title')" :description="headerDescription">
      <template #actions>
        <button
          class="btn-secondary inline-flex items-center gap-2"
          :disabled="featureUnavailable || batchDraftLoading || selectedCommentIds.length === 0"
          @click="handleBatchAiDraft"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ batchDraftLoading ? $t('commentsView.aiBatchGenerating') : $t('commentsView.aiBatchReply', { count: selectedCommentIds.length }) }}
        </button>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="featureUnavailable || syncing"
          @click="handleSync"
        >
          <ArrowPathIcon
            class="h-4 w-4"
            :class="{ 'animate-spin': syncing }"
          />
          {{ syncing ? $t('commentsView.syncing') : $t('commentsView.syncComments') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('commentsView.pageGuideTitle')" :items="($tm('commentsView.pageGuide') as string[])" />

    <EmptyState
      v-if="featureUnavailable"
      :title="$t('commentsView.planRequiredTitle')"
      :description="$t('commentsView.planRequiredDescription')"
      action-label="플랜 확인하기"
      action-to="/subscription"
    />

    <div v-else class="space-y-6">

    <!-- 위기 감지 배너 -->
    <div
      v-if="crisisStatus?.isInCrisis && !crisisDismissed"
      class="rounded-xl border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-900/20"
    >
      <div class="flex items-start justify-between gap-3">
        <div class="flex items-start gap-3">
          <ExclamationTriangleIcon class="h-5 w-5 shrink-0 text-red-600 dark:text-red-400 mt-0.5" />
          <div>
            <h4 class="text-sm font-semibold text-red-800 dark:text-red-300">
              {{ $t('commentsView.crisis.title') }}
            </h4>
            <p class="mt-0.5 text-xs text-red-700 dark:text-red-400">
              {{ $t('commentsView.crisis.description', { changePercent: crisisStatus.changePercent, count: crisisStatus.currentNegativeCount }) }}
            </p>
            <!-- 주요 키워드 태그 -->
            <div v-if="crisisStatus.topKeywords.length > 0" class="mt-2 flex flex-wrap gap-1.5">
              <span class="text-xs font-medium text-red-700 dark:text-red-400">
                {{ $t('commentsView.crisis.keywords') }}:
              </span>
              <span
                v-for="kw in crisisStatus.topKeywords"
                :key="kw"
                class="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-900/40 dark:text-red-300"
              >
                {{ kw }}
              </span>
            </div>
          </div>
        </div>
        <button
          class="text-xs text-red-600 hover:text-red-800 dark:text-red-400 dark:hover:text-red-200 shrink-0"
          @click="crisisDismissed = true"
        >
          {{ $t('commentsView.crisis.dismiss') }}
        </button>
      </div>
    </div>

    <!-- Sentiment summary -->
    <div v-if="commentStats.total > 0" class="card">
      <h3 class="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-300">{{ $t('commentsView.sentimentSummary') }}</h3>
      <div class="mb-2 flex h-4 overflow-hidden rounded-full">
        <div
          v-if="commentStats.positive > 0"
          class="bg-green-500"
          :style="{ width: `${(commentStats.positive / commentStats.total) * 100}%` }"
        />
        <div
          v-if="commentStats.neutral > 0"
          class="bg-gray-400"
          :style="{ width: `${(commentStats.neutral / commentStats.total) * 100}%` }"
        />
        <div
          v-if="commentStats.negative > 0"
          class="bg-red-500"
          :style="{ width: `${(commentStats.negative / commentStats.total) * 100}%` }"
        />
      </div>
      <div class="flex flex-wrap gap-4 text-sm">
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-green-500" />
          <span class="text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.sentimentPositive') }}: {{ commentStats.positive }}
            ({{ Math.round((commentStats.positive / commentStats.total) * 100) }}%)
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-gray-400" />
          <span class="text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.sentimentNeutral') }}: {{ commentStats.neutral }}
            ({{ Math.round((commentStats.neutral / commentStats.total) * 100) }}%)
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-red-500" />
          <span class="text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.sentimentNegative') }}: {{ commentStats.negative }}
            ({{ Math.round((commentStats.negative / commentStats.total) * 100) }}%)
          </span>
        </div>
      </div>
    </div>

    <!-- Sentiment Trend Chart -->
    <div class="card">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ $t('commentsView.sentimentTrend') }}</h3>
        <div class="flex gap-2">
          <button
            v-for="d in [7, 14, 30]"
            :key="d"
            class="rounded-lg px-3 py-1 text-xs font-medium transition-colors"
            :class="trendDays === d
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'"
            @click="loadTrend(d)"
          >
            {{ $t('commentsView.days', { n: d }) }}
          </button>
        </div>
      </div>
      <div v-if="sentimentTrendLoading" class="flex items-center justify-center py-8">
        <div class="h-6 w-6 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
      </div>
      <div v-else-if="sentimentTrendData" class="space-y-3">
        <!-- 트렌드 요약 배지 -->
        <div class="flex items-center gap-2 mb-2">
          <span
            class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium"
            :class="{
              'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400': sentimentTrendData.summary.trend === 'IMPROVING',
              'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400': sentimentTrendData.summary.trend === 'STABLE',
              'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400': sentimentTrendData.summary.trend === 'WORSENING',
            }"
          >
            <ArrowTrendingUpIcon v-if="sentimentTrendData.summary.trend === 'IMPROVING'" class="h-3 w-3" />
            <MinusIcon v-else-if="sentimentTrendData.summary.trend === 'STABLE'" class="h-3 w-3" />
            <ArrowTrendingDownIcon v-else class="h-3 w-3" />
            {{ sentimentTrendData.summary.trend === 'IMPROVING' ? $t('commentsView.trendImproving') : sentimentTrendData.summary.trend === 'STABLE' ? $t('commentsView.trendStable') : $t('commentsView.trendWorsening') }}
          </span>
          <span class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('commentsView.sentimentPositive') }} {{ sentimentTrendData.summary.totalPositive }} ·
            {{ $t('commentsView.sentimentNeutral') }} {{ sentimentTrendData.summary.totalNeutral }} ·
            {{ $t('commentsView.sentimentNegative') }} {{ sentimentTrendData.summary.totalNegative }}
          </span>
        </div>
        <!-- 간이 바 차트 -->
        <div class="space-y-1 max-h-48 overflow-y-auto">
          <div
            v-for="point in sentimentTrendData.data"
            :key="point.date"
            class="flex items-center gap-2 text-xs"
          >
            <span class="w-16 text-right text-gray-500 dark:text-gray-400 shrink-0">
              {{ formatTrendDate(point.date) }}
            </span>
            <div class="flex flex-1 h-3 rounded-full overflow-hidden bg-gray-100 dark:bg-gray-800">
              <div
                v-if="point.positive > 0"
                class="bg-green-500"
                :style="{ width: `${getPercent(point.positive, point)}%` }"
              />
              <div
                v-if="point.neutral > 0"
                class="bg-gray-400"
                :style="{ width: `${getPercent(point.neutral, point)}%` }"
              />
              <div
                v-if="point.negative > 0"
                class="bg-red-500"
                :style="{ width: `${getPercent(point.negative, point)}%` }"
              />
            </div>
            <span class="w-8 text-right text-gray-500 dark:text-gray-400 shrink-0">
              {{ point.positive + point.neutral + point.negative }}
            </span>
          </div>
        </div>
      </div>
      <div v-else class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
        {{ $t('commentsView.noTrendData') }}
      </div>
    </div>

    <!-- 키워드 클라우드 -->
    <KeywordCloudSection
      :keywords="keywordCloud"
      :loading="keywordCloudLoading"
      @load="handleKeywordCloudLoad"
      @filter="handleKeywordFilter"
    />

    <!-- FAQ Clusters -->
    <div class="card">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ $t('commentsView.faqTitle') }}</h3>
        <button
          class="btn-secondary inline-flex items-center gap-1.5 text-xs"
          :disabled="faqLoading"
          @click="commentsStore.fetchFaqClusters()"
        >
          <SparklesIcon class="h-3.5 w-3.5" />
          {{ faqLoading ? $t('commentsView.faqAnalyzing') : $t('commentsView.faqAiAnalyze') }}
        </button>
      </div>
      <div v-if="faqLoading" class="flex items-center justify-center py-8">
        <div class="h-6 w-6 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
      </div>
      <div v-else-if="faqData && faqData.clusters.length > 0" class="space-y-3">
        <div
          v-for="(cluster, idx) in faqData.clusters"
          :key="idx"
          class="rounded-lg border border-gray-200 dark:border-gray-700 p-3"
        >
          <div class="flex items-center justify-between mb-2">
            <h4 class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ cluster.topic }}
            </h4>
            <span class="text-xs text-gray-500 dark:text-gray-400">
              {{ $t('commentsView.faqCount', { count: cluster.questionCount }) }}
            </span>
          </div>
          <ul class="mb-2 space-y-1">
            <li
              v-for="(q, qIdx) in cluster.sampleQuestions"
              :key="qIdx"
              class="text-xs text-gray-600 dark:text-gray-400 pl-3 relative before:absolute before:left-0 before:top-1.5 before:h-1 before:w-1 before:rounded-full before:bg-gray-400"
            >
              {{ q }}
            </li>
          </ul>
          <div class="rounded-md bg-primary-50 dark:bg-primary-900/20 p-2">
            <p class="text-xs text-primary-700 dark:text-primary-300">
              <span class="font-medium">{{ $t('commentsView.faqSuggestedReply') }}</span> {{ cluster.suggestedReply }}
            </p>
          </div>
        </div>
      </div>
      <div v-else class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
        {{ $t('commentsView.faqEmpty') }}
      </div>
    </div>

    <!-- Batch AI Draft Results -->
    <div v-if="batchDrafts.length > 0" class="card">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ $t('commentsView.aiDraftTitle') }}</h3>
        <button
          class="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
          @click="commentsStore.batchDrafts = []"
        >
          {{ $t('commentsView.aiDraftClose') }}
        </button>
      </div>
      <div class="space-y-3">
        <div
          v-for="draft in batchDrafts"
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
            @click="handleReply(draft.commentId, draft.draftReply)"
          >
            {{ $t('commentsView.aiDraftApply') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Filter bar -->
    <div class="card">
      <div class="flex flex-wrap gap-4">
        <!-- Platform filter -->
        <div class="flex-1 min-w-[200px]">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.filterPlatform') }}
          </label>
          <select
            v-model="filters.platform"
            class="input"
            @change="handleFilterChange"
          >
            <option value="ALL">{{ $t('commentsView.filterAllPlatforms') }}</option>
            <option value="YOUTUBE">YouTube</option>
            <option value="INSTAGRAM">Instagram</option>
            <option value="TWITTER">X (Twitter)</option>
            <option value="FACEBOOK">Facebook</option>
            <option value="THREADS">Threads</option>
            <option value="LINKEDIN">LinkedIn</option>
            <option value="WORDPRESS">WordPress</option>
            <option value="TUMBLR">Tumblr</option>
            <option value="VIMEO">Vimeo</option>
            <option value="DAILYMOTION">Dailymotion</option>
            <option value="TIKTOK">TikTok</option>
            <option value="NAVER_CLIP">Naver Clip</option>
            <option value="PINTEREST">Pinterest</option>
          </select>
        </div>

        <!-- Sentiment filter -->
        <div class="flex-1 min-w-[200px]">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.filterSentiment') }}
          </label>
          <select
            v-model="filters.sentiment"
            class="input"
            @change="handleFilterChange"
          >
            <option value="ALL">{{ $t('commentsView.filterAll') }}</option>
            <option value="positive">{{ $t('commentsView.sentimentPositive') }}</option>
            <option value="neutral">{{ $t('commentsView.sentimentNeutral') }}</option>
            <option value="negative">{{ $t('commentsView.sentimentNegative') }}</option>
          </select>
        </div>

        <!-- Search -->
        <div class="flex-1 min-w-[200px]">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.filterSearch') }}
          </label>
          <input
            v-model="filters.searchText"
            type="text"
            :placeholder="$t('commentsView.filterSearchPlaceholder')"
            class="input"
            @keyup.enter="handleFilterChange"
          />
        </div>
      </div>

      <!-- Sort + Date range -->
      <div class="mt-4 flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('commentsView.sortLabel') }}</span>
          <button
            v-for="option in sortOptions"
            :key="option.value"
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="
              sortBy === option.value
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
            "
            @click="sortBy = option.value as typeof sortBy"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="card">
      <div class="flex items-center justify-center py-12">
        <div class="h-8 w-8 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
      </div>
    </div>

    <!-- Empty state -->
    <div v-else-if="sortedComments.length === 0" class="card">
      <div class="flex flex-col items-center justify-center py-12 text-center">
        <ChatBubbleLeftEllipsisIcon class="mb-3 h-12 w-12 text-gray-400" />
        <p class="text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ commentStats.total === 0 ? $t('commentsView.emptyComments') : $t('commentsView.emptyFiltered') }}
        </p>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ commentStats.total === 0 ? $t('commentsView.emptyCommentsHint') : $t('commentsView.emptyFilteredHint') }}
        </p>
      </div>
    </div>

    <!-- Comment list -->
    <div v-else class="space-y-4">
      <div
        v-for="comment in sortedComments"
        :key="comment.id"
        class="relative"
      >
        <!-- 선택 체크박스 -->
        <div class="absolute left-2 top-3 z-10">
          <input
            type="checkbox"
            :checked="selectedCommentIds.includes(comment.id)"
            class="h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
            @change="commentsStore.toggleCommentSelection(comment.id)"
          />
        </div>
        <div class="pl-8">
          <CommentCard
            :comment="comment"
            :capabilities="capabilities"
            @reply="handleReply"
            @hide="handleHide"
            @pin="handlePin"
            @delete="handleDelete"
          />
          <!-- AI 답변 패널 (선택된 댓글에 표시) -->
          <CommentAiReplyPanel
            v-if="activeAiPanelId === comment.id"
            :comment="comment"
            @use="handleAiReplyUse"
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

    <!-- Pagination -->
    <div
      v-if="totalPages > 1"
      class="flex items-center justify-between"
    >
      <p class="text-sm text-gray-600 dark:text-gray-400">
        {{ page * pageSize + 1 }}–{{ Math.min((page + 1) * pageSize, totalCount) }} / {{ totalCount }}
      </p>
      <div class="flex gap-2">
        <button
          class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
          :disabled="!hasPrevPage"
          @click="commentsStore.prevPage()"
        >
          {{ $t('commentsView.pagination.previous') }}
        </button>
        <button
          class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
          :disabled="!hasNextPage"
          @click="commentsStore.nextPage()"
        >
          {{ $t('commentsView.pagination.next') }}
        </button>
      </div>
    </div>
    </div>

    <!-- 일괄 처리 액션 바 (하단 고정) -->
    <Transition name="slide-up">
      <div
        v-if="selectedCommentIds.length > 0"
        class="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 flex items-center gap-3 rounded-2xl border border-gray-200 bg-white px-5 py-3 shadow-xl dark:border-gray-700 dark:bg-gray-900"
      >
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('commentsView.batch.selected', { count: selectedCommentIds.length }) }}
        </span>
        <div class="h-4 w-px bg-gray-300 dark:bg-gray-600" />
        <button
          class="btn-primary text-xs"
          :disabled="batchActionLoading"
          @click="showBatchReplyModal = true"
        >
          {{ $t('commentsView.batch.reply') }}
        </button>
        <button
          class="btn-secondary text-xs"
          :disabled="batchActionLoading"
          @click="handleBatchHide"
        >
          {{ $t('commentsView.batch.hide') }}
        </button>
        <button
          class="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
          @click="commentsStore.clearSelection()"
        >
          {{ $t('commentsView.batch.cancel') }}
        </button>
      </div>
    </Transition>

    <!-- 일괄 답변 모달 -->
    <BaseModal v-model="showBatchReplyModal" :title="$t('commentsView.batch.replyModalTitle')">
      <div class="space-y-3">
        <textarea
          v-model="batchReplyText"
          rows="4"
          class="input-field w-full"
          :placeholder="$t('commentsView.batch.replyPlaceholder', { count: selectedCommentIds.length })"
        />
      </div>
      <template #footer>
        <button class="btn-secondary" @click="showBatchReplyModal = false">
          {{ $t('commentsView.batch.cancel') }}
        </button>
        <button
          class="btn-primary text-sm"
          :disabled="!batchReplyText.trim() || batchActionLoading"
          @click="handleBatchReply"
        >
          {{ $t('commentsView.batch.confirm') }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ChatBubbleLeftEllipsisIcon,
  ArrowPathIcon,
  SparklesIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import { storeToRefs } from 'pinia'
import { useCommentsStore } from '@/stores/comments'
import CommentCard from '@/components/comments/CommentCard.vue'
import CommentAiReplyPanel from '@/components/comments/CommentAiReplyPanel.vue'
import KeywordCloudSection from '@/components/comments/KeywordCloudSection.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { SentimentTrendPoint } from '@/types/comment'

const { t } = useI18n({ useScope: 'global' })
const commentsStore = useCommentsStore()

const {
  filters,
  loading,
  syncing,
  filteredComments,
  commentStats,
  capabilities,
  lastSyncedAt,
  totalCount,
  page,
  pageSize,
  totalPages,
  hasNextPage,
  hasPrevPage,
  sentimentTrendData,
  sentimentTrendLoading,
  faqData,
  faqLoading,
  batchDrafts,
  batchDraftLoading,
  // 신규 스토어 상태
  selectedCommentIds,
  crisisStatus,
  keywordCloud,
  keywordCloudLoading,
  batchActionLoading,
  featureUnavailable,
} = storeToRefs(commentsStore)

const sortBy = ref<'recent' | 'likes' | 'replies'>('recent')
const trendDays = ref(30)
const crisisDismissed = ref(false)
const activeAiPanelId = ref<number | null>(null)
const showBatchReplyModal = ref(false)
const batchReplyText = ref('')

const sortOptions = [
  { value: 'recent', label: t('commentsView.sortRecent') },
  { value: 'likes', label: t('commentsView.sortLikes') },
  { value: 'replies', label: t('commentsView.sortReplies') },
]

const sortedComments = computed(() => {
  const comments = [...filteredComments.value]

  // Sort pinned comments to top first
  const pinnedComments = comments.filter((c) => c.isPinned)
  const unpinnedComments = comments.filter((c) => !c.isPinned)

  switch (sortBy.value) {
    case 'likes':
      unpinnedComments.sort((a, b) => b.likeCount - a.likeCount)
      break
    case 'replies':
      unpinnedComments.sort((a, b) => b.replyCount - a.replyCount)
      break
    default:
      unpinnedComments.sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )
  }

  return [...pinnedComments, ...unpinnedComments]
})

const toggleAiPanel = (id: number) => {
  activeAiPanelId.value = activeAiPanelId.value === id ? null : id
}

const getPercent = (value: number, point: SentimentTrendPoint) => {
  const total = point.positive + point.neutral + point.negative
  return total > 0 ? Math.round((value / total) * 100) : 0
}

const formatTrendDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

const loadTrend = (days: number) => {
  trendDays.value = days
  commentsStore.fetchSentimentTrend(days)
}

const handleFilterChange = () => {
  commentsStore.fetchComments()
}

const handleSync = async () => {
  await commentsStore.syncComments()
}

const handleReply = async (id: number, text: string) => {
  await commentsStore.replyToComment(id, text)
}

const handleHide = (id: number) => {
  commentsStore.hideComment(id)
}

const handlePin = (id: number) => {
  commentsStore.pinComment(id)
}

const handleDelete = (id: number) => {
  commentsStore.deleteComment(id)
}

const handleBatchAiDraft = async () => {
  if (selectedCommentIds.value.length === 0) return
  await commentsStore.generateBatchDrafts(selectedCommentIds.value)
}

const handleAiReplyUse = async (commentId: number, text: string) => {
  await commentsStore.replyToComment(commentId, text)
  activeAiPanelId.value = null
}

const handleBatchReply = async () => {
  if (!batchReplyText.value.trim()) return
  await commentsStore.batchReply(selectedCommentIds.value, batchReplyText.value.trim())
  showBatchReplyModal.value = false
  batchReplyText.value = ''
}

const handleBatchHide = async () => {
  await commentsStore.batchHide(selectedCommentIds.value)
}

const handleKeywordCloudLoad = (days: number) => {
  commentsStore.fetchKeywordCloud(undefined, days)
}

const handleKeywordFilter = (keyword: string) => {
  filters.value.searchText = keyword
  commentsStore.fetchComments()
}

const headerDescription = computed(() => {
  const base = t('commentsView.totalComments', { count: commentStats.value.total })
  if (lastSyncedAt.value) {
    return base + ' · ' + t('commentsView.lastSync', { time: formatSyncTime(lastSyncedAt.value) })
  }
  return base
})

const formatSyncTime = (iso: string) => {
  try {
    const date = new Date(iso)
    return date.toLocaleString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

// Debounced search
let searchTimeout: ReturnType<typeof setTimeout>
watch(() => filters.value.searchText, () => {
  clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    commentsStore.fetchComments()
  }, 500)
})

onMounted(() => {
  commentsStore.fetchComments()
  commentsStore.fetchSentimentTrend(trendDays.value)
  commentsStore.fetchCrisisDetection()
})

onUnmounted(() => {
  clearTimeout(searchTimeout)
})
</script>

<style scoped>
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateX(-50%) translateY(16px);
  opacity: 0;
}
</style>
