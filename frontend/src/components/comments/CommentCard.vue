<template>
  <div
    class="card border-l-4 transition-all hover:shadow-md"
    :class="[
      sentimentBorderClass,
      comment.isHidden ? 'opacity-60' : '',
      comment.isPinned ? 'ring-2 ring-primary-300 dark:ring-primary-700' : '',
    ]"
  >
    <!-- Pin indicator -->
    <div v-if="comment.isPinned" class="mb-2 flex items-center gap-1 text-xs text-primary-600 dark:text-primary-400">
      <span>📌</span>
      <span class="font-medium">고정된 댓글</span>
    </div>

    <div class="flex gap-3">
      <!-- Avatar -->
      <div
        v-if="comment.authorAvatar"
        class="h-10 w-10 shrink-0 overflow-hidden rounded-full"
      >
        <img :src="comment.authorAvatar" :alt="comment.author" class="h-full w-full object-cover" />
      </div>
      <div
        v-else
        class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-white"
        :style="{ backgroundColor: avatarColor }"
      >
        {{ comment.author.charAt(0).toUpperCase() }}
      </div>

      <!-- Content -->
      <div class="min-w-0 flex-1">
        <!-- Header -->
        <div class="mb-1 flex flex-wrap items-center gap-2">
          <component
            :is="comment.authorChannelUrl ? 'a' : 'span'"
            :href="comment.authorChannelUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="font-medium text-gray-900 dark:text-gray-100"
            :class="{ 'hover:underline': comment.authorChannelUrl }"
          >
            {{ comment.author }}
          </component>
          <PlatformBadge :platform="comment.platform" />
          <span class="text-xs text-gray-500 dark:text-gray-400">{{ relativeTime }}</span>
          <span
            v-if="comment.syncedAt"
            class="text-xs text-gray-400 dark:text-gray-500"
            :title="'동기화: ' + comment.syncedAt"
          >
            · 동기화됨
          </span>
        </div>

        <!-- Video title link -->
        <router-link
          v-if="comment.videoTitle"
          :to="`/videos/${comment.videoId}`"
          class="mb-2 block text-xs text-primary-600 hover:underline dark:text-primary-400"
        >
          {{ comment.videoTitle }}
        </router-link>

        <!-- Comment content -->
        <p
          class="mb-2 text-sm text-gray-700 dark:text-gray-300"
          :class="comment.isHidden ? 'line-through' : ''"
        >
          <span v-if="!expanded && comment.content.length > 200">
            {{ comment.content.slice(0, 200) }}...
            <button
              class="ml-1 font-medium text-primary-600 hover:underline dark:text-primary-400"
              @click="expanded = true"
            >
              더 보기
            </button>
          </span>
          <span v-else>
            {{ comment.content }}
          </span>
        </p>

        <!-- Reply content -->
        <div
          v-if="comment.isReplied && comment.replyContent"
          class="mb-2 rounded-lg bg-gray-50 p-2 text-sm dark:bg-gray-800"
        >
          <span class="text-xs font-medium text-primary-600 dark:text-primary-400">내 답글:</span>
          <p class="mt-1 text-gray-700 dark:text-gray-300">{{ comment.replyContent }}</p>
        </div>

        <!-- Metadata -->
        <div class="mb-2 flex flex-wrap items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
          <span class="flex items-center gap-1">
            <svg class="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" clip-rule="evenodd" />
            </svg>
            {{ comment.likeCount }}
          </span>
          <span>댓글 {{ comment.replyCount }}</span>
          <span
            class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium"
            :class="sentimentBadgeClass"
          >
            {{ sentimentLabel }}
          </span>
        </div>

        <!-- Actions (visible on hover) -->
        <div
          v-if="!isReplying"
          class="flex flex-wrap gap-2 opacity-0 transition-opacity group-hover:opacity-100"
          :class="showActions ? 'opacity-100' : ''"
        >
          <button
            v-if="platformCaps?.canReply && !comment.isReplied"
            class="text-xs font-medium text-gray-600 hover:text-primary-600 dark:text-gray-400 dark:hover:text-primary-400"
            @click="isReplying = true"
          >
            답글
          </button>
          <button
            class="text-xs font-medium text-gray-600 hover:text-primary-600 dark:text-gray-400 dark:hover:text-primary-400"
            @click="emit('hide', comment.id)"
          >
            {{ comment.isHidden ? '표시' : '숨기기' }}
          </button>
          <button
            class="text-xs font-medium text-gray-600 hover:text-primary-600 dark:text-gray-400 dark:hover:text-primary-400"
            @click="emit('pin', comment.id)"
          >
            {{ comment.isPinned ? '고정 해제' : '고정' }}
          </button>
          <button
            v-if="platformCaps?.canDelete"
            class="text-xs font-medium text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
            @click="handleDelete"
          >
            삭제
          </button>
        </div>

        <!-- Reply form -->
        <CommentReplyForm
          v-if="isReplying"
          @submit="handleReply"
          @cancel="isReplying = false"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Comment, CommentCapabilities } from '@/types/comment'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import CommentReplyForm from './CommentReplyForm.vue'

const props = defineProps<{
  comment: Comment
  capabilities?: Record<string, CommentCapabilities>
}>()

const emit = defineEmits<{
  reply: [id: number, text: string]
  hide: [id: number]
  pin: [id: number]
  delete: [id: number]
}>()

const expanded = ref(false)
const isReplying = ref(false)
const showActions = ref(false)

const platformCaps = computed(() => {
  if (!props.capabilities) return { canReply: true, canDelete: true, canLike: false, canHide: true, canListComments: true }
  return props.capabilities[props.comment.platform] ?? { canReply: false, canDelete: false, canLike: false, canHide: true, canListComments: false }
})

const relativeTime = computed(() => {
  try {
    const now = Date.now()
    const diff = now - new Date(props.comment.createdAt).getTime()
    const minutes = Math.floor(diff / 60000)
    if (minutes < 1) return '방금 전'
    if (minutes < 60) return `${minutes}분 전`
    const hours = Math.floor(minutes / 60)
    if (hours < 24) return `${hours}시간 전`
    const days = Math.floor(hours / 24)
    if (days < 30) return `${days}일 전`
    const months = Math.floor(days / 30)
    return `${months}개월 전`
  } catch {
    return props.comment.createdAt
  }
})

const avatarColor = computed(() => {
  const colors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981',
    '#3b82f6', '#ef4444', '#06b6d4', '#84cc16', '#f97316',
  ]
  const charCode = props.comment.author.charCodeAt(0)
  return colors[charCode % colors.length]
})

const sentimentBorderClass = computed(() => {
  switch (props.comment.sentiment) {
    case 'positive':
      return 'border-green-500 dark:border-green-600'
    case 'negative':
      return 'border-red-500 dark:border-red-600'
    default:
      return 'border-gray-300 dark:border-gray-600'
  }
})

const sentimentBadgeClass = computed(() => {
  switch (props.comment.sentiment) {
    case 'positive':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'negative':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
})

const sentimentLabel = computed(() => {
  switch (props.comment.sentiment) {
    case 'positive':
      return '긍정'
    case 'negative':
      return '부정'
    default:
      return '중립'
  }
})

const handleReply = (text: string) => {
  emit('reply', props.comment.id, text)
  isReplying.value = false
}

const handleDelete = () => {
  if (confirm('이 댓글을 삭제하시겠습니까? 플랫폼에서도 삭제됩니다.')) {
    emit('delete', props.comment.id)
  }
}
</script>

<style scoped>
.card:hover .opacity-0 {
  opacity: 1;
}
</style>
