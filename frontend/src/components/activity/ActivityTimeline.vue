<template>
  <div class="space-y-6">
    <!-- Timeline groups by date -->
    <div
      v-for="[dateKey, dateEvents] in visibleGroups"
      :key="dateKey"
      class="relative"
    >
      <!-- Date header (sticky) -->
      <div
        class="sticky top-0 z-10 mb-4 flex items-center gap-3 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm py-2"
      >
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ dateKey }}
        </h3>
        <div class="h-px flex-1 bg-gray-200 dark:bg-gray-700" />
      </div>

      <!-- Events in this date group -->
      <div class="relative space-y-2">
        <!-- Vertical timeline line -->
        <div
          class="absolute left-[15px] top-0 bottom-0 w-0.5 bg-gray-200 dark:bg-gray-700"
          aria-hidden="true"
        />

        <!-- Event items with animation -->
        <TransitionGroup name="event-appear">
          <ActivityEventItem
            v-for="event in dateEvents"
            :key="event.id"
            :event="event"
            class="relative pl-8"
          />
        </TransitionGroup>
      </div>
    </div>

    <!-- Load more button -->
    <div v-if="hasMore" class="flex justify-center pt-4">
      <button
        class="btn-secondary text-sm"
        @click="loadMore"
      >
        더 보기
      </button>
    </div>

    <!-- Empty state -->
    <div
      v-if="filteredEvents.length === 0"
      class="flex flex-col items-center justify-center py-12 text-center"
    >
      <div class="mb-4 rounded-full bg-gray-100 dark:bg-gray-800 p-6">
        <ClockIcon class="h-12 w-12 text-gray-400" />
      </div>
      <h3 class="mb-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
        활동 내역이 없습니다
      </h3>
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ selectedFilters.length > 0 ? '선택한 필터에 해당하는 활동이 없습니다.' : '활동을 시작하면 여기에 표시됩니다.' }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ClockIcon } from '@heroicons/vue/24/outline'
import ActivityEventItem from './ActivityEventItem.vue'
import { useActivityLogStore } from '@/stores/activityLog'
import type { ActivityEvent, ActivityEventType } from '@/stores/activityLog'

const props = defineProps<{
  selectedFilters: ActivityEventType[]
}>()

const activityStore = useActivityLogStore()
const itemsPerPage = 10
const currentPage = ref(1)

// Filter events based on selected filters
const filteredEvents = computed(() => {
  if (props.selectedFilters.length === 0) {
    return activityStore.events
  }

  return activityStore.events.filter((event) =>
    props.selectedFilters.includes(event.type)
  )
})

// Group events by date
const groupedEvents = computed(() => {
  const grouped = new Map<string, ActivityEvent[]>()
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)

  filteredEvents.value.forEach((event) => {
    const eventDate = new Date(event.createdAt)
    const eventDateOnly = new Date(
      eventDate.getFullYear(),
      eventDate.getMonth(),
      eventDate.getDate()
    )

    let dateKey: string
    if (eventDateOnly.getTime() === today.getTime()) {
      dateKey = '오늘'
    } else if (eventDateOnly.getTime() === yesterday.getTime()) {
      dateKey = '어제'
    } else {
      // Format: "2월 7일 금요일"
      const month = eventDate.getMonth() + 1
      const date = eventDate.getDate()
      const dayOfWeek = ['일', '월', '화', '수', '목', '금', '토'][eventDate.getDay()]
      dateKey = `${month}월 ${date}일 ${dayOfWeek}요일`
    }

    if (!grouped.has(dateKey)) {
      grouped.set(dateKey, [])
    }
    grouped.get(dateKey)!.push(event)
  })

  return Array.from(grouped.entries())
})

// Paginated visible groups
const visibleGroups = computed(() => {
  const allGroups = groupedEvents.value
  const visibleItemCount = currentPage.value * itemsPerPage

  // Calculate how many complete date groups we need to show
  let itemCount = 0
  const groups: [string, ActivityEvent[]][] = []

  for (const [dateKey, events] of allGroups) {
    if (itemCount >= visibleItemCount) break

    const remainingItems = visibleItemCount - itemCount
    if (remainingItems >= events.length) {
      // Show all events in this group
      groups.push([dateKey, events])
      itemCount += events.length
    } else {
      // Show partial events in this group
      groups.push([dateKey, events.slice(0, remainingItems)])
      itemCount += remainingItems
    }
  }

  return groups
})

const hasMore = computed(() => {
  const totalVisible = visibleGroups.value.reduce(
    (sum, [, events]) => sum + events.length,
    0
  )
  return totalVisible < filteredEvents.value.length
})

function loadMore() {
  currentPage.value++
}
</script>

<style scoped>
/* Smooth appear animation for events */
.event-appear-enter-active {
  transition: all 0.3s ease-out;
}

.event-appear-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.event-appear-enter-to {
  opacity: 1;
  transform: translateY(0);
}

.event-appear-leave-active {
  transition: all 0.2s ease-in;
}

.event-appear-leave-from {
  opacity: 1;
  transform: translateY(0);
}

.event-appear-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
