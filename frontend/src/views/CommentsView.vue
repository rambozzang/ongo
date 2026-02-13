<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">댓글 관리</h1>
        <p class="mt-1 text-sm text-gray-600 dark:text-gray-400">
          전체 {{ commentStats.total }}개의 댓글
        </p>
      </div>
    </div>

    <!-- Sentiment summary -->
    <div class="card">
      <h3 class="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-300">감정 분석 요약</h3>
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
          <span class="text-gray-700 dark:text-gray-300">긍정: {{ commentStats.positive }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-gray-400" />
          <span class="text-gray-700 dark:text-gray-300">중립: {{ commentStats.neutral }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-red-500" />
          <span class="text-gray-700 dark:text-gray-300">부정: {{ commentStats.negative }}</span>
        </div>
      </div>
    </div>

    <!-- Filter bar -->
    <div class="card">
      <div class="flex flex-wrap gap-4">
        <!-- Platform filter -->
        <div class="flex-1 min-w-[200px]">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            플랫폼
          </label>
          <select
            v-model="filters.platform"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
          >
            <option value="ALL">전체 플랫폼</option>
            <option value="YOUTUBE">YouTube</option>
            <option value="TIKTOK">TikTok</option>
            <option value="INSTAGRAM">Instagram</option>
            <option value="NAVER_CLIP">Naver Clip</option>
          </select>
        </div>

        <!-- Sentiment filter -->
        <div class="flex-1 min-w-[200px]">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            감정
          </label>
          <select
            v-model="filters.sentiment"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
          >
            <option value="ALL">전체</option>
            <option value="positive">긍정</option>
            <option value="neutral">중립</option>
            <option value="negative">부정</option>
          </select>
        </div>

        <!-- Search -->
        <div class="flex-1 min-w-[200px]">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            검색
          </label>
          <input
            v-model="filters.searchText"
            type="text"
            placeholder="작성자, 내용, 영상 제목 검색..."
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
          />
        </div>
      </div>

      <!-- Sort -->
      <div class="mt-4 flex items-center gap-2">
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">정렬:</span>
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
        <p class="text-sm font-medium text-gray-700 dark:text-gray-300">검색 결과가 없습니다</p>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">다른 필터를 시도해보세요</p>
      </div>
    </div>

    <!-- Comment list -->
    <div v-else class="space-y-4">
      <CommentCard
        v-for="comment in sortedComments"
        :key="comment.id"
        :comment="comment"
        @reply="handleReply"
        @hide="handleHide"
        @pin="handlePin"
        @delete="handleDelete"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ChatBubbleLeftEllipsisIcon } from '@heroicons/vue/24/outline'
import { useCommentsStore } from '@/stores/comments'
import CommentCard from '@/components/comments/CommentCard.vue'

const commentsStore = useCommentsStore()

const { filters, loading, filteredComments, commentStats } = commentsStore

const sortBy = ref<'recent' | 'likes' | 'replies'>('recent')

const sortOptions = [
  { value: 'recent', label: '최신순' },
  { value: 'likes', label: '좋아요순' },
  { value: 'replies', label: '댓글순' },
]

const sortedComments = computed(() => {
  const comments = [...filteredComments]

  // Sort pinned comments to top first
  comments.sort((a, b) => {
    if (a.isPinned && !b.isPinned) return -1
    if (!a.isPinned && b.isPinned) return 1
    return 0
  })

  // Then sort by selected criteria
  const unpinnedComments = comments.filter((c) => !c.isPinned)
  const pinnedComments = comments.filter((c) => c.isPinned)

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

onMounted(() => {
  commentsStore.fetchComments()
})
</script>
