<template>
  <EmptyState
    v-if="featureUnavailable"
    :title="$t('commentsView.planRequiredTitle')"
    :description="$t('commentsView.planRequiredDescription')"
    action-label="플랜 확인하기"
    action-to="/subscription"
  />

  <div v-else class="space-y-6">
    <!-- 툴바: 요약 + 액션 -->
    <div class="flex flex-wrap items-center justify-between gap-3">
      <p class="text-sm text-gray-600 dark:text-gray-400">{{ summaryText }}</p>
      <div class="flex flex-wrap items-center gap-2">
        <button
          class="btn-secondary inline-flex items-center gap-2"
          data-testid="batch-ai-draft-button"
          :disabled="batchDraftLoading || batchDraftCreditBlocked || selectedCommentIds.length === 0"
          @click="handleBatchAiDraft"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ batchDraftLoading ? $t('commentsView.aiBatchGenerating') : $t('commentsView.aiBatchReply', { count: selectedCommentIds.length }) }}
        </button>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="syncing"
          @click="commentsStore.syncComments()"
        >
          <ArrowPathIcon class="h-4 w-4" :class="{ 'animate-spin': syncing }" />
          {{ syncing ? $t('commentsView.syncing') : $t('commentsView.syncComments') }}
        </button>
      </div>
    </div>

    <CommentCrisisBanner :status="crisisStatus" />

    <!-- 크레딧 부족 차단 블록: 일괄 생성 실패 시에만 노출 -->
    <div
      v-if="batchDraftCreditBlocked"
      class="flex flex-col gap-2 rounded-lg border border-warning bg-warning-subtle px-4 py-3"
      role="alert"
    >
      <p class="text-body text-warning-strong">{{ $t('commentsView.batchCreditBlocked') }}</p>
      <button
        type="button"
        data-testid="batch-credit-cta"
        class="btn-primary inline-flex w-full items-center justify-center gap-2"
        @click="openBatchCreditModal"
      >
        {{ $t('commentsView.batchChargeCredits') }}
      </button>
    </div>

    <CommentSentimentPanel
      :stats="commentStats"
      :trend="sentimentTrendData"
      :loading="sentimentTrendLoading"
      :days="trendDays"
      @change-days="loadTrend"
    />

    <KeywordCloudSection
      :keywords="keywordCloud"
      :loading="keywordCloudLoading"
      @load="(days: number) => commentsStore.fetchKeywordCloud(undefined, days)"
      @filter="handleKeywordFilter"
    />

    <CommentFaqSection
      :data="faqData"
      :loading="faqLoading"
      :faq-credit-blocked="commentsStore.faqCreditBlocked"
      @analyze="commentsStore.fetchFaqClusters()"
      @credit="openFaqCreditModal"
    />

    <CommentBatchDraftList
      :drafts="batchDrafts"
      @apply="handleReply"
      @close="commentsStore.batchDrafts = []"
    />

    <CommentFilterBar
      :platform="filters.platform"
      :sentiment="filters.sentiment"
      :search-text="filters.searchText"
      :sort-by="sortBy"
      @update:platform="(v) => { filters.platform = v; commentsStore.fetchComments() }"
      @update:sentiment="(v) => { filters.sentiment = v; commentsStore.fetchComments() }"
      @update:search-text="(v) => (filters.searchText = v)"
      @update:sort-by="(v) => (sortBy = v)"
      @submit="commentsStore.fetchComments()"
    />

    <CommentList
      :comments="sortedComments"
      :capabilities="capabilities"
      :selected-ids="selectedCommentIds"
      :loading="loading"
      :is-unfiltered="commentStats.total === 0"
      @reply="handleReply"
      @hide="commentsStore.hideComment"
      @pin="commentsStore.pinComment"
      @delete="commentsStore.deleteComment"
      @toggle-select="commentsStore.toggleCommentSelection"
      @ai-reply-use="handleReply"
    />

    <CommentPagination
      :page="page"
      :page-size="pageSize"
      :total-count="totalCount"
      :total-pages="totalPages"
      :has-prev-page="hasPrevPage"
      :has-next-page="hasNextPage"
      @prev="commentsStore.prevPage()"
      @next="commentsStore.nextPage()"
    />

    <CommentBatchBar
      :count="selectedCommentIds.length"
      :loading="batchActionLoading"
      @reply="(text: string) => commentsStore.batchReply(selectedCommentIds, text)"
      @hide="commentsStore.batchHide(selectedCommentIds)"
      @clear="commentsStore.clearSelection()"
    />

    <CreditPurchaseModal v-model="showCreditModal" @purchase="onCreditPurchase" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ArrowPathIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import { useCommentsStore } from '@/stores/comments'
