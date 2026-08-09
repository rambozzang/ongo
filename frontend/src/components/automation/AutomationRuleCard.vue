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
    VIDEO_UPLOADED: '영상 업로드됨',
    SCHEDULE_DUE: '예약 시간',
    COMMENT_RECEIVED: '댓글 수신',
    ANALYTICS_MILESTONE: '분석 마일스톤',
    CREDIT_LOW: 'AI 크레딧 부족',
    VIEWS_MILESTONE: '조회수 마일스톤',
    VIRAL_DETECTED: '바이럴 감지',
    ENGAGEMENT_DROP: '참여율 하락'
  }
  return labels[props.rule.trigger.type] || props.rule.trigger.type
})

const actionLabels = computed(() => {
  const labels: Record<string, string> = {
    SEND_NOTIFICATION: '알림 전송',
    AUTO_PUBLISH: '자동 게시',
    ADD_TAG: '태그 추가',
    GENERATE_METADATA: 'AI 메타데이터 생성'
  }
  return props.rule.actions.map(action => labels[action.type] || action.type)
})

const statusColor = computed(() => {
  const colors: Record<string, { bg: string; text: string; dot: string }> = {
    active: {
      bg: 'bg-success-subtle',
      text: 'text-success-strong',
      dot: 'bg-success'
    },
    paused: {
      bg: 'bg-warning-subtle',
      text: 'text-warning-strong',
      dot: 'bg-warning'
    },
    error: {
      bg: 'bg-error-subtle',
      text: 'text-error-strong',
      dot: 'bg-error'
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
          <h3 class="text-title font-semibold text-gray-900 dark:text-white">
            {{ rule.name }}
          </h3>
          <div :class="[statusColor.bg, statusColor.text, 'px-2.5 py-0.5 rounded-full text-caption flex items-center gap-1.5']">
            <span :class="[statusColor.dot, 'w-1.5 h-1.5 rounded-full']"></span>
            {{ statusText }}
          </div>
        </div>
        <p class="text-body text-gray-600 dark:text-gray-400">
          {{ rule.description }}
        </p>
      </div>
      <div class="flex items-center gap-2 ml-4">
        <button
          class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          title="수정"
          @click="emit('edit', rule.id)"
        >
          <PencilIcon class="w-5 h-5" />
        </button>
        <button
          class="p-2 text-gray-400 hover:text-error-strong hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          title="삭제"
          @click="emit('delete', rule.id)"
        >
          <TrashIcon class="w-5 h-5" />
        </button>
      </div>
    </div>

    <!-- Trigger → Actions Flow -->
    <div class="flex items-center gap-3 mb-4 py-4 px-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
      <div class="flex items-center gap-2 px-3 py-2 bg-info-subtle text-info-strong rounded-lg text-body font-medium">
        <span>트리거:</span>
        <span class="font-semibold">{{ triggerLabel }}</span>
      </div>

      <ArrowRightIcon class="w-5 h-5 text-gray-400 dark:text-gray-500 flex-shrink-0" />

      <div class="flex items-center gap-2 flex-wrap">
        <div
          v-for="(label, index) in actionLabels"
          :key="index"
          class="px-3 py-2 bg-success-subtle text-success-strong rounded-lg text-body font-medium"
        >
          <span>액션:</span>
          <span class="font-semibold">{{ label }}</span>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class="flex items-center justify-between pt-4 border-t border-gray-200 dark:border-gray-700">
      <div class="flex items-center gap-6 text-body text-gray-600 dark:text-gray-400">
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
          class="sr-only peer"
          @change="emit('toggle', rule.id)"
        >
        <div class="w-11 h-6 bg-gray-200 dark:bg-gray-700 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary-300 dark:peer-focus:ring-primary-800 rounded-full peer peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-primary-600"></div>
        <span class="ms-3 text-body font-medium text-gray-900 dark:text-gray-300">
          {{ rule.isEnabled ? '활성' : '비활성' }}
        </span>
      </label>
    </div>
  </div>
</template>
