<template>
  <div class="space-y-4">
    <!-- Header with Filter Tabs -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-4">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">사용 기록</h2>

        <!-- Filter Tabs -->
        <div class="flex gap-2">
          <button
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="activeFilter === 'all'
              ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
              : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'"
            @click="activeFilter = 'all'"
          >
            전체
          </button>
          <button
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="activeFilter === 'favorites'
              ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
              : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'"
            @click="activeFilter = 'favorites'"
          >
            즐겨찾기
          </button>
        </div>
      </div>

      <button
        v-if="filteredHistory.length > 0"
        class="text-sm text-red-600 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 transition-colors"
        @click="handleClearHistory"
      >
        기록 지우기
      </button>
    </div>

    <!-- Empty State -->
    <div
      v-if="filteredHistory.length === 0"
      class="card py-16 text-center"
    >
      <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-800">
        <SparklesIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
      </div>
      <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
        {{ activeFilter === 'favorites' ? '즐겨찾기한 기록이 없습니다' : '아직 AI 사용 기록이 없습니다' }}
      </h3>
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ activeFilter === 'favorites' ? '별표를 눌러 중요한 기록을 즐겨찾기에 추가하세요' : 'AI 도구를 사용하면 여기에 기록이 표시됩니다' }}
      </p>
    </div>

    <!-- History List -->
    <div v-else class="space-y-3">
      <div
        v-for="record in filteredHistory"
        :key="record.id"
        class="card group cursor-pointer transition-all hover:shadow-md hover:border-primary-200 dark:hover:border-primary-800"
        @click="toggleExpand(record.id)"
      >
        <!-- Record Header -->
        <div class="flex items-start gap-4">
          <!-- Tool Icon -->
          <div
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
            :class="getToolIconBg(record.toolType)"
          >
            <component :is="getToolIcon(record.toolType)" class="h-5 w-5" :class="getToolIconColor(record.toolType)" />
          </div>

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <div class="mb-2 flex items-center gap-2">
              <span class="badge-blue text-xs">{{ record.toolType }}</span>
              <span class="text-xs text-gray-500 dark:text-gray-400">{{ formatDate(record.createdAt) }}</span>
              <span class="text-xs font-medium text-primary-600 dark:text-primary-400">{{ record.creditsUsed }} 크레딧</span>
            </div>

            <!-- Prompt -->
            <p class="mb-1 text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ expandedIds.includes(record.id) ? record.prompt : truncate(record.prompt, 80) }}
            </p>

            <!-- Result Preview/Full -->
            <div
              class="text-sm text-gray-600 dark:text-gray-300"
              :class="expandedIds.includes(record.id) ? 'mt-3' : ''"
            >
              <AiResultCard
                :record="record"
                :expanded="expandedIds.includes(record.id)"
                @copy="handleCopy"
              />
            </div>
          </div>

          <!-- Actions -->
          <div class="flex shrink-0 items-start gap-2">
            <button
              class="rounded-lg p-2 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
              :class="record.isFavorite ? 'text-yellow-500' : 'text-gray-400 dark:text-gray-500'"
              @click.stop="toggleFavorite(record.id)"
            >
              <component :is="record.isFavorite ? StarIconSolid : StarIconOutline" class="h-5 w-5" />
            </button>
            <button
              class="rounded-lg p-2 text-gray-400 dark:text-gray-500 transition-colors hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-600 dark:hover:text-red-400"
              @click.stop="handleDelete(record.id)"
            >
              <TrashIcon class="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  SparklesIcon,
  DocumentTextIcon,
  HashtagIcon,
  MicrophoneIcon,
  DocumentMagnifyingGlassIcon,
  ChatBubbleLeftRightIcon,
  ClockIcon,
  LightBulbIcon,
  ChartBarIcon,
  TrashIcon,
  StarIcon as StarIconOutline,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import type { Component } from 'vue'
