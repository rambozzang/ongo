import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Comment, CommentSentiment } from '@/types/comment'
import type { Platform } from '@/types/channel'
import { commentsApi } from '@/api/comments'

interface CommentFilters {
  platform: Platform | 'ALL'
  sentiment: CommentSentiment | 'ALL'
  searchText: string
}

export const useCommentsStore = defineStore('comments', () => {
  const comments = ref<Comment[]>([])
  const loading = ref(false)
  const filters = ref<CommentFilters>({
    platform: 'ALL',
    sentiment: 'ALL',
    searchText: '',
  })

  // Actions
  const fetchComments = async () => {
    loading.value = true
    try {
      const params: Record<string, string | number> = { page: 0, size: 100 }
      if (filters.value.platform !== 'ALL') params.platform = filters.value.platform
      if (filters.value.sentiment !== 'ALL') params.sentiment = filters.value.sentiment.toUpperCase()

      const response = await commentsApi.list(params as Parameters<typeof commentsApi.list>[0])
      comments.value = response.comments.map((c) => ({
        id: c.id,
        videoId: c.videoId ?? 0,
        videoTitle: '',
        platform: (c.platform ?? 'YOUTUBE') as Platform,
        author: c.authorName,
        authorAvatar: c.authorAvatarUrl ?? undefined,
        content: c.content,
        likeCount: 0,
        replyCount: c.isReplied ? 1 : 0,
        sentiment: (c.sentiment?.toLowerCase() ?? 'neutral') as CommentSentiment,
        isHidden: false,
        isPinned: false,
        createdAt: c.createdAt ?? new Date().toISOString(),
      }))
    } catch {
      // API failed — show empty list
      comments.value = []
    } finally {
      loading.value = false
    }
  }

  const hideComment = (id: number) => {
    const comment = comments.value.find((c) => c.id === id)
    if (comment) {
      comment.isHidden = !comment.isHidden
    }
  }

  const pinComment = (id: number) => {
    const comment = comments.value.find((c) => c.id === id)
    if (comment) {
      comment.isPinned = !comment.isPinned
    }
  }

  const replyToComment = async (id: number, text: string) => {
    try {
      await commentsApi.reply(id, text)
    } catch {
      // Silently handle
    }
    const comment = comments.value.find((c) => c.id === id)
    if (comment) {
      comment.replyCount++
    }
  }

  const deleteComment = async (id: number) => {
    try {
      await commentsApi.delete(id)
    } catch {
      // Silently handle
    }
    const index = comments.value.findIndex((c) => c.id === id)
    if (index !== -1) {
      comments.value.splice(index, 1)
    }
  }

  // Computed
  const filteredComments = computed(() => {
    let result = comments.value

    if (filters.value.platform !== 'ALL') {
      result = result.filter((c) => c.platform === filters.value.platform)
    }

    if (filters.value.sentiment !== 'ALL') {
      result = result.filter((c) => c.sentiment === filters.value.sentiment)
    }

    if (filters.value.searchText) {
      const searchLower = filters.value.searchText.toLowerCase()
      result = result.filter(
        (c) =>
          c.content.toLowerCase().includes(searchLower) ||
          c.author.toLowerCase().includes(searchLower) ||
          c.videoTitle.toLowerCase().includes(searchLower),
      )
    }

    return result
  })

  const commentStats = computed(() => {
    const total = comments.value.length
    const positive = comments.value.filter((c) => c.sentiment === 'positive').length
    const neutral = comments.value.filter((c) => c.sentiment === 'neutral').length
    const negative = comments.value.filter((c) => c.sentiment === 'negative').length

    return { total, positive, neutral, negative }
  })

  return {
    comments,
    loading,
    filters,
    filteredComments,
    commentStats,
    fetchComments,
    hideComment,
    pinComment,
    replyToComment,
    deleteComment,
  }
})
