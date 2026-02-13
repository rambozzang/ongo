<script setup lang="ts">
import { ref, computed } from 'vue'
import { ClockIcon, FunnelIcon } from '@heroicons/vue/24/outline'
import { useTeamStore } from '@/stores/team'

const teamStore = useTeamStore()

const selectedMemberId = ref<number | null>(null)
const displayLimit = ref(10)

const filteredActivities = computed(() => {
  let activities = teamStore.activities

  if (selectedMemberId.value !== null) {
    activities = activities.filter((a) => a.memberId === selectedMemberId.value)
  }

  return activities.slice(0, displayLimit.value)
})

const hasMore = computed(() => {
  const totalCount = selectedMemberId.value !== null
    ? teamStore.activities.filter((a) => a.memberId === selectedMemberId.value).length
    : teamStore.activities.length

  return displayLimit.value < totalCount
})

const uniqueMembers = computed(() => {
  const memberMap = new Map<number, string>()
  teamStore.activities.forEach((a) => {
    memberMap.set(a.memberId, a.memberName)
  })
  return Array.from(memberMap.entries()).map(([id, name]) => ({ id, name }))
})

const loadMore = () => {
  displayLimit.value += 10
}

const relativeTime = (dateString: string): string => {
  const diff = Date.now() - new Date(dateString).getTime()
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (minutes < 1) return '방금 전'
  if (minutes < 60) return `${minutes}분 전`
  if (hours < 24) return `${hours}시간 전`
  if (days < 7) return `${days}일 전`
  return new Date(dateString).toLocaleDateString('ko-KR')
}

const clearFilter = () => {
  selectedMemberId.value = null
}
</script>

<template>
  <div class="space-y-4">
    <!-- Filter Bar -->
    <div class="flex items-center justify-between">
      <div class="flex items-center space-x-2">
        <FunnelIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
        <select
          v-model="selectedMemberId"
          class="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:focus:border-indigo-400 dark:focus:ring-indigo-400"
        >
          <option :value="null">모든 멤버</option>
          <option
            v-for="member in uniqueMembers"
            :key="member.id"
            :value="member.id"
          >
            {{ member.name }}
          </option>
        </select>
        <button
          v-if="selectedMemberId !== null"
          @click="clearFilter"
          class="text-sm text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300"
        >
          필터 지우기
        </button>
      </div>

      <div class="text-sm text-gray-500 dark:text-gray-400">
        {{ filteredActivities.length }}개 활동
      </div>
    </div>

    <!-- Activity Timeline -->
    <div class="flow-root">
      <ul role="list" class="-mb-8">
        <li v-for="(activity, idx) in filteredActivities" :key="activity.id">
          <div class="relative pb-8">
            <span
              v-if="idx !== filteredActivities.length - 1"
              class="absolute left-4 top-4 -ml-px h-full w-0.5 bg-gray-200 dark:bg-gray-700"
              aria-hidden="true"
            ></span>
            <div class="relative flex space-x-3">
              <div>
                <span
                  class="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-100 ring-8 ring-white dark:bg-indigo-900/30 dark:ring-gray-800"
                >
                  <ClockIcon class="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                </span>
              </div>
              <div class="flex min-w-0 flex-1 justify-between space-x-4 pt-1">
                <div>
                  <p class="text-sm text-gray-700 dark:text-gray-300">
                    <span class="font-medium text-gray-900 dark:text-white">
                      {{ activity.memberName }}
                    </span>
                    님이
                    <span class="font-medium text-indigo-600 dark:text-indigo-400">
                      {{ activity.action }}
                    </span>
                    했습니다
                  </p>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                    {{ activity.target }}
                  </p>
                </div>
                <div class="whitespace-nowrap text-right text-sm text-gray-500 dark:text-gray-400">
                  {{ relativeTime(activity.createdAt) }}
                </div>
              </div>
            </div>
          </div>
        </li>
      </ul>
    </div>

    <!-- Load More Button -->
    <div v-if="hasMore" class="flex justify-center">
      <button
        @click="loadMore"
        class="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600"
      >
        더 보기
      </button>
    </div>

    <!-- Empty State -->
    <div
      v-if="filteredActivities.length === 0"
      class="text-center py-12"
    >
      <ClockIcon class="mx-auto h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="mt-2 text-sm font-medium text-gray-900 dark:text-white">
        활동 내역 없음
      </h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        선택한 멤버의 활동 내역이 없습니다.
      </p>
    </div>
  </div>
</template>
