<script setup lang="ts">
import { ref, computed } from 'vue'
import { ClockIcon } from '@heroicons/vue/24/outline'
import ActivityLogItem from './ActivityLogItem.vue'
import type { ActivityLog } from '@/types/activitylog'

const props = defineProps<{
  groupedLogs: [string, ActivityLog[]][]
  isLoading: boolean
}>()

const emit = defineEmits<{
  clickEntity: [log: ActivityLog]
}>()

const itemsPerPage = 15
const currentPage = ref(1)

// Paginated visible groups
const visibleGroups = computed(() => {
  const allGroups = props.groupedLogs
  const visibleItemCount = currentPage.value * itemsPerPage

  let itemCount = 0
  const groups: [string, ActivityLog[]][] = []

  for (const [dateKey, logs] of allGroups) {
    if (itemCount >= visibleItemCount) break

    const remainingItems = visibleItemCount - itemCount
    if (remainingItems >= logs.length) {
      groups.push([dateKey, logs])
      itemCount += logs.length
    } else {
      groups.push([dateKey, logs.slice(0, remainingItems)])
      itemCount += remainingItems
    }
  }

  return groups
})

const totalVisibleCount = computed(() => {
  return visibleGroups.value.reduce((sum, [, logs]) => sum + logs.length, 0)
})

const totalCount = computed(() => {
  return props.groupedLogs.reduce((sum, [, logs]) => sum + logs.length, 0)
})

const hasMore = computed(() => {
  return totalVisibleCount.value < totalCount.value
})

function loadMore() {
  currentPage.value++
}

function handleEntityClick(log: ActivityLog) {
  emit('clickEntity', log)
}
</script>

<template>
  <div class="space-y-6">
    <!-- Loading skeleton -->
    <div v-if="isLoading" class="space-y-4">
      <div v-for="i in 5" :key="i" class="flex gap-3 px-3 py-3">
        <div class="h-8 w-8 animate-pulse rounded-full bg-gray-200 dark:bg-gray-700" />
        <div class="h-7 w-7 animate-pulse rounded-full bg-gray-200 dark:bg-gray-700" />
        <div class="flex-1 space-y-2">
          <div class="h-4 w-3/4 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
          <div class="h-3 w-1/4 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
        </div>
      </div>
    </div>

    <!-- Timeline groups by date -->
    <template v-else-if="groupedLogs.length > 0">
      <div
        v-for="[dateKey, dateLogs] in visibleGroups"
        :key="dateKey"
        class="relative"
      >
        <!-- Date header (sticky) -->
        <div
          class="sticky top-0 z-10 mb-3 flex items-center gap-3 bg-white/80 py-2 backdrop-blur-sm dark:bg-gray-900/80"
        >
          <h3 class="whitespace-nowrap text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ dateKey }}
          </h3>
          <div class="h-px flex-1 bg-gray-200 dark:bg-gray-700" />
          <span class="text-xs text-gray-400 dark:text-gray-500">{{ dateLogs.length }}건</span>
        </div>

        <!-- Events in this date group -->
        <div class="relative space-y-1">
          <!-- Vertical timeline line -->
          <div
            class="absolute bottom-0 left-[33px] top-0 w-0.5 bg-gray-200 dark:bg-gray-700"
            aria-hidden="true"
          />

          <!-- Event items with animation -->
          <TransitionGroup name="log-appear">
            <div
              v-for="log in dateLogs"
              :key="log.id"
              class="relative"
            >
              <!-- Timeline dot -->
              <div
                class="absolute left-[30px] top-5 z-[1] h-2 w-2 rounded-full border-2 border-white bg-gray-300 dark:border-gray-900 dark:bg-gray-600"
              />

              <ActivityLogItem
                :log="log"
                :show-ip-address="true"
                class="pl-10"
                @click-entity="handleEntityClick"
              />
            </div>
          </TransitionGroup>
        </div>
      </div>

      <!-- Load more button -->
      <div v-if="hasMore" class="flex justify-center pt-4">
        <button
          class="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="loadMore"
        >
          더 보기
          <span class="text-xs text-gray-400 dark:text-gray-500">
            ({{ totalVisibleCount }}/{{ totalCount }})
          </span>
        </button>
      </div>
    </template>

    <!-- Empty state -->
    <div
      v-else-if="!isLoading"
      class="flex flex-col items-center justify-center py-16 text-center"
    >
      <div class="mb-4 rounded-full bg-gray-100 p-6 dark:bg-gray-800">
        <ClockIcon class="h-12 w-12 text-gray-400 dark:text-gray-500" />
      </div>
      <h3 class="mb-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
        활동 내역이 없습니다
      </h3>
      <p class="max-w-sm text-sm text-gray-500 dark:text-gray-400">
        선택한 필터에 해당하는 활동이 없습니다. 필터를 변경하거나 초기화해 보세요.
      </p>
    </div>
  </div>
</template>

<style scoped>
/* Smooth appear animation for log entries */
.log-appear-enter-active {
  transition: all 0.3s ease-out;
}

.log-appear-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}

.log-appear-enter-to {
  opacity: 1;
  transform: translateY(0);
}

.log-appear-leave-active {
  transition: all 0.2s ease-in;
}

.log-appear-leave-from {
  opacity: 1;
  transform: translateY(0);
}

.log-appear-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
