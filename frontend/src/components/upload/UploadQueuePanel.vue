<template>
  <Teleport to="body">
    <!-- Backdrop -->
    <Transition name="backdrop">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 bg-black/50"
        aria-hidden="true"
        @click="close"
      />
    </Transition>

    <!-- Slide-over Panel -->
    <Transition name="slide-over">
      <div
        v-if="modelValue"
        class="fixed inset-y-0 right-0 z-50 flex w-full flex-col overflow-hidden bg-white shadow-2xl backdrop-blur-sm dark:bg-gray-800 tablet:w-[480px]"
        role="dialog"
        aria-modal="true"
        aria-label="업로드 큐"
        @keydown.escape="close"
      >
        <!-- Header -->
        <div class="flex-shrink-0 border-b border-gray-200 bg-gradient-to-r from-primary-50 to-primary-100 px-6 py-4 dark:border-gray-700 dark:from-gray-800 dark:to-gray-900">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div class="rounded-lg bg-primary-600 p-2">
                <QueueListIcon class="h-5 w-5 text-white" />
              </div>
              <div>
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  업로드 큐
                </h2>
                <p class="text-xs text-gray-500 dark:text-gray-400">
                  {{ queueStore.queuedCount }}개 대기 중
                </p>
              </div>
            </div>
            <button
              class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-white/50 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              aria-label="모달 닫기"
              @click="close"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Overall progress bar -->
          <div class="mt-3">
            <div class="mb-1.5 flex items-center justify-between text-xs">
              <span class="font-medium text-gray-700 dark:text-gray-300">전체 진행률</span>
              <span class="font-semibold text-primary-600 dark:text-primary-400">{{ queueStore.overallProgress }}%</span>
            </div>
            <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full bg-gradient-to-r from-primary-500 to-primary-600 transition-all duration-300"
                :style="{ width: `${queueStore.overallProgress}%` }"
              />
            </div>
          </div>
        </div>

        <!-- Controls -->
        <div class="flex-shrink-0 border-b border-gray-200 bg-gray-50 px-6 py-3 dark:border-gray-700 dark:bg-gray-900/50">
          <div class="flex items-center gap-2">
            <button
              v-if="queueStore.uploadingCount > 0"
              class="inline-flex items-center gap-1.5 rounded-lg bg-amber-100 px-3 py-1.5 text-sm font-medium text-amber-700 transition-colors hover:bg-amber-200 dark:bg-amber-900/30 dark:text-amber-300 dark:hover:bg-amber-900/50"
              @click="queueStore.pauseAll()"
            >
              <PauseIcon class="h-4 w-4" />
              모두 일시정지
            </button>
            <button
              v-else-if="queueStore.queue.some((item) => item.status === 'paused')"
              class="inline-flex items-center gap-1.5 rounded-lg bg-primary-100 px-3 py-1.5 text-sm font-medium text-primary-700 transition-colors hover:bg-primary-200 dark:bg-primary-900/30 dark:text-primary-300 dark:hover:bg-primary-900/50"
              @click="queueStore.resumeAll()"
            >
              <PlayIcon class="h-4 w-4" />
              모두 재개
            </button>

            <button
              v-if="queueStore.completedCount > 0"
              class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:bg-white dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
              @click="queueStore.clearCompleted()"
            >
              <TrashIcon class="h-4 w-4" />
              완료 항목 정리
            </button>
          </div>
        </div>

        <!-- Queue items list -->
        <div class="flex-1 overflow-y-auto">
          <TransitionGroup v-if="queueStore.queue.length > 0" name="queue-item" tag="div">
            <div
              v-for="item in queueStore.queue"
              :key="item.id"
              class="border-b border-gray-100 px-6 py-4 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-900/30"
            >
              <div class="flex items-start gap-3">
                <!-- Drag handle (visual only) -->
                <div class="flex-shrink-0 cursor-grab pt-1 text-gray-400 dark:text-gray-500">
                  <Bars3Icon class="h-5 w-5" />
                </div>

                <!-- Content -->
                <div class="min-w-0 flex-1">
                  <!-- File name & title -->
                  <div class="flex items-start justify-between gap-2">
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100" :title="item.title">
                        {{ item.title }}
                      </p>
                      <p class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">
                        {{ item.fileName }} · {{ formatFileSize(item.fileSize) }}
                      </p>
                    </div>

                    <!-- Action buttons -->
                    <div class="flex items-center gap-1">
                      <template v-if="item.status === 'queued'">
                        <button
                          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-red-600 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-red-400"
                          title="제거"
                          @click="queueStore.removeFromQueue(item.id)"
                        >
                          <XMarkIcon class="h-4 w-4" />
                        </button>
                      </template>

                      <template v-else-if="item.status === 'uploading' || item.status === 'processing'">
                        <button
                          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-amber-600 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-amber-400"
                          title="일시정지"
                          @click="queueStore.pauseItem(item.id)"
                        >
                          <PauseIcon class="h-4 w-4" />
                        </button>
                      </template>

                      <template v-else-if="item.status === 'paused'">
                        <button
                          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-primary-600 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-primary-400"
                          title="재개"
                          @click="queueStore.resumeItem(item.id)"
                        >
                          <PlayIcon class="h-4 w-4" />
                        </button>
                        <button
                          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-red-600 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-red-400"
                          title="제거"
                          @click="queueStore.removeFromQueue(item.id)"
                        >
                          <XMarkIcon class="h-4 w-4" />
                        </button>
                      </template>

                      <template v-else-if="item.status === 'completed'">
                        <CheckCircleIcon class="h-5 w-5 text-green-500" />
                        <button
                          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-red-600 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-red-400"
                          title="제거"
                          @click="queueStore.removeFromQueue(item.id)"
                        >
                          <XMarkIcon class="h-4 w-4" />
                        </button>
                      </template>

                      <template v-else-if="item.status === 'failed'">
                        <button
                          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-primary-600 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-primary-400"
                          title="재시도"
                          @click="queueStore.retryItem(item.id)"
                        >
                          <ArrowPathIcon class="h-4 w-4" />
                        </button>
                        <button
                          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-red-600 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-red-400"
                          title="제거"
                          @click="queueStore.removeFromQueue(item.id)"
                        >
                          <XMarkIcon class="h-4 w-4" />
                        </button>
                      </template>
                    </div>
                  </div>

                  <!-- Platform badges -->
                  <div class="mt-2 flex flex-wrap gap-1.5">
                    <PlatformBadge
                      v-for="platform in item.platforms"
                      :key="platform"
                      :platform="platform"
                    />
                  </div>

                  <!-- Progress bar -->
                  <div v-if="item.status === 'uploading' || item.status === 'processing'" class="mt-3">
                    <div class="mb-1 flex items-center justify-between text-xs">
                      <span :class="getStatusClass(item.status)">
                        {{ getStatusText(item.status) }}
                      </span>
                      <span class="font-medium text-gray-700 dark:text-gray-300">{{ Math.round(item.progress) }}%</span>
                    </div>
                    <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                      <div
                        class="h-full rounded-full transition-all duration-300"
                        :class="getProgressBarClass(item.status)"
                        :style="{ width: `${item.progress}%` }"
                      />
                    </div>
                  </div>

                  <!-- Status text for other states -->
                  <div v-else class="mt-2">
                    <span class="text-xs font-medium" :class="getStatusClass(item.status)">
                      {{ getStatusText(item.status) }}
                    </span>
                  </div>

                  <!-- Error message -->
                  <div v-if="item.status === 'failed' && item.error" class="mt-2 rounded-lg bg-red-50 px-3 py-2 dark:bg-red-900/20">
                    <p class="text-xs text-red-600 dark:text-red-400">
                      {{ item.error }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </TransitionGroup>

          <!-- Empty state -->
          <div v-else class="flex h-full flex-col items-center justify-center px-6 py-12">
            <div class="rounded-full bg-gray-100 p-6 dark:bg-gray-800">
              <QueueListIcon class="h-12 w-12 text-gray-400 dark:text-gray-500" />
            </div>
            <h3 class="mt-4 text-sm font-medium text-gray-900 dark:text-gray-100">
              업로드 큐가 비어있습니다
            </h3>
            <p class="mt-1 text-center text-sm text-gray-500 dark:text-gray-400">
              영상을 큐에 추가하여 순차적으로 업로드할 수 있습니다.
            </p>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import {
  XMarkIcon,
  QueueListIcon,
  PlayIcon,
  PauseIcon,
  TrashIcon,
  CheckCircleIcon,
  ArrowPathIcon,
  Bars3Icon,
} from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import { useUploadQueueStore } from '@/stores/uploadQueue'
import type { QueueItemStatus } from '@/stores/uploadQueue'
import { formatFileSize } from '@/utils/format'

defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const queueStore = useUploadQueueStore()

function close() {
  emit('update:modelValue', false)
}

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
    uploading: 'text-blue-600 dark:text-blue-400',
    processing: 'text-purple-600 dark:text-purple-400',
    completed: 'text-green-600 dark:text-green-400',
    failed: 'text-red-600 dark:text-red-400',
    paused: 'text-amber-600 dark:text-amber-400',
  }
  return classMap[status]
}

