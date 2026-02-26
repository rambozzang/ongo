<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  VideoCameraIcon,
  SignalIcon,
  UserGroupIcon,
  ChatBubbleLeftRightIcon,
} from '@heroicons/vue/24/outline'
import { useLiveStreamStore } from '@/stores/liveStream'
import StreamCard from '@/components/livestream/StreamCard.vue'
import ChatMessageRow from '@/components/livestream/ChatMessageRow.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useLiveStreamStore()

const activeFilter = ref<'ALL' | 'SCHEDULED' | 'LIVE' | 'ENDED' | 'CANCELLED'>('ALL')
const selectedStreamId = ref<number | null>(null)

const filterTabs: { value: typeof activeFilter.value; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'LIVE', label: 'LIVE' },
  { value: 'SCHEDULED', label: '예약됨' },
  { value: 'ENDED', label: '종료' },
  { value: 'CANCELLED', label: '취소됨' },
]

const filteredStreams = computed(() => {
  if (activeFilter.value === 'ALL') return store.streams
  return store.streams.filter((s) => s.status === activeFilter.value)
})

const selectedStream = computed(() =>
  store.streams.find((s) => s.id === selectedStreamId.value) ?? null,
)

function handleSelectStream(id: number) {
  selectedStreamId.value = id
  store.fetchChats(id)
}

onMounted(() => {
  store.fetchStreams()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          라이브 스트림 관리
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          실시간 스트리밍 현황과 채팅을 관리하세요
        </p>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else>
      <!-- Summary Cards -->
      <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
        <!-- Total Streams -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <VideoCameraIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 스트림</p>
          </div>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary?.totalStreams?.toLocaleString() ?? 0 }}
          </p>
        </div>

        <!-- Current Live -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <SignalIcon class="h-5 w-5 text-red-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">현재 라이브</p>
          </div>
          <p class="mt-1 text-2xl font-bold text-red-600 dark:text-red-400">
            {{ store.summary?.liveNow ?? 0 }}
          </p>
        </div>

        <!-- Average Viewers -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <UserGroupIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 시청자</p>
          </div>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary?.avgViewers?.toLocaleString() ?? 0 }}
          </p>
        </div>

        <!-- Total Chats -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <ChatBubbleLeftRightIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 채팅</p>
          </div>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary?.totalChatMessages?.toLocaleString() ?? 0 }}
          </p>
        </div>
      </div>

      <!-- Status Filter Tabs -->
      <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex gap-6">
          <button
            v-for="tab in filterTabs"
            :key="tab.value"
            @click="activeFilter = tab.value"
            :class="[
              'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium transition-colors',
              activeFilter === tab.value
                ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
            ]"
          >
            {{ tab.label }}
            <span
              v-if="tab.value === 'LIVE' && (store.summary?.liveNow ?? 0) > 0"
              class="ml-1.5 inline-flex items-center rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-900/30 dark:text-red-300"
            >
              {{ store.summary?.liveNow }}
            </span>
          </button>
        </nav>
      </div>

      <!-- Stream Card Grid -->
      <div v-if="filteredStreams.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <StreamCard
          v-for="stream in filteredStreams"
          :key="stream.id"
          :stream="stream"
          @select="handleSelectStream"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <VideoCameraIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          스트림이 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          해당 상태의 라이브 스트림이 없습니다.
        </p>
      </div>

      <!-- Chat Messages Section (when a stream is selected) -->
      <div
        v-if="selectedStreamId && selectedStream"
        class="mt-6 rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <!-- Chat header -->
        <div class="border-b border-gray-200 px-4 py-3 dark:border-gray-700">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <ChatBubbleLeftRightIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
              <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                채팅 메시지 - {{ selectedStream.title }}
              </h2>
            </div>
            <span class="text-xs text-gray-400 dark:text-gray-500">
              {{ store.chats.length }}개 메시지
            </span>
          </div>
        </div>

        <!-- Chat list -->
        <div class="max-h-80 divide-y divide-gray-100 overflow-y-auto dark:divide-gray-800">
          <ChatMessageRow
            v-for="chat in store.chats"
            :key="chat.id"
            :chat="chat"
          />
        </div>

        <!-- Empty chat -->
        <div
          v-if="store.chats.length === 0"
          class="py-8 text-center text-sm text-gray-400 dark:text-gray-500"
        >
          채팅 메시지가 없습니다.
        </div>
      </div>
    </template>
  </div>
</template>
