<script setup lang="ts">
import { ClockIcon } from '@heroicons/vue/24/outline'
import type { TeamActivity } from '@/types/teamPerformance'

defineProps<{
  activities: TeamActivity[]
}>()

const formatRelativeTime = (timestamp: string) => {
  const now = new Date()
  const date = new Date(timestamp)
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMin / 60)
  const diffDay = Math.floor(diffHour / 24)

  if (diffMin < 1) return '방금 전'
  if (diffMin < 60) return `${diffMin}분 전`
  if (diffHour < 24) return `${diffHour}시간 전`
  if (diffDay < 7) return `${diffDay}일 전`
  return date.toLocaleDateString('ko-KR')
}

const actionColors: Record<string, string> = {
  '콘텐츠 기획 완료': 'bg-blue-500',
  '영상 편집 완료': 'bg-green-500',
  '썸네일 제작': 'bg-purple-500',
  '스크립트 초안 제출': 'bg-orange-500',
  'SNS 포스팅': 'bg-pink-500',
  '기획안 리뷰': 'bg-indigo-500',
  '영상 컬러 그레이딩': 'bg-teal-500',
  '배너 디자인': 'bg-yellow-500',
}

const getActionColor = (action: string) => actionColors[action] ?? 'bg-gray-500'
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <div class="border-b border-gray-200 px-5 py-4 dark:border-gray-700">
      <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">
        최근 활동
      </h3>
    </div>

    <div v-if="activities.length > 0" class="divide-y divide-gray-100 dark:divide-gray-800">
      <div
        v-for="activity in activities"
        :key="activity.id"
        class="flex items-start gap-3 px-5 py-3.5"
      >
        <!-- 타임라인 도트 -->
        <div class="mt-1.5 flex flex-col items-center">
          <div
            class="h-2.5 w-2.5 rounded-full"
            :class="getActionColor(activity.action)"
          />
        </div>

        <!-- 내용 -->
        <div class="min-w-0 flex-1">
          <p class="text-sm text-gray-900 dark:text-gray-100">
            <span class="font-semibold">{{ activity.memberName }}</span>
            <span class="text-gray-500 dark:text-gray-400"> {{ activity.action }} </span>
          </p>
          <p class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">
            {{ activity.target }}
          </p>
        </div>

        <!-- 시간 -->
        <div class="flex flex-shrink-0 items-center gap-1 text-xs text-gray-400 dark:text-gray-500">
          <ClockIcon class="h-3 w-3" />
          {{ formatRelativeTime(activity.timestamp) }}
        </div>
      </div>
    </div>

    <div
      v-else
      class="px-5 py-12 text-center"
    >
      <ClockIcon class="mx-auto mb-2 h-8 w-8 text-gray-400 dark:text-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">최근 활동이 없습니다</p>
    </div>
  </div>
</template>
