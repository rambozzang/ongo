<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
const recyclingStore = useRecyclingStore()

const activeTab = ref<'queues' | 'history' | 'suggestions'>('queues')
const isModalOpen = ref(false)
const editingQueue = ref<RecyclingQueue | undefined>(undefined)

const activeQueueCount = computed(() => recyclingStore.activeQueues.length)
const totalRecycled = computed(() => recyclingStore.totalRecycledCount)
const nextScheduledLabel = computed(() => {
  const item = recyclingStore.nextScheduledItem
  if (!item) return t('recycling.none')
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
  if (confirm(t('recycling.deleteConfirmMessage'))) {
    recyclingStore.deleteQueue(id)
  }
}

function handleToggle(id: number) {
  recyclingStore.toggleActive(id)
}

const suggestionTypeLabels = computed<Record<string, string>>(() => ({
  REPOST: t('recycling.typeRepost'),
  CLIP: t('recycling.typeClip'),
  REMIX: t('recycling.typeRemix'),
  UPDATE_METADATA: t('recycling.typeUpdateMetadata'),
}))

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
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('recycling.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('recycling.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="openCreateModal"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('recycling.newQueue') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('recycling.pageGuideTitle')" :items="($tm('recycling.pageGuide') as string[])" />

    <!-- Stats Cards -->
    <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-3">
      <div class="card">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
            <QueueListIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('recycling.activeQueues') }}</p>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ activeQueueCount }}{{ $t('recycling.countUnit') }}</p>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
            <CheckBadgeIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('recycling.totalRecycled') }}</p>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ totalRecycled }}{{ $t('recycling.caseUnit') }}</p>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
            <CalendarDaysIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('recycling.nextScheduled') }}</p>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ nextScheduledLabel }}</p>
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
              ? 'border-primary-600 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ $t('recycling.tabQueues') }}
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'queues'
                ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400'
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
              ? 'border-primary-600 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ $t('recycling.tabHistory') }}
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'history'
                ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400'
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
              ? 'border-primary-600 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ $t('recycling.tabSuggestions') }}
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'suggestions'
                ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400'
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
      <div v-if="sortedQueues.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
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
        class="card py-16 text-center"
      >
        <ArrowPathIcon class="mx-auto mb-4 h-16 w-16 text-gray-400 dark:text-gray-600" />
        <h3 class="mb-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('recycling.emptyQueuesTitle') }}
        </h3>
        <p class="mb-6 text-gray-600 dark:text-gray-400">
          {{ $t('recycling.emptyQueuesDescription') }}
        </p>
        <button
          @click="openCreateModal"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('recycling.createFirstQueue') }}
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
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
        >
          <LightBulbIcon class="h-5 w-5" />
          {{ recyclingStore.suggestionsLoading ? $t('recycling.analyzing') : $t('recycling.generateSuggestions') }}
        </button>
      </div>

      <div v-if="recyclingStore.suggestions.length > 0" class="space-y-4">
        <div
          v-for="suggestion in recyclingStore.suggestions"
          :key="suggestion.id"
          class="card"
        >
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <div class="mb-2 flex items-center gap-2">
                <span class="rounded-full bg-purple-100 px-2 py-1 text-xs font-semibold text-purple-600 dark:bg-purple-900/30 dark:text-purple-400">
                  {{ suggestionTypeLabels[suggestion.suggestionType] || suggestion.suggestionType }}
                </span>
                <span
                  :class="[
                    'rounded-full px-2 py-1 text-xs font-semibold',
                    suggestion.status === 'PENDING'
                      ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-600 dark:text-yellow-400'
                      : suggestion.status === 'ACCEPTED'
                        ? 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400'
                        : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
                  ]"
                >
                  {{ suggestion.status === 'PENDING' ? $t('recycling.statusPending') : suggestion.status === 'ACCEPTED' ? $t('recycling.statusAccepted') : $t('recycling.statusDismissed') }}
                </span>
                <span class="text-xs text-gray-500 dark:text-gray-400">
                  {{ $t('recycling.priority') }}: {{ suggestion.priorityScore }}
                </span>
              </div>
              <p class="mb-2 text-sm text-gray-700 dark:text-gray-300">{{ suggestion.reason }}</p>
              <div class="flex gap-1">
                <span
                  v-for="platform in suggestion.suggestedPlatforms"
                  :key="platform"
                  class="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400"
                >
                  {{ platform }}
                </span>
              </div>
            </div>
            <div v-if="suggestion.status === 'PENDING'" class="ml-4 flex gap-2">
              <button
                @click="handleAcceptSuggestion(suggestion.id)"
                class="rounded-lg bg-green-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-green-700"
              >
                {{ $t('recycling.accept') }}
              </button>
              <button
                @click="handleDismissSuggestion(suggestion.id)"
                class="rounded-lg bg-gray-100 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
              >
                {{ $t('recycling.dismiss') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div
        v-else-if="!recyclingStore.suggestionsLoading"
        class="card py-16 text-center"
      >
        <LightBulbIcon class="mx-auto mb-4 h-16 w-16 text-gray-400 dark:text-gray-600" />
        <h3 class="mb-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('recycling.emptySuggestionsTitle') }}
        </h3>
        <p class="mb-6 text-gray-600 dark:text-gray-400">
          {{ $t('recycling.emptySuggestionsDescription') }}
        </p>
        <button
          @click="handleGenerateSuggestions"
          class="btn-primary inline-flex items-center gap-2"
        >
          <LightBulbIcon class="h-5 w-5" />
          {{ $t('recycling.generateSuggestionsBtn') }}
        </button>
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
