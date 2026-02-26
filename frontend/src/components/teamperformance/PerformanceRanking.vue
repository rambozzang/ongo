<script setup lang="ts">
import { TrophyIcon } from '@heroicons/vue/24/outline'
import type { TeamMemberPerformance } from '@/types/teamPerformance'
import { computed } from 'vue'

const props = defineProps<{
  members: TeamMemberPerformance[]
}>()

const rankedMembers = computed(() =>
  [...props.members].sort((a, b) => b.completionRate - a.completionRate),
)

const getRankBadge = (rank: number) => {
  if (rank === 1) return { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', icon: 'text-yellow-500' }
  if (rank === 2) return { bg: 'bg-gray-100 dark:bg-gray-700', text: 'text-gray-600 dark:text-gray-300', icon: 'text-gray-400' }
  if (rank === 3) return { bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-300', icon: 'text-orange-500' }
  return { bg: 'bg-gray-50 dark:bg-gray-800', text: 'text-gray-500 dark:text-gray-400', icon: 'text-gray-400' }
}

const getCompletionColor = (rate: number) => {
  if (rate >= 90) return 'bg-green-500'
  if (rate >= 75) return 'bg-blue-500'
  if (rate >= 60) return 'bg-yellow-500'
  return 'bg-red-500'
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
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <div class="border-b border-gray-200 px-5 py-4 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <TrophyIcon class="h-5 w-5 text-yellow-500" />
        <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">
          성과 랭킹
        </h3>
      </div>
    </div>

    <div v-if="rankedMembers.length > 0" class="divide-y divide-gray-100 dark:divide-gray-800">
      <div
        v-for="(member, index) in rankedMembers"
        :key="member.id"
        class="flex items-center gap-3 px-5 py-3.5"
      >
        <!-- 순위 뱃지 -->
        <div
          class="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold"
          :class="[getRankBadge(index + 1).bg, getRankBadge(index + 1).text]"
        >
          {{ index + 1 }}
        </div>

        <!-- 아바타 -->
        <div
          v-if="member.avatar"
          class="h-8 w-8 flex-shrink-0 overflow-hidden rounded-full"
        >
          <img :src="member.avatar" :alt="member.name" class="h-full w-full object-cover" />
        </div>
        <div
          v-else
          class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold text-white"
          :class="getAvatarColor(member.id)"
        >
          {{ member.name.slice(0, 1) }}
        </div>

        <!-- 이름 + 역할 -->
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ member.name }}
          </p>
          <p class="truncate text-xs text-gray-500 dark:text-gray-400">
            {{ member.role }}
          </p>
        </div>

        <!-- 완료율 프로그레스바 -->
        <div class="w-24 flex-shrink-0">
          <div class="mb-0.5 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">
            {{ member.completionRate.toFixed(1) }}%
          </div>
          <div class="h-1.5 w-full rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-1.5 rounded-full transition-all duration-500"
              :class="getCompletionColor(member.completionRate)"
              :style="{ width: `${Math.min(member.completionRate, 100)}%` }"
            />
          </div>
        </div>
      </div>
    </div>

    <div
      v-else
      class="px-5 py-12 text-center"
    >
      <TrophyIcon class="mx-auto mb-2 h-8 w-8 text-gray-400 dark:text-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">팀원 데이터가 없습니다</p>
    </div>
  </div>
</template>
