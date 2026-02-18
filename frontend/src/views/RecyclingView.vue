<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  PlusIcon,
  ArrowPathIcon,
  QueueListIcon,
  CheckBadgeIcon,
  CalendarDaysIcon,
} from '@heroicons/vue/24/outline'
import { useRecyclingStore } from '@/stores/recycling'
import RecyclingQueueCard from '@/components/recycling/RecyclingQueueCard.vue'
import RecyclingCreateModal from '@/components/recycling/RecyclingCreateModal.vue'
import RecyclingHistoryComponent from '@/components/recycling/RecyclingHistory.vue'
import { LightBulbIcon } from '@heroicons/vue/24/outline'
import type { RecyclingQueue, RecyclingQueueCreateRequest } from '@/types/recycling'
import PageGuide from '@/components/common/PageGuide.vue'

const recyclingStore = useRecyclingStore()

const activeTab = ref<'queues' | 'history' | 'suggestions'>('queues')
const isModalOpen = ref(false)
const editingQueue = ref<RecyclingQueue | undefined>(undefined)

const activeQueueCount = computed(() => recyclingStore.activeQueues.length)
const totalRecycled = computed(() => recyclingStore.totalRecycledCount)
const nextScheduledLabel = computed(() => {
  const item = recyclingStore.nextScheduledItem
  if (!item) return '없음'
  const date = new Date(item.scheduledAt)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
})

const sortedQueues = computed(() => {
  return [...recyclingStore.queues].sort((a, b) => {
    if (a.isActive !== b.isActive) {
      return a.isActive ? -1 : 1
    }
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })
})

function openCreateModal() {
  editingQueue.value = undefined
  isModalOpen.value = true
}

function openEditModal(id: number) {
  editingQueue.value = recyclingStore.queues.find((q) => q.id === id)
  isModalOpen.value = true
}

function closeModal() {
  isModalOpen.value = false
  editingQueue.value = undefined
}

function handleSave(data: RecyclingQueueCreateRequest) {
  if (editingQueue.value) {
    recyclingStore.updateQueue(editingQueue.value.id, {
      name: data.name,
      filterCriteria: data.filterCriteria,
      frequency: data.frequency,
      scheduleDays: data.scheduleDays,
      scheduleTime: data.scheduleTime,
      platforms: data.platforms,
      titleVariation: data.titleVariation,
    })
  } else {
    recyclingStore.createQueue(data)
  }
}

function handleDelete(id: number) {
  if (confirm('이 큐를 삭제하시겠습니까?')) {
    recyclingStore.deleteQueue(id)
  }
}

function handleToggle(id: number) {
  recyclingStore.toggleActive(id)
}

const suggestionTypeLabels: Record<string, string> = {
  REPOST: '재게시',
  CLIP: '클립 생성',
  REMIX: '리믹스',
  UPDATE_METADATA: '메타데이터 업데이트',
}

async function handleGenerateSuggestions() {
  await recyclingStore.generateSuggestions()
}

async function handleAcceptSuggestion(id: number) {
  await recyclingStore.acceptSuggestion(id)
}

async function handleDismissSuggestion(id: number) {
  await recyclingStore.dismissSuggestion(id)
}

