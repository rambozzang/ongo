<script setup lang="ts">
import { computed } from 'vue'
import type { ApprovalRequest, ApprovalComment } from '@/types/approval'
import {
  PaperAirplaneIcon,
  CheckCircleIcon,
  XCircleIcon,
  PencilSquareIcon,
  ChatBubbleLeftIcon,
  ArrowPathIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  request: ApprovalRequest
  comments: ApprovalComment[]
}>()

interface TimelineEvent {
  id: string
  type: 'submitted' | 'comment' | 'approved' | 'rejected' | 'revision_requested' | 'resubmitted'
  userName: string
  userAvatar?: string
  text: string
  timestamp: string
  field?: string
}

const events = computed<TimelineEvent[]>(() => {
  const items: TimelineEvent[] = []

  // Request submitted event
  items.push({
    id: `evt-submit-${props.request.id}`,
    type: 'submitted',
    userName: props.request.requesterName,
    userAvatar: props.request.requesterAvatar,
    text: '승인 요청을 제출했습니다.',
    timestamp: props.request.requestedAt,
  })

  // Comments as events
  for (const comment of props.comments) {
    items.push({
      id: `evt-cmt-${comment.id}`,
      type: 'comment',
      userName: comment.userName,
      userAvatar: comment.userAvatar,
      text: comment.content,
      timestamp: comment.createdAt,
      field: comment.field,
    })
  }

  // Decision event
  if (props.request.decidedAt && props.request.status !== 'pending') {
    const decisionType = props.request.status === 'approved'
      ? 'approved'
      : props.request.status === 'rejected'
        ? 'rejected'
        : 'revision_requested'

    const decisionText = props.request.status === 'approved'
      ? '요청을 승인했습니다.'
      : props.request.status === 'rejected'
        ? '요청을 반려했습니다.'
        : '수정을 요청했습니다.'

    items.push({
      id: `evt-decision-${props.request.id}`,
      type: decisionType,
      userName: props.request.reviewerName || '검토자',
      text: decisionText,
      timestamp: props.request.decidedAt,
    })
  }

  // Sort chronologically
  items.sort(
    (a, b) =>
      new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
  )

  return items
})

function getEventIcon(type: TimelineEvent['type']) {
  switch (type) {
    case 'submitted':
      return PaperAirplaneIcon
    case 'comment':
      return ChatBubbleLeftIcon
    case 'approved':
      return CheckCircleIcon
    case 'rejected':
      return XCircleIcon
    case 'revision_requested':
      return PencilSquareIcon
    case 'resubmitted':
      return ArrowPathIcon
    default:
      return ChatBubbleLeftIcon
  }
}

function getEventColorClass(type: TimelineEvent['type']): string {
  switch (type) {
    case 'submitted':
      return 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400'
    case 'comment':
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
    case 'approved':
      return 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400'
    case 'rejected':
      return 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400'
    case 'revision_requested':
      return 'bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400'
    case 'resubmitted':
      return 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
  }
}

function getLineColorClass(type: TimelineEvent['type']): string {
  switch (type) {
    case 'approved':
      return 'bg-green-300 dark:bg-green-700'
    case 'rejected':
      return 'bg-red-300 dark:bg-red-700'
    case 'revision_requested':
      return 'bg-orange-300 dark:bg-orange-700'
    default:
      return 'bg-gray-200 dark:bg-gray-700'
  }
}

function formatTimestamp(dateStr: string): string {
  const date = new Date(dateStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
}
</script>

<template>
  <div class="flow-root">
    <ul class="-mb-8">
      <li
        v-for="(event, index) in events"
        :key="event.id"
        class="relative pb-8"
      >
        <!-- Connecting line -->
        <span
          v-if="index < events.length - 1"
          class="absolute left-4 top-9 -ml-px h-full w-0.5"
          :class="getLineColorClass(event.type)"
        />

        <div class="relative flex items-start gap-3">
          <!-- Icon -->
          <div
            class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full"
            :class="getEventColorClass(event.type)"
          >
            <component :is="getEventIcon(event.type)" class="h-4 w-4" />
          </div>

          <!-- Content -->
          <div class="min-w-0 flex-1 pt-0.5">
            <div class="flex items-center gap-2">
              <div
                v-if="event.userAvatar"
                class="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full text-[10px] font-bold text-white"
                :style="{ backgroundColor: event.userAvatar }"
              >
                {{ event.userName.charAt(0) }}
              </div>
              <span class="text-sm font-medium text-gray-900 dark:text-white">
                {{ event.userName }}
              </span>
              <span class="text-xs text-gray-500 dark:text-gray-400">
                {{ formatTimestamp(event.timestamp) }}
              </span>
            </div>
            <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">
              {{ event.text }}
            </p>
            <span
              v-if="event.field"
              class="mt-1 inline-block rounded bg-gray-100 px-1.5 py-0.5 text-[11px] text-gray-500 dark:bg-gray-700 dark:text-gray-400"
            >
              {{ event.field }}
            </span>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>
