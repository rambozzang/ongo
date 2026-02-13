<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRightIcon, PencilIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { AutomationRule } from '@/types/automation'

const props = defineProps<{
  rule: AutomationRule
}>()

const emit = defineEmits<{
  edit: [id: number]
  delete: [id: number]
  toggle: [id: number]
}>()

const triggerLabel = computed(() => {
  const labels: Record<string, string> = {
    video_published: '영상 게시됨',
    views_threshold: '조회수 도달',
    schedule_time: '예약 시간',
    comment_received: '댓글 수신',
    subscriber_milestone: '구독자 달성'
  }
  return labels[props.rule.trigger.type] || props.rule.trigger.type
})

const actionLabels = computed(() => {
  const labels: Record<string, string> = {
    cross_post: '크로스 포스팅',
    send_notification: '알림 전송',
    add_tag: '태그 추가',
    move_to_status: '상태 변경',
    generate_ai_metadata: 'AI 메타데이터 생성'
  }
  return props.rule.actions.map(action => labels[action.type] || action.type)
})

const statusColor = computed(() => {
  const colors: Record<string, { bg: string; text: string; dot: string }> = {
    active: {
      bg: 'bg-green-100 dark:bg-green-900/30',
      text: 'text-green-800 dark:text-green-300',
      dot: 'bg-green-500'
    },
    paused: {
      bg: 'bg-yellow-100 dark:bg-yellow-900/30',
      text: 'text-yellow-800 dark:text-yellow-300',
      dot: 'bg-yellow-500'
    },
    error: {
      bg: 'bg-red-100 dark:bg-red-900/30',
      text: 'text-red-800 dark:text-red-300',
      dot: 'bg-red-500'
    }
  }
  return colors[props.rule.status] || colors.active
})

const statusText = computed(() => {
  const texts: Record<string, string> = {
    active: '활성',
    paused: '일시중지',
    error: '오류'
  }
  return texts[props.rule.status] || props.rule.status
})

const formatDate = (dateString: string | null) => {
  if (!dateString) return '없음'
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '방금 전'
  if (diffMins < 60) return `${diffMins}분 전`
  if (diffHours < 24) return `${diffHours}시간 전`
  if (diffDays < 7) return `${diffDays}일 전`

  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 hover:shadow-md transition-shadow">
    <!-- Header -->
    <div class="flex items-start justify-between mb-4">
      <div class="flex-1">
        <div class="flex items-center gap-3 mb-2">
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white">
            {{ rule.name }}
          </h3>
          <div :class="[statusColor.bg, statusColor.text, 'px-2.5 py-0.5 rounded-full text-xs font-medium flex items-center gap-1.5']">
            <span :class="[statusColor.dot, 'w-1.5 h-1.5 rounded-full']"></span>
            {{ statusText }}
          </div>
        </div>
        <p class="text-sm text-gray-600 dark:text-gray-400">
          {{ rule.description }}
        </p>
      </div>
      <div class="flex items-center gap-2 ml-4">
        <button
          @click="emit('edit', rule.id)"
          class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          title="수정"
        >
          <PencilIcon class="w-5 h-5" />
        </button>
        <button
          @click="emit('delete', rule.id)"
          class="p-2 text-gray-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          title="삭제"
        >
          <TrashIcon class="w-5 h-5" />
        </button>
      </div>
    </div>

    <!-- Trigger → Actions Flow -->
    <div class="flex items-center gap-3 mb-4 py-4 px-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
      <div class="flex items-center gap-2 px-3 py-2 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-lg text-sm font-medium">
        <span>트리거:</span>
        <span class="font-semibold">{{ triggerLabel }}</span>
      </div>

      <ArrowRightIcon class="w-5 h-5 text-gray-400 dark:text-gray-500 flex-shrink-0" />

      <div class="flex items-center gap-2 flex-wrap">
        <div
          v-for="(label, index) in actionLabels"
          :key="index"
          class="px-3 py-2 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 rounded-lg text-sm font-medium"
        >
          <span>액션:</span>
          <span class="font-semibold">{{ label }}</span>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class="flex items-center justify-between pt-4 border-t border-gray-200 dark:border-gray-700">
      <div class="flex items-center gap-6 text-sm text-gray-600 dark:text-gray-400">
        <div>
          <span class="text-gray-500 dark:text-gray-500">실행 횟수:</span>
          <span class="ml-1 font-semibold text-gray-900 dark:text-white">{{ rule.executionCount }}회</span>
        </div>
        <div>
          <span class="text-gray-500 dark:text-gray-500">마지막 실행:</span>
          <span class="ml-1 font-medium">{{ formatDate(rule.lastExecutedAt) }}</span>
        </div>
      </div>

      <label class="relative inline-flex items-center cursor-pointer">
        <input
          type="checkbox"
          :checked="rule.isEnabled"
          @change="emit('toggle', rule.id)"
          class="sr-only peer"
        >
        <div class="w-11 h-6 bg-gray-200 dark:bg-gray-700 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
        <span class="ms-3 text-sm font-medium text-gray-900 dark:text-gray-300">
          {{ rule.isEnabled ? '활성' : '비활성' }}
        </span>
      </label>
    </div>
  </div>
</template>
