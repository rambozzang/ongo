import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
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

interface CommentFilters {
  platform: Platform | 'ALL'
  sentiment: CommentSentiment | 'ALL'
  searchText: string
  startDate: string
  endDate: string
}

export const useCommentsStore = defineStore('comments', () => {
  const comments = ref<Comment[]>([])
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

  const filters = ref<CommentFilters>({
    platform: 'ALL',
    sentiment: 'ALL',
    searchText: '',
    startDate: '',
    endDate: '',
  })

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
    } catch {
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
    } catch {
      return null
    } finally {
      syncing.value = false
    }
  }

  const hideComment = async (id: number) => {
    try {
      await commentsApi.hide(id)
      const comment = comments.value.find((c) => c.id === id)
      if (comment) {
        comment.isHidden = !comment.isHidden
      }
    } catch {
      // Silently handle
    }
  }

  const pinComment = async (id: number) => {
    try {
      await commentsApi.pin(id)
      const comment = comments.value.find((c) => c.id === id)
      if (comment) {
        comment.isPinned = !comment.isPinned
      }
    } catch {
      // Silently handle
    }
  }

  const replyToComment = async (id: number, text: string) => {
    try {
      await commentsApi.reply(id, text)
      const comment = comments.value.find((c) => c.id === id)
      if (comment) {
        comment.isReplied = true
        comment.replyContent = text
        comment.replyCount++
      }
    } catch {
      // Silently handle
    }
  }

  const deleteComment = async (id: number) => {
    try {
      await commentsApi.delete(id)
      const index = comments.value.findIndex((c) => c.id === id)
      if (index !== -1) {
        comments.value.splice(index, 1)
        totalCount.value--
      }
    } catch {
      // Silently handle
    }
  }

  const fetchSentimentTrend = async (days = 30) => {
    sentimentTrendLoading.value = true
    try {
      sentimentTrendData.value = await commentsApi.sentimentTrend(days)
    } catch {
      sentimentTrendData.value = null
    } finally {
      sentimentTrendLoading.value = false
    }
  }

  const fetchFaqClusters = async () => {
    faqLoading.value = true
    try {
      faqData.value = await commentsApi.faqClusters()
    } catch {
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
    } catch {
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
    } catch {
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
    } catch {
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
    } catch {
      return null
    } finally {
      batchActionLoading.value = false
    }
  }

  const fetchCrisisDetection = async () => {
    crisisLoading.value = true
    try {
      crisisStatus.value = await commentsApi.crisisDetection()
    } catch {
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
    } catch {
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
  }
})
