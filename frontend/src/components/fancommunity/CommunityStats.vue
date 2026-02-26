<script setup lang="ts">
import {
  DocumentTextIcon,
  UsersIcon,
  UserGroupIcon,
  HeartIcon,
  CalendarDaysIcon,
} from '@heroicons/vue/24/outline'
import type { CommunitySummary } from '@/types/fanCommunity'

defineProps<{
  summary: CommunitySummary
}>()
</script>

<template>
  <div class="grid grid-cols-2 gap-4 desktop:grid-cols-5">
    <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
      <div class="flex items-center gap-3">
        <div class="rounded-lg bg-blue-100 p-2 dark:bg-blue-900/30">
          <DocumentTextIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
        </div>
        <div>
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">전체 게시글</p>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.totalPosts.toLocaleString('ko-KR') }}</p>
        </div>
      </div>
    </div>

    <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
      <div class="flex items-center gap-3">
        <div class="rounded-lg bg-green-100 p-2 dark:bg-green-900/30">
          <UsersIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
        </div>
        <div>
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 멤버</p>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.totalMembers.toLocaleString('ko-KR') }}</p>
        </div>
      </div>
    </div>

    <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
      <div class="flex items-center gap-3">
        <div class="rounded-lg bg-purple-100 p-2 dark:bg-purple-900/30">
          <UserGroupIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
        </div>
        <div>
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">오늘 활성</p>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.activeMembersToday.toLocaleString('ko-KR') }}</p>
        </div>
      </div>
    </div>

    <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
      <div class="flex items-center gap-3">
        <div class="rounded-lg bg-red-100 p-2 dark:bg-red-900/30">
          <HeartIcon class="h-5 w-5 text-red-600 dark:text-red-400" />
        </div>
        <div>
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 좋아요</p>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.totalLikes.toLocaleString('ko-KR') }}</p>
        </div>
      </div>
    </div>

    <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl shadow-lg border border-white/20 dark:border-gray-700/30 p-6">
      <div class="flex items-center gap-3">
        <div class="rounded-lg bg-orange-100 p-2 dark:bg-orange-900/30">
          <CalendarDaysIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
        </div>
        <div>
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">이번주 게시글</p>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.postsThisWeek.toLocaleString('ko-KR') }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
