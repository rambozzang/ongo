<template>
  <div
    v-if="queueStore.queue.length > 0"
    class="mt-6 overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800"
  >
    <!-- Header -->
    <div class="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 px-6 py-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <h3 class="text-h3 text-gray-900 dark:text-gray-100">
            업로드 큐
          </h3>
          <span class="rounded-full bg-primary-100 dark:bg-primary-900/30 px-2.5 py-0.5 text-caption text-primary-700 dark:text-primary-300">
            {{ queueStore.queue.length }}개 파일
          </span>
        </div>

        <div class="flex items-center gap-2">
          <!-- Total progress -->
          <div class="hidden tablet:flex items-center gap-2 rounded-lg bg-white dark:bg-gray-800 px-3 py-1.5 text-body">
            <span class="text-gray-500 dark:text-gray-400">전체 진행률:</span>
            <span class="font-medium text-gray-900 dark:text-gray-100">{{ queueStore.totalProgress }}%</span>
          </div>

          <!-- Actions -->
          <button
            v-if="queueStore.pendingCount > 0"
            class="btn-primary inline-flex items-center gap-1.5"
            :disabled="queueStore.isUploading"
            @click="queueStore.startUpload()"
          >
            <PlayIcon class="h-4 w-4" />
            <span class="hidden tablet:inline">모두 시작</span>
          </button>

          <button
            v-if="queueStore.queue.some((item) => item.status === 'completed')"
            class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-1.5 text-body font-medium text-gray-700 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
            @click="queueStore.clearCompleted()"
          >
            <TrashIcon class="h-4 w-4" />
            <span class="hidden tablet:inline">완료 항목 제거</span>
          </button>

          <button
            class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-1.5 text-body font-medium text-gray-700 dark:text-gray-300 transition-colors hover:bg-error-subtle hover:text-error-strong"
            @click="handleClearAll"
          >
            <XMarkIcon class="h-4 w-4" />
            <span class="hidden tablet:inline">전체 취소</span>
          </button>
        </div>
      </div>

      <!-- Total progress bar -->
      <div class="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-full rounded-full bg-primary-600 transition-all duration-300"
          :style="{ width: `${queueStore.totalProgress}%` }"
        />
      </div>
    </div>

    <!-- Queue items -->
    <div class="max-h-[400px] overflow-y-auto">
      <TransitionGroup name="queue-item">
        <div
          v-for="item in queueStore.queue"
          :key="item.id"
          class="border-b border-gray-100 dark:border-gray-700 last:border-0 px-6 py-4 transition-colors hover:bg-gray-50 dark:hover:bg-gray-900/30"
        >
          <div class="flex items-start gap-3">
            <!-- Thumbnail -->
            <div class="flex-shrink-0">
              <div
                v-if="item.thumbnail"
                class="h-10 w-10 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-800"
              >
                <img
                  :src="item.thumbnail"
                  :alt="item.fileName"
                  class="h-full w-full object-cover"
                />
              </div>
              <div
                v-else
                class="flex h-10 w-10 items-center justify-center rounded-lg bg-gray-100 dark:bg-gray-800"
              >
                <FilmIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
              </div>
            </div>

            <!-- File info & progress -->
            <div class="min-w-0 flex-1">
              <!-- File name & size -->
              <div class="flex items-start justify-between gap-2">
                <div class="min-w-0 flex-1">
                  <p class="truncate text-body font-medium text-gray-900 dark:text-gray-100" :title="item.fileName">
                    {{ item.fileName }}
                  </p>
                  <div class="mt-0.5 flex items-center gap-2 text-body-xs text-gray-500 dark:text-gray-400">
                    <span>{{ formatFileSize(item.fileSize) }}</span>
                    <span>·</span>
                    <span :class="getStatusClass(item.status)">
                      {{ getStatusText(item.status) }}
                    </span>
                  </div>
                </div>

                <!-- Action buttons -->
                <div class="flex items-center gap-1">
                  <!-- Pending: play, remove -->
                  <template v-if="item.status === 'queued'">
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-primary-600 dark:hover:text-primary-400"
                      title="업로드 시작"
                      @click="queueStore.presignedUpload(item.id)"
                    >
                      <PlayIcon class="h-4 w-4" />
                    </button>
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-error-strong"
                      title="제거"
                      @click="queueStore.removeItem(item.id)"
                    >
                      <XMarkIcon class="h-4 w-4" />
                    </button>
                  </template>

                  <!-- Uploading: pause, cancel -->
                  <template v-else-if="item.status === 'uploading'">
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-warning-strong"
                      title="일시정지"
                      @click="queueStore.pauseItem(item.id)"
                    >
                      <PauseIcon class="h-4 w-4" />
                    </button>
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-error-strong"
                      title="취소"
                      @click="queueStore.removeItem(item.id)"
                    >
                      <XMarkIcon class="h-4 w-4" />
                    </button>
                  </template>

                  <!-- Paused: resume, remove -->
                  <template v-else-if="item.status === 'paused'">
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-primary-600 dark:hover:text-primary-400"
                      title="재개"
                      @click="queueStore.resumeItem(item.id)"
                    >
                      <PlayIcon class="h-4 w-4" />
                    </button>
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-error-strong"
                      title="제거"
                      @click="queueStore.removeItem(item.id)"
                    >
                      <XMarkIcon class="h-4 w-4" />
                    </button>
                  </template>

                  <!-- Completed: checkmark, remove -->
                  <template v-else-if="item.status === 'completed'">
                    <CheckCircleIcon class="h-5 w-5 text-success-strong" />
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-error-strong"
                      title="제거"
                      @click="queueStore.removeItem(item.id)"
                    >
                      <XMarkIcon class="h-4 w-4" />
                    </button>
                  </template>

                  <!-- Failed: retry, remove -->
                  <template v-else-if="item.status === 'failed'">
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-primary-600 dark:hover:text-primary-400"
                      :title="item.error || '재시도'"
                      @click="queueStore.retryItem(item.id)"
                    >
                      <ArrowPathIcon class="h-4 w-4" />
                    </button>
                    <button
                      class="rounded-lg p-1.5 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-error-strong"
                      title="제거"
                      @click="queueStore.removeItem(item.id)"
                    >
                      <XMarkIcon class="h-4 w-4" />
                    </button>
                  </template>
                </div>
              </div>

              <!-- Progress bar (only for uploading) -->
              <div v-if="item.status === 'uploading'" class="mt-2">
                <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                  <div
                    class="h-full rounded-full bg-info transition-all duration-300"
                    :style="{ width: `${item.progress}%` }"
                  />
                </div>
                <p class="mt-1 text-body-xs text-gray-500 dark:text-gray-400">
                  {{ Math.round(item.progress) }}%
                </p>
              </div>

              <!-- Error message -->
              <div v-if="item.status === 'failed' && item.error" class="mt-2">
                <p class="text-body-xs text-error-strong">
                  {{ item.error }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </TransitionGroup>
    </div>

    <!-- Empty state (should not show as component is v-if) -->

    <!-- 업로드 중 전체 취소 확인 -->
    <ConfirmModal
      v-model="showClearAllModal"
      :title="$t('upload.clearAllTitle')"
      :message="$t('upload.clearAllMessage')"
      :confirm-text="$t('action.confirm')"
      danger
      @confirm="confirmClearAll"
    />
  </div>
</template>

<script setup lang="ts">
import {
  PlayIcon,
  PauseIcon,
  XMarkIcon,
  TrashIcon,
  FilmIcon,
  CheckCircleIcon,
  ArrowPathIcon,
} from '@heroicons/vue/24/outline'
import { ref } from 'vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { useUploadQueueStore } from '@/stores/uploadQueue'
import type { QueueItemStatus } from '@/stores/uploadQueue'
import { formatFileSize } from '@/utils/format'

const queueStore = useUploadQueueStore()
const showClearAllModal = ref(false)

function getStatusText(status: QueueItemStatus): string {
  const statusMap: Record<QueueItemStatus, string> = {
    queued: '대기 중',
    uploading: '업로드 중',
    processing: '처리 중',
    completed: '완료',
    failed: '실패',
    paused: '일시정지',
  }
  return statusMap[status]
}

function getStatusClass(status: QueueItemStatus): string {
  const classMap: Record<QueueItemStatus, string> = {
    queued: 'text-gray-500 dark:text-gray-400',
    uploading: 'text-info-strong',
    processing: 'text-purple-600 dark:text-purple-400',
    completed: 'text-success-strong',
    failed: 'text-error-strong',
    paused: 'text-warning-strong',
  }
  return classMap[status]
}

function handleClearAll() {
  // 업로드 중일 때만 확인을 거친다 (기존 동작 유지)
  if (queueStore.isUploading) {
    showClearAllModal.value = true
    return
  }
  queueStore.clearAll()
}

function confirmClearAll() {
  queueStore.clearAll()
}
</script>

<style scoped>
/* TransitionGroup animations */
.queue-item-enter-active,
.queue-item-leave-active {
  transition: all 0.3s ease;
}

.queue-item-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.queue-item-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.queue-item-move {
  transition: transform 0.3s ease;
}
</style>
