<script setup lang="ts">
import { computed } from 'vue'
import type { ApprovalRequest } from '@/types/approval'
import ApprovalChainProgress from './ApprovalChainProgress.vue'
import {
  ClockIcon,
  CheckCircleIcon,
  XCircleIcon,
  PencilSquareIcon,
  CalendarDaysIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  requests: ApprovalRequest[]
}>()

const emit = defineEmits<{
  click: [request: ApprovalRequest]
  approve: [id: string]
  reject: [id: string]
  'request-revision': [id: string]
  resubmit: [id: string]
}>()

interface KanbanColumn {
  key: string
  label: string
  icon: typeof ClockIcon
  colorClass: string
  headerBg: string
  items: ApprovalRequest[]
}

const columns = computed<KanbanColumn[]>(() => [
  {
    key: 'pending',
    label: '검토 대기',
    icon: ClockIcon,
    colorClass: 'text-yellow-600 dark:text-yellow-400',
    headerBg: 'border-yellow-400',
    items: props.requests.filter((r) => r.status === 'pending'),
  },
  {
    key: 'revision_requested',
    label: '수정 요청',
    icon: PencilSquareIcon,
    colorClass: 'text-orange-600 dark:text-orange-400',
    headerBg: 'border-orange-400',
    items: props.requests.filter((r) => r.status === 'revision_requested'),
  },
  {
    key: 'approved',
    label: '승인됨',
    icon: CheckCircleIcon,
    colorClass: 'text-green-600 dark:text-green-400',
    headerBg: 'border-green-400',
    items: props.requests.filter((r) => r.status === 'approved'),
  },
  {
    key: 'rejected',
    label: '반려됨',
    icon: XCircleIcon,
    colorClass: 'text-red-600 dark:text-red-400',
    headerBg: 'border-red-400',
    items: props.requests.filter((r) => r.status === 'rejected'),
  },
])

const platformLabels: Record<string, string> = {
  youtube: 'YT',
  tiktok: 'TK',
  instagram: 'IG',
  naverclip: 'NC',
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

  if (diffMin < 1) return '방금'
  if (diffMin < 60) return `${diffMin}분`
  if (diffHour < 24) return `${diffHour}시간`
  return `${diffDay}일`
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
    <div
      v-for="col in columns"
      :key="col.key"
      class="flex flex-col rounded-xl border border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900"
    >
      <!-- Column Header -->
      <div
        class="flex items-center justify-between border-t-2 px-4 py-3"
        :class="col.headerBg"
      >
        <div class="flex items-center gap-2">
          <component :is="col.icon" class="h-4 w-4" :class="col.colorClass" />
          <span class="text-sm font-semibold text-gray-900 dark:text-white">
            {{ col.label }}
          </span>
        </div>
        <span
          class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-gray-200 text-[11px] font-semibold text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          {{ col.items.length }}
        </span>
      </div>

      <!-- Column Content -->
      <div class="flex-1 space-y-2 overflow-y-auto p-2" style="max-height: 600px">
        <div
          v-for="request in col.items"
          :key="request.id"
          class="cursor-pointer rounded-lg border border-gray-200 bg-white p-3 transition-all hover:border-primary-300 hover:shadow-sm dark:border-gray-700 dark:bg-gray-800 dark:hover:border-primary-600"
          @click="emit('click', request)"
        >
          <!-- Title -->
          <h4 class="line-clamp-2 text-sm font-medium text-gray-900 dark:text-white">
            {{ request.videoTitle }}
          </h4>

          <!-- Platforms -->
          <div class="mt-1.5 flex flex-wrap gap-1">
            <span
              v-for="p in request.platforms"
              :key="p"
              class="rounded px-1.5 py-0.5 text-[10px] font-medium"
              :class="platformColors[p] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'"
            >
              {{ platformLabels[p] || p }}
            </span>
          </div>

          <!-- Scheduled -->
          <div
            v-if="request.scheduledAt"
            class="mt-1.5 flex items-center gap-1 text-[11px] text-gray-500 dark:text-gray-400"
          >
            <CalendarDaysIcon class="h-3 w-3" />
            {{ formatDate(request.scheduledAt) }}
          </div>

          <!-- Requester + Time -->
          <div class="mt-2 flex items-center justify-between">
            <div class="flex items-center gap-1.5">
              <div
                class="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full text-[9px] font-bold text-white"
                :style="{ backgroundColor: request.requesterAvatar || '#6B7280' }"
              >
                {{ request.requesterName.charAt(0) }}
              </div>
              <span class="text-[11px] text-gray-600 dark:text-gray-400">
                {{ request.requesterName }}
              </span>
            </div>
            <span class="text-[11px] text-gray-400 dark:text-gray-500">
              {{ timeAgo(request.requestedAt) }} 전
            </span>
          </div>

          <!-- Approval Chain Progress (SLA Countdown) -->
          <ApprovalChainProgress :approval-id="Number(request.id)" />

          <!-- Revision Note -->
          <div
            v-if="request.revisionNote && request.status === 'revision_requested'"
            class="mt-2 rounded bg-orange-50 p-1.5 text-[11px] text-orange-700 dark:bg-orange-900/20 dark:text-orange-300"
          >
            {{ request.revisionNote }}
          </div>

          <!-- Quick Actions -->
          <div class="mt-2 flex gap-1.5" @click.stop>
            <template v-if="request.status === 'pending'">
              <button
                class="flex-1 rounded-md bg-green-600 py-1 text-[11px] font-medium text-white hover:bg-green-700"
                @click="emit('approve', request.id)"
              >
                승인
              </button>
              <button
                class="flex-1 rounded-md bg-red-600 py-1 text-[11px] font-medium text-white hover:bg-red-700"
                @click="emit('reject', request.id)"
              >
                반려
              </button>
            </template>
            <template v-if="request.status === 'revision_requested'">
              <button
                class="flex-1 rounded-md bg-primary-600 py-1 text-[11px] font-medium text-white hover:bg-primary-700"
                @click="emit('resubmit', request.id)"
              >
                재요청
              </button>
            </template>
          </div>
        </div>

        <!-- Empty Column -->
        <div
          v-if="col.items.length === 0"
          class="flex flex-col items-center justify-center py-8 text-center"
        >
          <component :is="col.icon" class="h-8 w-8 text-gray-300 dark:text-gray-600" />
          <p class="mt-2 text-xs text-gray-400 dark:text-gray-500">항목 없음</p>
        </div>
      </div>
    </div>
  </div>
</template>