import { useAiHistoryStore } from '@/stores/aiHistory'
import { useNotification } from '@/composables/useNotification'
import AiResultCard from './AiHistoryResultCard.vue'

const historyStore = useAiHistoryStore()
const { success } = useNotification()

const activeFilter = ref<'all' | 'favorites'>('all')
const expandedIds = ref<string[]>([])

const filteredHistory = computed(() => {
  const records = historyStore.history
  if (activeFilter.value === 'favorites') {
    return records.filter(r => r.isFavorite)
  }
  return records
})

function getToolIcon(toolType: string): Component {
  const icons: Record<string, Component> = {
    '제목/설명 생성': DocumentTextIcon,
    '해시태그 추천': HashtagIcon,
    '영상 STT 변환': MicrophoneIcon,
    '스크립트 분석': DocumentMagnifyingGlassIcon,
    '댓글 답변 생성': ChatBubbleLeftRightIcon,
    '업로드 시간 추천': ClockIcon,
    '콘텐츠 아이디어': LightBulbIcon,
    '성과 리포트': ChartBarIcon,
  }
  return icons[toolType] || SparklesIcon
}

function getToolIconBg(toolType: string): string {
  const backgrounds: Record<string, string> = {
    '제목/설명 생성': 'bg-blue-100 dark:bg-blue-900/30',
    '해시태그 추천': 'bg-purple-100 dark:bg-purple-900/30',
    '영상 STT 변환': 'bg-red-100 dark:bg-red-900/30',
    '스크립트 분석': 'bg-amber-100 dark:bg-amber-900/30',
    '댓글 답변 생성': 'bg-green-100 dark:bg-green-900/30',
    '업로드 시간 추천': 'bg-cyan-100 dark:bg-cyan-900/30',
    '콘텐츠 아이디어': 'bg-yellow-100 dark:bg-yellow-900/30',
    '성과 리포트': 'bg-indigo-100 dark:bg-indigo-900/30',
  }
  return backgrounds[toolType] || 'bg-gray-100 dark:bg-gray-800'
}

function getToolIconColor(toolType: string): string {
  const colors: Record<string, string> = {
    '제목/설명 생성': 'text-blue-600 dark:text-blue-400',
    '해시태그 추천': 'text-purple-600 dark:text-purple-400',
    '영상 STT 변환': 'text-red-600 dark:text-red-400',
    '스크립트 분석': 'text-amber-600 dark:text-amber-400',
    '댓글 답변 생성': 'text-green-600 dark:text-green-400',
    '업로드 시간 추천': 'text-cyan-600 dark:text-cyan-400',
    '콘텐츠 아이디어': 'text-yellow-600 dark:text-yellow-400',
    '성과 리포트': 'text-indigo-600 dark:text-indigo-400',
  }
  return colors[toolType] || 'text-gray-600 dark:text-gray-400'
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '방금 전'
  if (diffMins < 60) return `${diffMins}분 전`
  if (diffHours < 24) return `${diffHours}시간 전`
  if (diffDays < 7) return `${diffDays}일 전`

  return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}

function truncate(text: string, length: number): string {
  if (text.length <= length) return text
  return text.slice(0, length) + '...'
}

function toggleExpand(id: string) {
  const index = expandedIds.value.indexOf(id)
  if (index > -1) {
    expandedIds.value.splice(index, 1)
  } else {
    expandedIds.value.push(id)
  }
}

function toggleFavorite(id: string) {
  historyStore.toggleFavorite(id)
}

function handleDelete(id: string) {
  if (confirm('이 기록을 삭제하시겠습니까?')) {
    historyStore.removeRecord(id)
    success('기록이 삭제되었습니다')
  }
}

function handleClearHistory() {
  if (confirm('모든 사용 기록을 삭제하시겠습니까?')) {
    historyStore.clearHistory()
    success('모든 기록이 삭제되었습니다')
  }
}

function handleCopy(_text: string) {
  success('클립보드에 복사되었습니다')
}
</script>