function getProgressBarClass(status: QueueItemStatus): string {
  const classMap: Record<QueueItemStatus, string> = {
    queued: 'bg-gray-500',
    uploading: 'bg-blue-500',
    processing: 'bg-purple-500',
    completed: 'bg-green-500',
    failed: 'bg-red-500',
    paused: 'bg-amber-500',
  }
  return classMap[status]
}
</script>

<style scoped>
/* Backdrop transitions */
.backdrop-enter-active {
  transition: opacity 200ms ease-out;
}
.backdrop-leave-active {
  transition: opacity 150ms ease-in;
}
.backdrop-enter-from,
.backdrop-leave-to {
  opacity: 0;
}

/* Slide-over transitions */
.slide-over-enter-active {
  transition: transform 300ms cubic-bezier(0.32, 0.72, 0, 1);
}
.slide-over-leave-active {
  transition: transform 200ms ease-in;
}
.slide-over-enter-from,
.slide-over-leave-to {
  transform: translateX(100%);
}

/* Queue item transitions */
.queue-item-enter-active,
.queue-item-leave-active {
  transition: all 0.3s ease;
}
.queue-item-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.queue-item-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
.queue-item-move {
  transition: transform 0.3s ease;
}

/* Accessibility: Respect user's motion preferences */
@media (prefers-reduced-motion: reduce) {
  .backdrop-enter-active,
  .backdrop-leave-active,
  .slide-over-enter-active,
  .slide-over-leave-active,
  .queue-item-enter-active,
  .queue-item-leave-active,
  .queue-item-move {
    transition: none !important;
  }
  .slide-over-enter-from,
  .slide-over-leave-to {
    transform: none !important;
  }
}
</style>