import { useCreditStore } from '@/stores/credit'
import type { CommentSentiment } from '@/types/comment'
import type { Platform } from '@/types/channel'
import CommentCrisisBanner from './CommentCrisisBanner.vue'
import CommentSentimentPanel from './CommentSentimentPanel.vue'
import CommentFaqSection from './CommentFaqSection.vue'
import CommentBatchDraftList from './CommentBatchDraftList.vue'
import CommentFilterBar, { type CommentSortBy } from './CommentFilterBar.vue'
import CommentList from './CommentList.vue'
import CommentPagination from './CommentPagination.vue'
import CommentBatchBar from './CommentBatchBar.vue'
import KeywordCloudSection from './KeywordCloudSection.vue'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const props = withDefaults(
  defineProps<{
    /** 필터·정렬·페이지 상태를 URL 쿼리에 반영할지 여부 */
    syncQuery?: boolean
  }>(),
  { syncQuery: false },
)

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const commentsStore = useCommentsStore()
const creditStore = useCreditStore()

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
  batchDraftCreditBlocked,
  selectedCommentIds,
  crisisStatus,
  keywordCloud,
  keywordCloudLoading,
  batchActionLoading,
  featureUnavailable,
} = storeToRefs(commentsStore)

const sortBy = ref<CommentSortBy>('recent')
const trendDays = ref(30)
  const showCreditModal = ref(false)
  const creditModalContext = ref<'batch' | 'faq' | null>(null)

const sortedComments = computed(() => {
  const list = [...filteredComments.value]
  const pinned = list.filter((c) => c.isPinned)
  const unpinned = list.filter((c) => !c.isPinned)

  switch (sortBy.value) {
    case 'likes':
      unpinned.sort((a, b) => b.likeCount - a.likeCount)
      break
    case 'replies':
      unpinned.sort((a, b) => b.replyCount - a.replyCount)
      break
    default:
      unpinned.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  }

  return [...pinned, ...unpinned]
})

