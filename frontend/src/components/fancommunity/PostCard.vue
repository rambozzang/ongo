<script setup lang="ts">
import {
  HeartIcon,
  ChatBubbleLeftIcon,
  ShareIcon,
  MapPinIcon,
  PaperClipIcon,
  CheckBadgeIcon,
} from '@heroicons/vue/24/outline'
import type { CommunityPost, PostType } from '@/types/fanCommunity'

defineProps<{
  post: CommunityPost
}>()

const emit = defineEmits<{
  click: [post: CommunityPost]
  like: [id: number]
  pin: [id: number]
}>()

const typeConfig: Record<PostType, { bg: string; text: string; label: string }> = {
  ANNOUNCEMENT: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: '공지' },
  DISCUSSION: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: '토론' },
  POLL: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-300', label: '투표' },
  QNA: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: 'Q&A' },
  BEHIND_SCENES: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: '비하인드' },
  FAN_ART: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300', label: '팬아트' },
}

const getTypeStyle = (type: PostType) => typeConfig[type] ?? typeConfig.DISCUSSION

const formatDate = (iso: string) => {
  try {
    const date = new Date(iso)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMs / 3600000)
    const diffDays = Math.floor(diffMs / 86400000)

    if (diffMins < 1) return '방금 전'
    if (diffMins < 60) return `${diffMins}분 전`
    if (diffHours < 24) return `${diffHours}시간 전`
    if (diffDays < 7) return `${diffDays}일 전`
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
  } catch {
    return iso
  }
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="emit('click', post)"
  >
    <!-- Top: Type badge + Pin + Date -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span
        :class="[getTypeStyle(post.type).bg, getTypeStyle(post.type).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ getTypeStyle(post.type).label }}
      </span>
      <button
        v-if="post.isPinned"
        class="inline-flex items-center gap-0.5 text-orange-500"
        @click.stop="emit('pin', post.id)"
      >
        <MapPinIcon class="h-3.5 w-3.5" />
        <span class="text-[10px] font-medium">고정됨</span>
      </button>
      <span class="ml-auto text-xs text-gray-400 dark:text-gray-500">
        {{ formatDate(post.createdAt) }}
      </span>
    </div>

    <!-- Author -->
    <div class="mb-2 flex items-center gap-2">
      <div
        class="flex h-7 w-7 items-center justify-center rounded-full bg-gray-200 text-xs font-semibold text-gray-600 dark:bg-gray-700 dark:text-gray-300"
      >
        {{ post.authorName.charAt(0) }}
      </div>
      <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ post.authorName }}</span>
      <CheckBadgeIcon
        v-if="post.isCreator"
        class="h-4 w-4 text-blue-500"
      />
    </div>

    <!-- Title + Content preview -->
    <h3 class="mb-1 text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ post.title }}
    </h3>
    <p class="mb-3 line-clamp-2 text-sm text-gray-600 dark:text-gray-400">
      {{ post.content }}
    </p>

    <!-- Tags -->
    <div v-if="post.tags.length > 0" class="mb-3 flex flex-wrap gap-1.5">
      <span
        v-for="tag in post.tags"
        :key="tag"
        class="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400"
      >
        #{{ tag }}
      </span>
    </div>

    <!-- Footer: Likes, Comments, Shares + Attachments -->
    <div class="flex items-center gap-4 border-t border-gray-100 pt-3 dark:border-gray-800">
      <button
        class="inline-flex items-center gap-1 text-xs text-gray-500 transition-colors hover:text-red-500 dark:text-gray-400 dark:hover:text-red-400"
        @click.stop="emit('like', post.id)"
      >
        <HeartIcon class="h-4 w-4" />
        {{ post.likes.toLocaleString('ko-KR') }}
      </button>
      <div class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <ChatBubbleLeftIcon class="h-4 w-4" />
        {{ post.comments.toLocaleString('ko-KR') }}
      </div>
      <div class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <ShareIcon class="h-4 w-4" />
        {{ post.shares.toLocaleString('ko-KR') }}
      </div>
      <div
        v-if="post.attachments.length > 0"
        class="ml-auto inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400"
      >
        <PaperClipIcon class="h-3.5 w-3.5" />
        {{ post.attachments.length }}
      </div>
    </div>
  </div>
</template>
