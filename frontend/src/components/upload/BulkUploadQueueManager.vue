<script setup lang="ts">
import { computed } from 'vue'
import {
  QueueListIcon,
  PauseIcon,
  PlayIcon,
  TrashIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import UploadQueueItem from '@/components/upload/UploadQueueItem.vue'
import BulkUploadDropzone from '@/components/upload/BulkUploadDropzone.vue'
import { useBulkUploadQueueStore } from '@/stores/bulkUploadQueue'
import { formatFileSize } from '@/utils/format'

const store = useBulkUploadQueueStore()

const hasPausedItems = computed(() => {
  return store.items.some((i) => i.status === 'paused')
})

const completedText = computed(() => {
  return `${store.stats.completed} / ${store.stats.total} 완료`
})

function handleAddToQueue(files: File[], platforms: string[]) {
  store.addFiles(files, platforms)
  store.startAll()
}

function handlePause(id: string) {
  store.pauseItem(id)
}

function handleResume(id: string) {
  store.resumeItem(id)
}

function handleRetry(id: string) {
  store.retryItem(id)
}

function handleRemove(id: string) {
  store.removeItem(id)
}

function handleMoveUp(id: string) {
  const index = store.items.findIndex((i) => i.id === id)
  if (index > 0) {
    store.reorderItem(id, index - 1)
  }
}

function handleMoveDown(id: string) {
  const index = store.items.findIndex((i) => i.id === id)
  if (index < store.items.length - 1) {
    store.reorderItem(id, index + 1)
  }
}

function handleCancelAll() {
  if (store.hasActiveUploads) {
    if (!confirm('업로드 중인 파일이 있습니다. 정말 전체 취소하시겠습니까?')) {
      return
    }
  }
  store.cancelAll()
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">
    <!-- Page header -->
    <div class="flex items-center gap-3">
      <div class="rounded-lg bg-primary-600 p-2">
        <QueueListIcon class="h-6 w-6 text-white" />
      </div>
      <div>
        <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100">
          대량 업로드 큐 관리
        </h1>
        <p class="text-sm text-gray-500 dark:text-gray-400">
          여러 파일을 동시에 업로드하고 진행 상황을 관리하세요
        </p>
      </div>
    </div>

    <!-- Dropzone -->
    <BulkUploadDropzone @add-to-queue="handleAddToQueue" />

    <!-- Queue section -->
    <div
      v-if="store.items.length > 0"
      class="overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800"
    >
      <!-- Queue header with stats -->
      <div class="border-b border-gray-200 bg-gray-50 px-6 py-4 dark:border-gray-700 dark:bg-gray-900/50">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <!-- Stats row -->
          <div class="flex flex-wrap items-center gap-4">
            <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100">
              업로드 큐
            </h2>
            <span class="rounded-full bg-primary-100 px-2.5 py-0.5 text-xs font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
              {{ store.stats.total }}개 파일
            </span>
            <span class="text-sm text-gray-500 dark:text-gray-400">
              {{ completedText }}
            </span>
            <span class="hidden text-sm text-gray-400 sm:inline dark:text-gray-500">
              {{ formatFileSize(store.stats.uploadedSize) }} / {{ formatFileSize(store.stats.totalSize) }}
            </span>
          </div>

          <!-- Action buttons -->
          <div class="flex items-center gap-2">
            <!-- Pause All / Resume All -->
            <button
              v-if="store.hasActiveUploads"
              class="inline-flex items-center gap-1.5 rounded-lg bg-amber-100 px-3 py-1.5 text-sm font-medium text-amber-700 transition-colors hover:bg-amber-200 dark:bg-amber-900/30 dark:text-amber-300 dark:hover:bg-amber-900/50"
              @click="store.pauseAll()"
            >
              <PauseIcon class="h-4 w-4" />
              모두 일시정지
            </button>
            <button
              v-else-if="hasPausedItems"
              class="inline-flex items-center gap-1.5 rounded-lg bg-primary-100 px-3 py-1.5 text-sm font-medium text-primary-700 transition-colors hover:bg-primary-200 dark:bg-primary-900/30 dark:text-primary-300 dark:hover:bg-primary-900/50"
              @click="store.resumeAll()"
            >
              <PlayIcon class="h-4 w-4" />
              모두 재개
            </button>

            <!-- Clear Completed -->
            <button
              v-if="store.stats.completed > 0"
              class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:bg-white dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
              @click="store.clearCompleted()"
            >
              <TrashIcon class="h-4 w-4" />
              완료 항목 정리
            </button>

            <!-- Cancel All -->
            <button
              class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:bg-red-50 hover:text-red-600 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-red-900/20 dark:hover:text-red-400"
              @click="handleCancelAll"
            >
              <XMarkIcon class="h-4 w-4" />
              전체 취소
            </button>
          </div>
        </div>

        <!-- Overall progress bar -->
        <div class="mt-3">
          <div class="mb-1 flex items-center justify-between text-xs">
            <span class="text-gray-500 dark:text-gray-400">전체 진행률</span>
            <span class="font-semibold text-primary-600 dark:text-primary-400">
              {{ store.overallProgress }}%
            </span>
          </div>
          <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full bg-gradient-to-r from-primary-500 to-primary-600 transition-all duration-300"
              :style="{ width: `${store.overallProgress}%` }"
            />
          </div>
        </div>
      </div>

      <!-- Queue item list -->
      <div class="max-h-[600px] overflow-y-auto p-4">
        <TransitionGroup name="queue-item" tag="div" class="space-y-3">
          <UploadQueueItem
            v-for="(item, index) in store.items"
            :key="item.id"
            :item="item"
            :index="index"
            :total="store.items.length"
            @pause="handlePause"
            @resume="handleResume"
            @retry="handleRetry"
            @remove="handleRemove"
            @move-up="handleMoveUp"
            @move-down="handleMoveDown"
          />
        </TransitionGroup>
      </div>
    </div>

    <!-- Empty state -->
    <div
      v-else
      class="flex flex-col items-center justify-center rounded-xl border border-gray-200 bg-white px-6 py-16 dark:border-gray-700 dark:bg-gray-800"
    >
      <div class="rounded-full bg-gray-100 p-6 dark:bg-gray-700">
        <QueueListIcon class="h-12 w-12 text-gray-400 dark:text-gray-500" />
      </div>
      <h3 class="mt-4 text-base font-medium text-gray-900 dark:text-gray-100">
        업로드 큐가 비어있습니다
      </h3>
      <p class="mt-1 text-center text-sm text-gray-500 dark:text-gray-400">
        위의 드롭존에서 파일을 선택하여 큐에 추가하세요.<br />
        최대 3개의 파일이 동시에 업로드됩니다.
      </p>
    </div>
  </div>
</template>

<style scoped>
.queue-item-enter-active,
.queue-item-leave-active {
  transition: all 0.3s ease;
}

.queue-item-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.queue-item-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.queue-item-move {
  transition: transform 0.3s ease;
}

@media (prefers-reduced-motion: reduce) {
  .queue-item-enter-active,
  .queue-item-leave-active,
  .queue-item-move {
    transition: none !important;
  }
  .queue-item-enter-from,
  .queue-item-leave-to {
    transform: none !important;
  }
}
</style>
