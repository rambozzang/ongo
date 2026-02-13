<script setup lang="ts">
import { computed } from 'vue'
import type { ApprovalRequest } from '@/types/approval'
import {
  CheckCircleIcon,
  XCircleIcon,
  PencilSquareIcon,
  ClockIcon,
  CalendarDaysIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  request: ApprovalRequest
  role?: 'requester' | 'reviewer'
}>()

const emit = defineEmits<{
  click: [request: ApprovalRequest]
  approve: [id: string]
  reject: [id: string]
  'request-revision': [id: string]
  resubmit: [id: string]
}>()

const statusConfig = computed(() => {
  switch (props.request.status) {
    case 'pending':
      return {
        label: '검토 대기',
        bgClass: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
        dotClass: 'bg-yellow-500',
      }
    case 'approved':
      return {
        label: '승인됨',
        bgClass: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
        dotClass: 'bg-green-500',
      }
    case 'rejected':
      return {
        label: '반려됨',
        bgClass: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
        dotClass: 'bg-red-500',
      }
    case 'revision_requested':
      return {
        label: '수정 요청',
        bgClass: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
        dotClass: 'bg-orange-500',
      }
    default:
      return {
        label: '',
        bgClass: '',
        dotClass: '',
      }
  }
})

const platformLabels: Record<string, string> = {
  youtube: 'YouTube',
  tiktok: 'TikTok',
  instagram: 'Instagram',
  naverclip: 'Naver Clip',
}

const platformColors: Record<string, string> = {
  youtube: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  tiktok: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
  instagram: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  naverclip: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
}

function timeAgo(dateStr: string): string {
  const now = Date.now()
  const date = new Date(dateStr).getTime()
  const diffMs = now - date
  const diffMin = Math.floor(diffMs / (1000 * 60))
  const diffHour = Math.floor(diffMs / (1000 * 60 * 60))
  const diffDay = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffMin < 1) return '방금 전'
  if (diffMin < 60) return `${diffMin}분 전`
  if (diffHour < 24) return `${diffHour}시간 전`
  return `${diffDay}일 전`
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
}
</script>

<template>
  <div
    class="group cursor-pointer rounded-xl border border-gray-200 bg-white p-4 transition-all hover:border-primary-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800 dark:hover:border-primary-600"
    @click="emit('click', request)"
  >
    <div class="flex items-start gap-4">
      <!-- Thumbnail -->
      <div
        class="h-20 w-32 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700"
      >
        <img
          v-if="request.videoThumbnail"
          :src="request.videoThumbnail"
          :alt="request.videoTitle"
          class="h-full w-full object-cover"
        />
        <div
          v-else
          class="flex h-full w-full items-center justify-center text-gray-400 dark:text-gray-500"
        >
          <svg
            class="h-8 w-8"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.5"
              d="m15.75 10.5 4.72-4.72a.75.75 0 0 1 1.28.53v11.38a.75.75 0 0 1-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 0 0 2.25-2.25v-9a2.25 2.25 0 0 0-2.25-2.25h-9A2.25 2.25 0 0 0 2.25 7.5v9a2.25 2.25 0 0 0 2.25 2.25Z"
            />
          </svg>
        </div>
      </div>

      <!-- Content -->
      <div class="min-w-0 flex-1">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <!-- Title -->
            <h3
              class="truncate text-sm font-semibold text-gray-900 dark:text-white"
            >
              {{ request.videoTitle }}
            </h3>

            <!-- Requester and time -->
            <div class="mt-1 flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <div
                class="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full text-[10px] font-bold text-white"
                :style="{ backgroundColor: request.requesterAvatar || '#6B7280' }"
              >
                {{ request.requesterName.charAt(0) }}
              </div>
              <span>{{ request.requesterName }}</span>
              <span class="text-gray-300 dark:text-gray-600">·</span>
              <span>{{ timeAgo(request.requestedAt) }}</span>
            </div>

            <!-- Platforms -->
            <div class="mt-2 flex flex-wrap items-center gap-1.5">
              <span
                v-for="platform in request.platforms"
                :key="platform"
                class="inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium"
                :class="platformColors[platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'"
              >
                {{ platformLabels[platform] || platform }}
              </span>
            </div>

            <!-- Scheduled date -->
            <div
              v-if="request.scheduledAt"
              class="mt-1.5 flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400"
            >
              <CalendarDaysIcon class="h-3.5 w-3.5" />
              <span>예약: {{ formatDate(request.scheduledAt) }}</span>
            </div>
          </div>

          <!-- Status Badge -->
          <div class="flex flex-shrink-0 flex-col items-end gap-2">
            <span
              class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
              :class="statusConfig.bgClass"
            >
              <span
                class="h-1.5 w-1.5 rounded-full"
                :class="statusConfig.dotClass"
              />
              {{ statusConfig.label }}
            </span>
          </div>
        </div>

        <!-- Revision Note -->
        <div
          v-if="request.revisionNote && (request.status === 'rejected' || request.status === 'revision_requested')"
          class="mt-2 rounded-lg bg-orange-50 p-2 text-xs text-orange-700 dark:bg-orange-900/20 dark:text-orange-300"
        >
          <span class="font-medium">사유:</span> {{ request.revisionNote }}
        </div>

        <!-- Action Buttons -->
        <div class="mt-3 flex items-center gap-2" @click.stop>
          <!-- Reviewer actions for pending -->
          <template v-if="request.status === 'pending' && role === 'reviewer'">
            <button
              class="inline-flex items-center gap-1 rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400"
              @click="emit('approve', request.id)"
            >
              <CheckCircleIcon class="h-3.5 w-3.5" />
              승인
            </button>
            <button
              class="inline-flex items-center gap-1 rounded-lg bg-red-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-red-700 dark:bg-red-500 dark:hover:bg-red-400"
              @click="emit('reject', request.id)"
            >
              <XCircleIcon class="h-3.5 w-3.5" />
              반려
            </button>
            <button
              class="inline-flex items-center gap-1 rounded-lg border border-orange-300 px-3 py-1.5 text-xs font-medium text-orange-700 transition-colors hover:bg-orange-50 dark:border-orange-600 dark:text-orange-400 dark:hover:bg-orange-900/20"
              @click="emit('request-revision', request.id)"
            >
              <PencilSquareIcon class="h-3.5 w-3.5" />
              수정 요청
            </button>
          </template>

          <!-- Requester actions for revision_requested -->
          <template
            v-if="request.status === 'revision_requested' && role === 'requester'"
          >
            <button
              class="inline-flex items-center gap-1 rounded-lg bg-primary-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-400"
              @click="emit('resubmit', request.id)"
            >
              <ClockIcon class="h-3.5 w-3.5" />
              수정 완료 · 재요청
            </button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
