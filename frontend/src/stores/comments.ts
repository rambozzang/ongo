import { defineStore } from 'pinia'
import { ref, shallowRef, computed } from 'vue'
import type {
  Comment,
  CommentSentiment,
  CommentStats,
  CommentCapabilities,
  SentimentTrendResponse,
  FaqClusterResponse,
  AiDraftItem,
  AiReplyGenerateResponse,
  CrisisDetectionResult,
  KeywordCloudItem,
} from '@/types/comment'
import type { Platform } from '@/types/channel'
import { commentsApi } from '@/api/comments'
import { useNotificationStore } from '@/stores/notification'
import { PLAN_LIMIT_EXCEEDED, matchesCode } from '@/composables/usePlanLimit'

interface CommentFilters {
  platform: Platform | 'ALL'
  sentiment: CommentSentiment | 'ALL'
  searchText: string
  startDate: string
  endDate: string
}

export const useCommentsStore = defineStore('comments', () => {
  const comments = shallowRef<Comment[]>([])
  const loading = ref(false)
  const syncing = ref(false)
  const totalCount = ref(0)
  const page = ref(0)
  const pageSize = ref(20)
  const stats = ref<CommentStats>({ total: 0, positive: 0, neutral: 0, negative: 0 })
  const capabilities = ref<Record<string, CommentCapabilities>>({})
  const lastSyncedAt = ref<string | null>(null)
  const sentimentTrendData = ref<SentimentTrendResponse | null>(null)
  const sentimentTrendLoading = ref(false)
  const faqData = ref<FaqClusterResponse | null>(null)
  const faqLoading = ref(false)
  const batchDrafts = ref<AiDraftItem[]>([])
  const batchDraftLoading = ref(false)

  // 신규: AI 개별 답변, 위기 감지, 키워드 클라우드, 일괄 선택
  const aiReplies = ref<Record<number, AiReplyGenerateResponse>>({})
  const aiReplyLoading = ref<Record<number, boolean>>({})
  const crisisStatus = ref<CrisisDetectionResult | null>(null)
  const crisisLoading = ref(false)
  const keywordCloud = ref<KeywordCloudItem[]>([])
  const keywordCloudLoading = ref(false)
  const selectedCommentIds = ref<number[]>([])
  const batchActionLoading = ref(false)
  const featureUnavailable = ref(false)

  const filters = ref<CommentFilters>({
    platform: 'ALL',
    sentiment: 'ALL',
    searchText: '',
    startDate: '',
    endDate: '',
  })

  const isPlanAccessError = (error: any) => {
    // 403 은 이 기능이 플랜에 없다는 뜻이라 그대로 두고, 한도 코드 판별만 공통으로 옮겼다.
    return error?.response?.status === 403 || error?.statusCode === 403 ||
      matchesCode(error, PLAN_LIMIT_EXCEEDED)
  }

  const handleRequestError = (error: any, fallback: string) => {
    if (isPlanAccessError(error)) {
      featureUnavailable.value = true
      return
    }
    const notificationStore = useNotificationStore()
    notificationStore.error(error?.response?.data?.message || error?.message || fallback)
  }

  // Actions
  const fetchComments = async (resetPage = true) => {
    loading.value = true
    if (resetPage) page.value = 0

    try {
      const params: Record<string, string | number> = {
        page: page.value,
        size: pageSize.value,
      }
      if (filters.value.platform !== 'ALL') params.platform = filters.value.platform
      if (filters.value.sentiment !== 'ALL') params.sentiment = filters.value.sentiment.toUpperCase()
      if (filters.value.searchText) params.searchText = filters.value.searchText
      if (filters.value.startDate) params.startDate = filters.value.startDate
      if (filters.value.endDate) params.endDate = filters.value.endDate

      const response = await commentsApi.list(params as Parameters<typeof commentsApi.list>[0])
      comments.value = response.comments.map((c) => ({
        id: c.id,
        videoId: c.videoId ?? 0,
        videoTitle: '',
        platform: (c.platform ?? 'YOUTUBE') as Platform,
        platformCommentId: c.platformCommentId ?? undefined,
        author: c.authorName,
        authorAvatar: c.authorAvatarUrl ?? undefined,
        authorChannelUrl: c.authorChannelUrl ?? undefined,
        content: c.content,
        likeCount: c.likeCount ?? 0,
        replyCount: c.replyCount ?? 0,
        sentiment: (c.sentiment?.toLowerCase() ?? 'neutral') as CommentSentiment,
        isReplied: c.isReplied ?? false,
        isHidden: c.isHidden ?? false,
        isPinned: c.isPinned ?? false,
        replyContent: c.replyContent ?? undefined,
        repliedAt: c.repliedAt ?? undefined,
        syncedAt: c.syncedAt ?? undefined,
        createdAt: c.createdAt ?? new Date().toISOString(),
      }))
      totalCount.value = response.totalCount
      stats.value = response.stats
      capabilities.value = response.capabilities
    } catch (error: any) {
      handleRequestError(error, '댓글을 불러오지 못했습니다.')
      comments.value = []
      totalCount.value = 0
    } finally {
      loading.value = false
    }
  }

  const syncComments = async () => {
    syncing.value = true
    try {
      const result = await commentsApi.syncAll()
      lastSyncedAt.value = new Date().toISOString()
      // Refresh the list after sync
      await fetchComments()
      return result
    } catch (error: any) {
      handleRequestError(error, '댓글 동기화에 실패했습니다.')
      return null
    } finally {
      syncing.value = false
    }
  }

  const hideComment = async (id: number) => {
    try {
      await commentsApi.hide(id)
      comments.value = comments.value.map((c) =>
        c.id === id ? { ...c, isHidden: !c.isHidden } : c
      )
    } catch (error: any) {
      handleRequestError(error, '댓글 처리 중 오류가 발생했습니다.')
    }
  }

  const pinComment = async (id: number) => {
    try {
      await commentsApi.pin(id)
      comments.value = comments.value.map((c) =>
        c.id === id ? { ...c, isPinned: !c.isPinned } : c
      )
    } catch (error: any) {
      handleRequestError(error, '댓글 처리 중 오류가 발생했습니다.')
    }
  }

  const replyToComment = async (id: number, text: string) => {
    try {
      await commentsApi.reply(id, text)
      comments.value = comments.value.map((c) =>
        c.id === id
          ? { ...c, isReplied: true, replyContent: text, replyCount: c.replyCount + 1 }
          : c
      )
    } catch (error: any) {
      handleRequestError(error, '댓글 답변에 실패했습니다.')
    }
  }

  const deleteComment = async (id: number) => {
    try {
      await commentsApi.delete(id)
      comments.value = comments.value.filter((c) => c.id !== id)
      totalCount.value--
    } catch (error: any) {
      handleRequestError(error, '댓글 삭제에 실패했습니다.')
    }
  }

  const fetchSentimentTrend = async (days = 30) => {
    sentimentTrendLoading.value = true
    try {
      sentimentTrendData.value = await commentsApi.sentimentTrend(days)
    } catch (error: any) {
      handleRequestError(error, '감정 트렌드를 불러오지 못했습니다.')
      sentimentTrendData.value = null
    } finally {
      sentimentTrendLoading.value = false
    }
  }

  const fetchFaqClusters = async () => {
    faqLoading.value = true
    try {
      faqData.value = await commentsApi.faqClusters()
    } catch (error: any) {
      handleRequestError(error, 'FAQ 분석에 실패했습니다.')
      faqData.value = null
    } finally {
      faqLoading.value = false
    }
  }

  const generateBatchDrafts = async (commentIds: number[], tone = 'FRIENDLY') => {
    batchDraftLoading.value = true
    try {
      const response = await commentsApi.batchAiDraft(commentIds, tone)
      batchDrafts.value = response.drafts
      return response
    } catch (error: any) {
      handleRequestError(error, 'AI 답변 초안 생성에 실패했습니다.')
      batchDrafts.value = []
      return null
    } finally {
      batchDraftLoading.value = false
    }
  }

  const generateAiReply = async (commentId: number, tone = 'FRIENDLY') => {
    aiReplyLoading.value = { ...aiReplyLoading.value, [commentId]: true }
    try {
      const result = await commentsApi.aiReplyGenerate(commentId, tone)
      aiReplies.value = { ...aiReplies.value, [commentId]: result }
      return result
    } catch (error: any) {
      handleRequestError(error, 'AI 답변 생성에 실패했습니다.')
      return null
    } finally {
      aiReplyLoading.value = { ...aiReplyLoading.value, [commentId]: false }
    }
  }

  const batchReply = async (commentIds: number[], replyText: string) => {
    batchActionLoading.value = true
    try {
      const result = await commentsApi.batchReply(commentIds, replyText)
      selectedCommentIds.value = []
      await fetchComments(false)
      return result
    } catch (error: any) {
      handleRequestError(error, '일괄 답변 처리에 실패했습니다.')
      return null
    } finally {
      batchActionLoading.value = false
    }
  }

  const batchHide = async (commentIds: number[]) => {
    batchActionLoading.value = true
    try {
      const result = await commentsApi.batchHide(commentIds)
      selectedCommentIds.value = []
      await fetchComments(false)
      return result
    } catch (error: any) {
      handleRequestError(error, '일괄 숨김 처리에 실패했습니다.')
      return null
    } finally {
      batchActionLoading.value = false
    }
  }

  const fetchCrisisDetection = async () => {
    crisisLoading.value = true
    try {
      crisisStatus.value = await commentsApi.crisisDetection()
    } catch (error: any) {
      handleRequestError(error, '위기 감지 결과를 불러오지 못했습니다.')
      crisisStatus.value = null
    } finally {
      crisisLoading.value = false
    }
  }

  const fetchKeywordCloud = async (videoId?: number, days?: number) => {
    keywordCloudLoading.value = true
    try {
      // 백엔드는 Map<String, Int> 형태로 반환 → KeywordCloudItem[] 변환
      const result = await commentsApi.keywordCloud(videoId, days)
      keywordCloud.value = Object.entries(result).map(([keyword, count]) => ({
        keyword,
        count,
        sentiment: 'neutral' as const,
      }))
    } catch (error: any) {
      handleRequestError(error, '키워드 클라우드를 불러오지 못했습니다.')
      keywordCloud.value = []
    } finally {
      keywordCloudLoading.value = false
    }
  }

  const toggleCommentSelection = (id: number) => {
    const idx = selectedCommentIds.value.indexOf(id)
    if (idx === -1) {
      selectedCommentIds.value.push(id)
    } else {
      selectedCommentIds.value.splice(idx, 1)
    }
  }

  const clearSelection = () => {
    selectedCommentIds.value = []
  }

  const nextPage = () => {
    if ((page.value + 1) * pageSize.value < totalCount.value) {
      page.value++
      fetchComments(false)
    }
  }

  const prevPage = () => {
    if (page.value > 0) {
      page.value--
      fetchComments(false)
    }
  }

  // Computed
  const filteredComments = computed(() => comments.value)

  const commentStats = computed(() => stats.value)

  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))

  const hasNextPage = computed(() => (page.value + 1) * pageSize.value < totalCount.value)
  const hasPrevPage = computed(() => page.value > 0)

  return {
    comments,
    loading,
    syncing,
    totalCount,
    page,
    pageSize,
    stats,
    capabilities,
    lastSyncedAt,
    filters,
    filteredComments,
    commentStats,
    totalPages,
    hasNextPage,
    hasPrevPage,
    sentimentTrendData,
    sentimentTrendLoading,
    faqData,
    faqLoading,
    batchDrafts,
    batchDraftLoading,
    fetchComments,
    syncComments,
    hideComment,
    pinComment,
    replyToComment,
    deleteComment,
    fetchSentimentTrend,
    fetchFaqClusters,
    generateBatchDrafts,
    generateAiReply,
    batchReply,
    batchHide,
    fetchCrisisDetection,
    fetchKeywordCloud,
    toggleCommentSelection,
    clearSelection,
    nextPage,
    prevPage,
    aiReplies,
    aiReplyLoading,
    crisisStatus,
    crisisLoading,
    keywordCloud,
    keywordCloudLoading,
    selectedCommentIds,
    batchActionLoading,
    featureUnavailable,
  }
})
