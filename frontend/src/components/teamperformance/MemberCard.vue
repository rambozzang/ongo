<script setup lang="ts">
import {
  StarIcon,
  FireIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import type { TeamMemberPerformance } from '@/types/teamPerformance'

defineProps<{
  member: TeamMemberPerformance
}>()

const getCompletionColor = (rate: number) => {
  if (rate >= 90) return 'bg-green-500'
  if (rate >= 75) return 'bg-blue-500'
  if (rate >= 60) return 'bg-yellow-500'
  return 'bg-red-500'
}

const getInitials = (name: string) => {
  return name.slice(0, 1)
}

const avatarColors = [
  'bg-blue-500',
  'bg-green-500',
  'bg-purple-500',
  'bg-pink-500',
  'bg-orange-500',
  'bg-teal-500',
  'bg-indigo-500',
]

const getAvatarColor = (id: number) => avatarColors[id % avatarColors.length]
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: 아바타 + 이름 + 역할 -->
    <div class="mb-4 flex items-center gap-3">
      <div
        v-if="member.avatar"
        class="h-10 w-10 flex-shrink-0 overflow-hidden rounded-full"
      >
        <img :src="member.avatar" :alt="member.name" class="h-full w-full object-cover" />
      </div>
      <div
        v-else
        class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full text-sm font-bold text-white"
        :class="getAvatarColor(member.id)"
      >
        {{ getInitials(member.name) }}
      </div>
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ member.name }}
        </h3>
        <p class="truncate text-xs text-gray-500 dark:text-gray-400">
          {{ member.role }}
        </p>
      </div>
      <!-- 연속 일수 배지 -->
      <div
        v-if="member.streak > 0"
        class="flex items-center gap-1 rounded-full bg-orange-100 px-2 py-0.5 dark:bg-orange-900/30"
      >
        <FireIcon class="h-3.5 w-3.5 text-orange-500" />
        <span class="text-xs font-semibold text-orange-700 dark:text-orange-300">
          {{ member.streak }}일
        </span>
      </div>
    </div>

    <!-- 완료율 프로그레스바 -->
    <div class="mb-4">
      <div class="mb-1.5 flex items-center justify-between">
        <span class="text-xs text-gray-500 dark:text-gray-400">완료율</span>
        <span class="text-xs font-semibold text-gray-700 dark:text-gray-300">
          {{ member.completionRate.toFixed(1) }}%
        </span>
      </div>
      <div class="h-2 w-full rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-2 rounded-full transition-all duration-500"
          :class="getCompletionColor(member.completionRate)"
          :style="{ width: `${Math.min(member.completionRate, 100)}%` }"
        />
      </div>
    </div>

    <!-- 메트릭 그리드 -->
    <div class="mb-3 grid grid-cols-2 gap-3">
      <div>
        <div class="flex items-center gap-1">
          <CheckCircleIcon class="h-3 w-3 text-gray-400" />
          <p class="text-xs text-gray-500 dark:text-gray-400">완료 작업</p>
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ member.tasksCompleted }} / {{ member.tasksAssigned }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">콘텐츠 수</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ member.contentProduced }}건
        </p>
      </div>
    </div>

    <!-- 평점 별 -->
    <div class="border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-0.5">
          <template v-for="i in 5" :key="i">
            <StarIconSolid
              v-if="i <= Math.floor(member.rating)"
              class="h-4 w-4 text-yellow-400"
            />
            <StarIcon
              v-else
              class="h-4 w-4 text-gray-300 dark:text-gray-600"
            />
          </template>
          <span class="ml-1 text-xs font-semibold text-gray-700 dark:text-gray-300">
            {{ member.rating.toFixed(1) }}
          </span>
        </div>
        <span class="text-xs text-gray-500 dark:text-gray-400">
          정시 완료 {{ member.onTimeRate }}%
        </span>
      </div>
    </div>
  </div>
</template>
