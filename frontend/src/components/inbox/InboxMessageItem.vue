<script setup lang="ts">
import { computed } from 'vue'
import type { InboxMessage } from '@/types/inbox'
import { StarIcon } from '@heroicons/vue/24/solid'
import { StarIcon as StarIconOutline } from '@heroicons/vue/24/outline'

interface Props {
  message: InboxMessage
  isSelected: boolean
  isActive: boolean
  showCheckbox?: boolean
  isChecked?: boolean
}

interface Emits {
  (e: 'click'): void
  (e: 'toggle-star'): void
  (e: 'toggle-check'): void
}

const props = withDefaults(defineProps<Props>(), {
  showCheckbox: false,
  isChecked: false,
})

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
  positive: 'bg-green-500',
  neutral: 'bg-gray-400 dark:bg-gray-500',
  negative: 'bg-red-500',
}

const timeAgo = computed(() => {
  const now = new Date()
  const messageDate = new Date(props.message.createdAt)
  const diffMs = now.getTime() - messageDate.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '방금 전'
  if (diffMins < 60) return `${diffMins}분 전`
  if (diffHours < 24) return `${diffHours}시간 전`
  if (diffDays < 7) return `${diffDays}일 전`
  return messageDate.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
})

const truncatedContent = computed(() => {
  if (props.message.content.length > 80) {
    return props.message.content.substring(0, 80) + '...'
  }
  return props.message.content
})

const handleClick = () => {
  emit('click')
}

const handleStarClick = (e: Event) => {
  e.stopPropagation()
  emit('toggle-star')
}

const handleCheckClick = (e: Event) => {
  e.stopPropagation()
  emit('toggle-check')
}
</script>

<template>
  <div
    @click="handleClick"
    class="flex items-start gap-3 p-4 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
    :class="{
      'bg-blue-50 dark:bg-blue-900/20': isActive,
      'bg-white dark:bg-gray-800': !isActive,
    }"
  >
    <!-- Checkbox (when in selection mode) -->
    <div v-if="showCheckbox" class="flex items-center pt-1">
      <input
        type="checkbox"
        :checked="isChecked"
        @click="handleCheckClick"
        class="w-4 h-4 text-blue-600 border-gray-300 dark:border-gray-600 rounded focus:ring-blue-500 dark:focus:ring-blue-600 dark:bg-gray-700"
      />
    </div>

    <!-- Unread Indicator -->
    <div class="flex items-center pt-1">
      <div
        v-if="message.status === 'unread'"
        class="w-2 h-2 bg-blue-600 rounded-full"
      ></div>
      <div v-else class="w-2 h-2"></div>
    </div>

    <!-- Sender Avatar -->
    <img
      :src="message.senderAvatar"
      :alt="message.senderName"
      class="w-10 h-10 rounded-full flex-shrink-0"
    />

    <!-- Message Content -->
    <div class="flex-1 min-w-0">
      <!-- Header -->
      <div class="flex items-center gap-2 mb-1">
        <span
          class="text-sm font-medium px-2 py-0.5 rounded"
          :class="platformConfig[message.platform].bgColor"
        >
          <span :class="platformConfig[message.platform].color">
            {{ platformConfig[message.platform].label }}
          </span>
        </span>
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ typeLabels[message.type] }}
        </span>
        <div
          :class="sentimentConfig[message.sentiment]"
          class="w-2 h-2 rounded-full"
          :title="message.sentiment"
        ></div>
      </div>

      <!-- Sender Name & Video Title -->
      <div class="mb-1">
        <span
          class="text-sm font-semibold text-gray-900 dark:text-white"
          :class="{ 'font-bold': message.status === 'unread' }"
        >
          {{ message.senderName }}
        </span>
        <span
          v-if="message.videoTitle"
          class="text-xs text-gray-500 dark:text-gray-400 ml-2"
        >
          · {{ message.videoTitle }}
        </span>
      </div>

      <!-- Message Preview -->
      <p
        class="text-sm text-gray-600 dark:text-gray-300"
        :class="{ 'font-semibold': message.status === 'unread' }"
      >
        {{ truncatedContent }}
      </p>
    </div>

    <!-- Right Side: Time & Star -->
    <div class="flex flex-col items-end gap-2 flex-shrink-0">
      <span class="text-xs text-gray-500 dark:text-gray-400">{{ timeAgo }}</span>
      <button @click="handleStarClick" class="text-gray-400 hover:text-yellow-500 transition-colors">
        <StarIcon v-if="message.isStarred" class="w-5 h-5 text-yellow-500" />
        <StarIconOutline v-else class="w-5 h-5" />
      </button>
    </div>
  </div>
</template>
