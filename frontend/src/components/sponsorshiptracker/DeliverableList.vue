<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleIcon,
  ClockIcon,
  CalendarIcon,
} from '@heroicons/vue/24/outline'
import type { Deliverable, DeliverableType } from '@/types/sponsorshipTracker'

interface Props {
  deliverables: Deliverable[]
}

interface Emits {
  (e: 'toggleComplete', deliverable: Deliverable): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const sortedDeliverables = computed(() => {
  return [...props.deliverables].sort((a, b) => {
    if (a.isCompleted !== b.isCompleted) return a.isCompleted ? 1 : -1
    return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime()
  })
})

const typeConfig: Record<DeliverableType, { label: string; color: string }> = {
  DEDICATED_VIDEO: {
    label: '전용 영상',
    color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  },
  INTEGRATED: {
    label: 'PPL 통합',
    color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  },
  SHORT_FORM: {
    label: '숏폼',
    color: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
  },
  STORY: {
    label: '스토리',
    color: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  },
  POST: {
    label: '게시물',
    color: 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400',
  },
  LIVE_STREAM: {
    label: '라이브',
    color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  },
}

const platformConfig: Record<string, { label: string; color: string }> = {
  YOUTUBE: {
    label: 'YouTube',
    color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  },
  TIKTOK: {
    label: 'TikTok',
    color: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400',
  },
  INSTAGRAM: {
    label: 'Instagram',
    color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
  },
  NAVER_CLIP: {
    label: 'Naver Clip',
    color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  },
}

function getTypeConfig(type: DeliverableType) {
  return typeConfig[type] ?? { label: type, color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' }
}

function getPlatformConfig(platform: string) {
  return platformConfig[platform.toUpperCase()] ?? { label: platform, color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function isDueSoon(dateStr: string): boolean {
  const due = new Date(dateStr)
  const now = new Date()
  const diff = due.getTime() - now.getTime()
  return diff > 0 && diff < 3 * 24 * 60 * 60 * 1000 // 3일 이내
}

function isOverdue(dateStr: string, isCompleted: boolean): boolean {
  if (isCompleted) return false
  return new Date(dateStr).getTime() < new Date().getTime()
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm overflow-hidden">
    <!-- Header -->
    <div class="px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          산출물 목록
        </h3>
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ deliverables.filter(d => d.isCompleted).length }}/{{ deliverables.length }} 완료
        </span>
      </div>
    </div>

    <!-- Empty state -->
    <div v-if="deliverables.length === 0" class="p-8 text-center">
      <ClockIcon class="w-10 h-10 text-gray-400 dark:text-gray-500 mx-auto mb-2" />
      <p class="text-sm text-gray-500 dark:text-gray-400">등록된 산출물이 없습니다</p>
    </div>

    <!-- Deliverable list -->
    <div v-else class="divide-y divide-gray-200 dark:divide-gray-700 max-h-96 overflow-y-auto">
      <div
        v-for="deliverable in sortedDeliverables"
        :key="deliverable.id"
        class="flex items-center gap-3 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors"
        :class="deliverable.isCompleted ? 'opacity-60' : ''"
      >
        <!-- Checkbox -->
        <button
          class="flex-shrink-0"
          @click="emit('toggleComplete', deliverable)"
        >
          <CheckCircleIcon
            class="w-5 h-5 transition-colors"
            :class="deliverable.isCompleted
              ? 'text-green-500 dark:text-green-400'
              : 'text-gray-300 dark:text-gray-600 hover:text-green-400 dark:hover:text-green-500'
            "
          />
        </button>

        <!-- Title & badges -->
        <div class="flex-1 min-w-0">
          <p
            class="text-sm font-medium truncate"
            :class="deliverable.isCompleted
              ? 'text-gray-500 dark:text-gray-500 line-through'
              : 'text-gray-900 dark:text-gray-100'
            "
          >
            {{ deliverable.title }}
          </p>
          <div class="flex items-center gap-1.5 mt-1">
            <!-- Type badge -->
            <span
              :class="['inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium', getTypeConfig(deliverable.type).color]"
            >
              {{ getTypeConfig(deliverable.type).label }}
            </span>
            <!-- Platform badge -->
            <span
              :class="['inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium', getPlatformConfig(deliverable.platform).color]"
            >
              {{ getPlatformConfig(deliverable.platform).label }}
            </span>
          </div>
        </div>

        <!-- Due date -->
        <div class="flex-shrink-0 text-right">
          <div class="flex items-center gap-1 text-xs" :class="[
            isOverdue(deliverable.dueDate, deliverable.isCompleted) ? 'text-red-500 dark:text-red-400' :
            isDueSoon(deliverable.dueDate) && !deliverable.isCompleted ? 'text-yellow-600 dark:text-yellow-400' :
            'text-gray-500 dark:text-gray-400'
          ]">
            <CalendarIcon class="w-3 h-3" />
            <span>{{ formatDate(deliverable.dueDate) }}</span>
          </div>
          <span
            v-if="isOverdue(deliverable.dueDate, deliverable.isCompleted)"
            class="text-[10px] text-red-500 dark:text-red-400 font-medium"
          >
            기한 초과
          </span>
          <span
            v-else-if="deliverable.isCompleted && deliverable.completedAt"
            class="text-[10px] text-green-500 dark:text-green-400"
          >
            {{ formatDate(deliverable.completedAt) }} 완료
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
