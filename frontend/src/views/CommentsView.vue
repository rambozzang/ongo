<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">댓글 관리</h1>
        <p class="mt-1 text-sm text-gray-600 dark:text-gray-400">
          전체 {{ commentStats.total }}개의 댓글
          <span v-if="lastSyncedAt" class="ml-2 text-xs text-gray-400">
            · 마지막 동기화: {{ formatSyncTime(lastSyncedAt) }}
          </span>
        </p>
      </div>
      <button
        class="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50"
        :disabled="syncing"
        @click="handleSync"
      >
        <ArrowPathIcon
          class="h-4 w-4"
          :class="{ 'animate-spin': syncing }"
        />
        {{ syncing ? '동기화 중...' : '댓글 동기화' }}
      </button>
    </div>

    <PageGuide title="댓글 관리" :items="[
      '댓글 동기화 버튼을 클릭하면 연결된 모든 플랫폼에서 최신 댓글을 가져옵니다 (마지막 동기화 시간 표시)',
      '상단 감정 분석 막대에서 긍정(초록)·중립(회색)·부정(빨간) 댓글 비율을 한눈에 파악하세요',
      '플랫폼 필터·검색·상태 필터로 원하는 댓글을 빠르게 찾고, 감정 배지로 댓글 분위기를 확인하세요',
      '댓글 카드에서 바로 답글을 작성하여 팬과 소통하세요. 답글은 해당 플랫폼에 직접 게시됩니다',
    ]" />

    <!-- Sentiment summary -->
    <div v-if="commentStats.total > 0" class="card">
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
          <span class="text-gray-700 dark:text-gray-300">
            긍정: {{ commentStats.positive }}
            ({{ Math.round((commentStats.positive / commentStats.total) * 100) }}%)
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-gray-400" />
          <span class="text-gray-700 dark:text-gray-300">
            중립: {{ commentStats.neutral }}
            ({{ Math.round((commentStats.neutral / commentStats.total) * 100) }}%)
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-red-500" />
          <span class="text-gray-700 dark:text-gray-300">
            부정: {{ commentStats.negative }}
            ({{ Math.round((commentStats.negative / commentStats.total) * 100) }}%)
          </span>
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
            @change="handleFilterChange"
          >
            <option value="ALL">전체 플랫폼</option>
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
            감정
          </label>
          <select
            v-model="filters.sentiment"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
            @change="handleFilterChange"
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
            placeholder="작성자, 내용 검색..."
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
            @keyup.enter="handleFilterChange"
          />
        </div>
      </div>

      <!-- Sort + Date range -->
      <div class="mt-4 flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
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
          {{ commentStats.total === 0 ? '아직 댓글이 없습니다' : '검색 결과가 없습니다' }}
        </p>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ commentStats.total === 0 ? '"댓글 동기화" 버튼을 눌러 플랫폼 댓글을 가져오세요' : '다른 필터를 시도해보세요' }}
        </p>
      </div>
    </div>

    <!-- Comment list -->
    <div v-else class="space-y-4">
      <CommentCard
        v-for="comment in sortedComments"
        :key="comment.id"
        :comment="comment"
        :capabilities="capabilities"
        @reply="handleReply"
        @hide="handleHide"
        @pin="handlePin"
        @delete="handleDelete"
      />
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
          이전
        </button>
        <button
          class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
          :disabled="!hasNextPage"
          @click="commentsStore.nextPage()"
        >
          다음
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ChatBubbleLeftEllipsisIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { storeToRefs } from 'pinia'
import { useCommentsStore } from '@/stores/comments'
import CommentCard from '@/components/comments/CommentCard.vue'
import PageGuide from '@/components/common/PageGuide.vue'

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
} = storeToRefs(commentsStore)

const sortBy = ref<'recent' | 'likes' | 'replies'>('recent')

const sortOptions = [
  { value: 'recent', label: '최신순' },
  { value: 'likes', label: '좋아요순' },
  { value: 'replies', label: '댓글순' },
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
})
</script>