const formatSyncTime = (iso: string) => {
  try {
    return new Date(iso).toLocaleString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

const summaryText = computed(() => {
  const base = t('commentsView.totalComments', { count: commentStats.value.total })
  if (lastSyncedAt.value) {
    return `${base} · ${t('commentsView.lastSync', { time: formatSyncTime(lastSyncedAt.value) })}`
  }
  return base
})

const loadTrend = (days: number) => {
  trendDays.value = days
  commentsStore.fetchSentimentTrend(days)
}

const handleReply = async (id: number, text: string) => {
  await commentsStore.replyToComment(id, text)
}

const handleBatchAiDraft = async () => {
  if (selectedCommentIds.value.length === 0) return
  // 차단 상태에서는 반복 요청을 막는다. 사용자가 충전 CTA 로 잔액을 채운 뒤
  // 직접 다시 눌러야만 재시도된다 (auto re-run 금지).
  if (batchDraftCreditBlocked.value) return
  await commentsStore.generateBatchDrafts(selectedCommentIds.value)
}

const openBatchCreditModal = () => {
  // 배치 CTA 에서 연 모달은 구매 시 일괄 생성 차단만 해제한다.
  creditModalContext.value = 'batch'
  showCreditModal.value = true
}

const openFaqCreditModal = () => {
  // FAQ CTA 에서 연 모달은 구매 시 FAQ 차단만 해제한다.
  creditModalContext.value = 'faq'
  showCreditModal.value = true
}

const onCreditPurchase = async () => {
  await creditStore.fetchBalance()
  // whichever CTA opened the modal, release only that blocked state. No auto re-run of
  // batch generation or FAQ clustering.
  if (creditModalContext.value === 'batch') {
    commentsStore.batchDraftCreditBlocked = false
  } else if (creditModalContext.value === 'faq') {
    commentsStore.faqCreditBlocked = false
  }
  creditModalContext.value = null
}

const handleKeywordFilter = (keyword: string) => {
  filters.value.searchText = keyword
  commentsStore.fetchComments()
}

// ---- URL 쿼리 동기화 ----
const currentQueryState = () => ({
  platform: filters.value.platform === 'ALL' ? undefined : filters.value.platform,
  sentiment: filters.value.sentiment === 'ALL' ? undefined : filters.value.sentiment,
  q: filters.value.searchText || undefined,
  sort: sortBy.value === 'recent' ? undefined : sortBy.value,
  page: page.value > 0 ? String(page.value + 1) : undefined,
})

const readString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)

// 쿼리 → 상태 반영 중에는 상태 감시자가 되받아치지 않도록 잠근다
let applyingQuery = false

const applyQuery = async () => {
  applyingQuery = true
  const q = route.query
  filters.value.platform = (readString(q.platform) as Platform | undefined) ?? 'ALL'
  filters.value.sentiment = (readString(q.sentiment) as CommentSentiment | undefined) ?? 'ALL'
  filters.value.searchText = readString(q.q) ?? ''
  sortBy.value = (readString(q.sort) as CommentSortBy | undefined) ?? 'recent'
  const parsedPage = Number.parseInt(readString(q.page) ?? '1', 10)
  page.value = Number.isFinite(parsedPage) && parsedPage > 1 ? parsedPage - 1 : 0
  await nextTick()
  applyingQuery = false
}

const writeQuery = () => {
  const next: Record<string, unknown> = { ...route.query, ...currentQueryState() }
  Object.keys(next).forEach((key) => {
    if (next[key] === undefined) delete next[key]
  })
  if (JSON.stringify(next) === JSON.stringify(route.query)) return
  router.replace({ query: next as Record<string, string> })
}

// 검색어 디바운스 (기존 CommentsView 동작 유지)
let searchTimeout: ReturnType<typeof setTimeout>
watch(
  () => filters.value.searchText,
  () => {
    if (applyingQuery) return
    clearTimeout(searchTimeout)
    searchTimeout = setTimeout(() => {
      commentsStore.fetchComments()
    }, 500)
  },
)

watch(
  [() => filters.value.platform, () => filters.value.sentiment, () => filters.value.searchText, sortBy, page],
  () => {
    if (props.syncQuery && !applyingQuery) writeQuery()
  },
)

// 뒤로/앞으로 가기 대응 — 쿼리가 현재 상태와 달라졌을 때만 재적용
watch(
  () => route.query,
  () => {
    if (!props.syncQuery) return
    const desired = currentQueryState()
    const same = (Object.keys(desired) as (keyof typeof desired)[]).every(
      (key) => (readString(route.query[key]) ?? undefined) === desired[key],
    )
    if (same) return
    void applyQuery()
    commentsStore.fetchComments(false)
  },
)

onMounted(() => {
  if (props.syncQuery) void applyQuery()
  commentsStore.fetchComments(!props.syncQuery)
  commentsStore.fetchSentimentTrend(trendDays.value)
  commentsStore.fetchCrisisDetection()
})

onUnmounted(() => {
  clearTimeout(searchTimeout)
})
</script>
