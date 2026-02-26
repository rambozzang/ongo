<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  CalendarDaysIcon,
  CheckBadgeIcon,
  SparklesIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import { useContentCalendarAiStore } from '@/stores/contentCalendarAi'
import SuggestionCard from '@/components/contentcalendarai/SuggestionCard.vue'
import TimeSlotGrid from '@/components/contentcalendarai/TimeSlotGrid.vue'
import CalendarTimeline from '@/components/contentcalendarai/CalendarTimeline.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useContentCalendarAiStore()
const { suggestions, slots, summary, isLoading } = storeToRefs(store)

const handleAccept = (id: number) => {
  const item = suggestions.value.find((s) => s.id === id)
  if (item) {
    item.status = 'ACCEPTED'
  }
}

onMounted(() => {
  store.fetchSuggestions()
  store.fetchSlots('2025-02-03', '2025-02-09')
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            AI 콘텐츠 캘린더
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          AI가 분석한 최적의 콘텐츠 일정과 업로드 시간대를 확인하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CalendarDaysIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">전체 제안</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalSuggestions }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CheckBadgeIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">수락된 제안</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.acceptedCount }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <SparklesIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 신뢰도</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgConfidence }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ClockIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">최적 시간대</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.bestTimeSlot || '-' }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Calendar Timeline -->
      <div class="mb-8">
        <CalendarTimeline :suggestions="suggestions" />
      </div>

      <!-- Time Slot Grid -->
      <div class="mb-8">
        <TimeSlotGrid :slots="slots" />
      </div>

      <!-- Suggestion Cards -->
      <div>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          콘텐츠 제안 목록
        </h2>
        <div v-if="suggestions.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <SuggestionCard
            v-for="s in suggestions"
            :key="s.id"
            :suggestion="s"
            @accept="handleAccept"
          />
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <CalendarDaysIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">콘텐츠 제안이 없습니다</p>
        </div>
      </div>
    </template>
  </div>
</template>