onMounted(() => {
  recyclingStore.fetchSuggestions()
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Header -->
      <div class="mb-8">
        <div class="flex items-center justify-between">
          <div>
            <div class="flex items-center gap-3 mb-2">
              <ArrowPathIcon class="w-8 h-8 text-blue-600 dark:text-blue-400" />
              <h1 class="text-3xl font-bold text-gray-900 dark:text-white">
                콘텐츠 재활용
              </h1>
            </div>
            <p class="text-gray-600 dark:text-gray-400">
              인기 콘텐츠를 자동으로 재게시하여 도달 범위를 극대화하세요
            </p>
          </div>

          <button
            @click="openCreateModal"
            class="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            <PlusIcon class="w-5 h-5" />
            새 큐 생성
          </button>
        </div>
      </div>

      <PageGuide title="콘텐츠 재활용" :items="[
        '큐 목록 탭에서 자동 재게시 규칙을 생성하고, 활성 큐 수와 총 재활용 횟수를 확인하세요',
        '재활용 규칙에 빈도(매일/매주/매월)·대상 플랫폼·제목 변형 옵션을 설정할 수 있습니다',
        '기록 탭에서 과거 재활용 실행 이력과 성과를 확인하세요',
        '추천 탭에서 AI가 분석한 재게시 추천 콘텐츠를 확인하고, 바로 재활용 큐에 추가하세요',
        '다음 예정 항목 표시에서 곧 재게시될 콘텐츠를 미리 확인할 수 있습니다',
      ]" />

      <!-- Stats Cards -->
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div class="flex items-center gap-3">
            <div class="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
              <QueueListIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-sm text-gray-500 dark:text-gray-400">활성 큐</p>
              <p class="text-2xl font-bold text-gray-900 dark:text-white">{{ activeQueueCount }}개</p>
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div class="flex items-center gap-3">
            <div class="p-2 bg-green-100 dark:bg-green-900/30 rounded-lg">
              <CheckBadgeIcon class="w-5 h-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-sm text-gray-500 dark:text-gray-400">총 재활용 완료</p>
              <p class="text-2xl font-bold text-gray-900 dark:text-white">{{ totalRecycled }}건</p>
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div class="flex items-center gap-3">
            <div class="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
              <CalendarDaysIcon class="w-5 h-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-sm text-gray-500 dark:text-gray-400">다음 예정</p>
              <p class="text-2xl font-bold text-gray-900 dark:text-white">{{ nextScheduledLabel }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex gap-8">
          <button
            @click="activeTab = 'queues'"
            :class="[
              'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
              activeTab === 'queues'
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
            ]"
          >
            재활용 큐
            <span
              :class="[
                'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
                activeTab === 'queues'
                  ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
              ]"
            >
              {{ recyclingStore.queues.length }}
            </span>
          </button>

          <button
            @click="activeTab = 'history'"
            :class="[
              'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
              activeTab === 'history'
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
            ]"
          >
            재활용 기록
            <span
              :class="[
                'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
                activeTab === 'history'
                  ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
              ]"
            >
              {{ recyclingStore.history.length }}
            </span>
          </button>

          <button
            @click="activeTab = 'suggestions'"
            :class="[
              'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
              activeTab === 'suggestions'
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
            ]"
          >
            AI 제안
            <span
              :class="[
                'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
                activeTab === 'suggestions'
                  ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
              ]"
            >
              {{ recyclingStore.suggestions.filter(s => s.status === 'PENDING').length }}
            </span>
          </button>
        </nav>
      </div>

      <!-- Queues Tab -->
      <div v-if="activeTab === 'queues'">
        <div v-if="sortedQueues.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <RecyclingQueueCard
            v-for="queue in sortedQueues"
            :key="queue.id"
            :queue="queue"
            @edit="openEditModal"
            @delete="handleDelete"
            @toggle="handleToggle"
          />
        </div>

        <!-- Empty State -->
        <div
          v-else
          class="text-center py-16 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700"
        >
          <ArrowPathIcon class="w-16 h-16 mx-auto text-gray-400 dark:text-gray-600 mb-4" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-2">
            재활용 큐가 없습니다
          </h3>
          <p class="text-gray-600 dark:text-gray-400 mb-6">
            첫 번째 재활용 큐를 만들어 콘텐츠를 자동으로 재게시하세요
          </p>
          <button
            @click="openCreateModal"
            class="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            <PlusIcon class="w-5 h-5" />
            첫 큐 만들기
          </button>
        </div>
      </div>

      <!-- History Tab -->
      <div v-if="activeTab === 'history'">
        <RecyclingHistoryComponent :history="recyclingStore.recentHistory" />
      </div>

      <!-- Suggestions Tab -->
      <div v-if="activeTab === 'suggestions'">
        <div class="mb-4 flex justify-end">
          <button
            @click="handleGenerateSuggestions"
            :disabled="recyclingStore.suggestionsLoading"
            class="inline-flex items-center gap-2 px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors font-medium disabled:opacity-50"
          >
            <LightBulbIcon class="w-5 h-5" />
            {{ recyclingStore.suggestionsLoading ? '분석 중...' : '새 제안 생성' }}
          </button>
        </div>

        <div v-if="recyclingStore.suggestions.length > 0" class="space-y-4">
          <div
            v-for="suggestion in recyclingStore.suggestions"
            :key="suggestion.id"
            class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5"
          >
            <div class="flex items-start justify-between">
              <div class="flex-1">
                <div class="flex items-center gap-2 mb-2">
                  <span class="px-2 py-1 text-xs font-semibold rounded-full bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400">
                    {{ suggestionTypeLabels[suggestion.suggestionType] || suggestion.suggestionType }}
                  </span>
                  <span
                    :class="[
                      'px-2 py-1 text-xs font-semibold rounded-full',
                      suggestion.status === 'PENDING'
                        ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-600 dark:text-yellow-400'
                        : suggestion.status === 'ACCEPTED'
                          ? 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400'
                          : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
                    ]"
                  >
                    {{ suggestion.status === 'PENDING' ? '대기 중' : suggestion.status === 'ACCEPTED' ? '수락됨' : '거절됨' }}
                  </span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">
                    우선순위: {{ suggestion.priorityScore }}
                  </span>
                </div>
                <p class="text-sm text-gray-700 dark:text-gray-300 mb-2">{{ suggestion.reason }}</p>
                <div class="flex gap-1">
                  <span
                    v-for="platform in suggestion.suggestedPlatforms"
                    :key="platform"
                    class="px-2 py-0.5 text-xs rounded bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400"
                  >
                    {{ platform }}
                  </span>
                </div>
              </div>
              <div v-if="suggestion.status === 'PENDING'" class="flex gap-2 ml-4">
                <button
                  @click="handleAcceptSuggestion(suggestion.id)"
                  class="px-3 py-1.5 text-sm font-medium text-white bg-green-600 rounded-lg hover:bg-green-700 transition-colors"
                >
                  수락
                </button>
                <button
                  @click="handleDismissSuggestion(suggestion.id)"
                  class="px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
                >
                  거절
                </button>
              </div>
            </div>
          </div>
        </div>

        <div
          v-else-if="!recyclingStore.suggestionsLoading"
          class="text-center py-16 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700"
        >
          <LightBulbIcon class="w-16 h-16 mx-auto text-gray-400 dark:text-gray-600 mb-4" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-2">
            재활용 제안이 없습니다
          </h3>
          <p class="text-gray-600 dark:text-gray-400 mb-6">
            AI가 기존 영상을 분석하여 재활용 제안을 생성합니다
          </p>
          <button
            @click="handleGenerateSuggestions"
            class="inline-flex items-center gap-2 px-6 py-3 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors font-medium"
          >
            <LightBulbIcon class="w-5 h-5" />
            제안 생성하기
          </button>
        </div>
      </div>
    </div>

    <!-- Modal -->
    <RecyclingCreateModal
      :is-open="isModalOpen"
      :queue="editingQueue"
      @close="closeModal"
      @save="handleSave"
    />
  </div>
</template>
