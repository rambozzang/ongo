<script setup lang="ts">
import { computed } from 'vue'
import type { InboxMessage } from '@/types/inbox'
import {
  EnvelopeOpenIcon,
  EnvelopeIcon,
  StarIcon as StarIconSolid,
  ArchiveBoxIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarIconFilled } from '@heroicons/vue/24/solid'
import InboxReplyForm from './InboxReplyForm.vue'

interface Props {
  message: InboxMessage | null
}

interface Emits {
  (e: 'toggle-read'): void
  (e: 'toggle-star'): void
  (e: 'archive'): void
  (e: 'delete'): void
  (e: 'reply', content: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const platformConfig = {
  YOUTUBE: {
    color: 'text-red-600 dark:text-red-400',
    bgColor: 'bg-red-100 dark:bg-red-900/30',
    label: 'YouTube',
  },
  TIKTOK: {
    color: 'text-gray-900 dark:text-white',
    bgColor: 'bg-gray-100 dark:bg-gray-700',
    label: 'TikTok',
  },
  INSTAGRAM: {
    color: 'text-pink-600 dark:text-pink-400',
    bgColor: 'bg-pink-100 dark:bg-pink-900/30',
    label: 'Instagram',
  },
  NAVER_CLIP: {
    color: 'text-green-600 dark:text-green-400',
    bgColor: 'bg-green-100 dark:bg-green-900/30',
    label: '네이버 클립',
  },
}

const typeLabels = {
  comment: '댓글',
  mention: '멘션',
  dm: 'DM',
  reply: '답글',
}

const sentimentConfig = {
  positive: { color: 'bg-green-500', label: '긍정' },
  neutral: { color: 'bg-gray-400 dark:bg-gray-500', label: '중립' },
  negative: { color: 'bg-red-500', label: '부정' },
}

const formattedDate = computed(() => {
  if (!props.message) return ''
  const date = new Date(props.message.createdAt)
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
})

const formattedReplyDate = computed(() => {
  if (!props.message?.repliedAt) return ''
  const date = new Date(props.message.repliedAt)
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
})

const handleReply = (content: string) => {
  emit('reply', content)
}
</script>

<template>
  <div v-if="!message" class="h-full flex items-center justify-center bg-gray-50 dark:bg-gray-900">
    <div class="text-center text-gray-500 dark:text-gray-400">
      <EnvelopeIcon class="w-16 h-16 mx-auto mb-4 opacity-50" />
      <p class="text-lg">메시지를 선택하세요</p>
    </div>
  </div>

  <div v-else class="h-full flex flex-col bg-white dark:bg-gray-800">
    <!-- Header -->
    <div class="border-b border-gray-200 dark:border-gray-700 p-4">
      <!-- Action Buttons -->
      <div class="flex items-center gap-2 mb-4">
        <button
          @click="emit('toggle-read')"
          :title="message.status === 'unread' ? '읽음으로 표시' : '안읽음으로 표시'"
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-600 dark:text-gray-400 transition-colors"
        >
          <EnvelopeOpenIcon v-if="message.status === 'unread'" class="w-5 h-5" />
          <EnvelopeIcon v-else class="w-5 h-5" />
        </button>

        <button
          @click="emit('toggle-star')"
          :title="message.isStarred ? '별표 해제' : '별표'"
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          :class="message.isStarred ? 'text-yellow-500' : 'text-gray-600 dark:text-gray-400'"
        >
          <StarIconFilled v-if="message.isStarred" class="w-5 h-5" />
          <StarIconSolid v-else class="w-5 h-5" />
        </button>

        <button
          @click="emit('archive')"
          title="보관"
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-600 dark:text-gray-400 transition-colors"
        >
          <ArchiveBoxIcon class="w-5 h-5" />
        </button>

        <button
          @click="emit('delete')"
          title="삭제"
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-red-600 dark:text-red-400 transition-colors"
        >
          <TrashIcon class="w-5 h-5" />
        </button>

        <div class="ml-auto flex items-center gap-2">
          <span
            class="text-sm font-medium px-3 py-1 rounded"
            :class="platformConfig[message.platform].bgColor"
          >
            <span :class="platformConfig[message.platform].color">
              {{ platformConfig[message.platform].label }}
            </span>
          </span>
          <span class="text-sm text-gray-500 dark:text-gray-400">
            {{ typeLabels[message.type] }}
          </span>
        </div>
      </div>

      <!-- Sender Info -->
      <div class="flex items-start gap-3">
        <img
          :src="message.senderAvatar"
          :alt="message.senderName"
          class="w-12 h-12 rounded-full flex-shrink-0"
        />
        <div class="flex-1">
          <div class="flex items-center gap-2 mb-1">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white">
              {{ message.senderName }}
            </h3>
            <div
              :class="sentimentConfig[message.sentiment].color"
              class="w-3 h-3 rounded-full"
              :title="sentimentConfig[message.sentiment].label"
            ></div>
          </div>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ formattedDate }}</p>
          <p v-if="message.videoTitle" class="text-sm text-gray-600 dark:text-gray-300 mt-1">
            영상: <span class="font-medium">{{ message.videoTitle }}</span>
          </p>
        </div>
      </div>
    </div>

    <!-- Message Content -->
    <div class="flex-1 overflow-y-auto p-4">
      <div class="bg-gray-50 dark:bg-gray-900 rounded-lg p-4 mb-4">
        <p class="text-gray-900 dark:text-white whitespace-pre-wrap">{{ message.content }}</p>
      </div>

      <!-- Reply History -->
      <div v-if="message.status === 'replied' && message.replyContent" class="mt-6">
        <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">답장 기록</h4>
        <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4 border-l-4 border-blue-500">
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-2">
            {{ formattedReplyDate }}에 답장함
          </p>
          <p class="text-gray-900 dark:text-white whitespace-pre-wrap">
            {{ message.replyContent }}
          </p>
        </div>
      </div>
    </div>

    <!-- Reply Form -->
    <InboxReplyForm
      v-if="message.status !== 'archived'"
      :platform="message.platform"
      @send="handleReply"
    />
  </div>
</template>
