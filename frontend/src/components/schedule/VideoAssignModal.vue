<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import {
  XMarkIcon,
  MagnifyingGlassIcon,
  FilmIcon,
  CheckIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { videoApi } from '@/api/video'
import type { Video } from '@/types/video'

const emit = defineEmits<{
  close: []
  select: [video: { id: number; title: string; thumbnail?: string }]
}>()

// ── State ────────────────────────────────────────────────────────────
const videos = ref<Video[]>([])
const isLoading = ref(false)
const loadError = ref<string | null>(null)

// ── Load videos from API ─────────────────────────────────────────────
async function loadVideos() {
  isLoading.value = true
  loadError.value = null
  try {
    const page = await videoApi.list({ page: 0, size: 50, sort: 'createdAt', direction: 'DESC' })
    videos.value = page.content
  } catch {
    loadError.value = '영상 목록을 불러올 수 없습니다.'
  } finally {
    isLoading.value = false
  }
}

onMounted(loadVideos)

// ── Helpers ──────────────────────────────────────────────────────────
function formatDuration(seconds: number | null): string {
  if (!seconds) return '--:--'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function formatDate(dateStr: string): string {
  return dateStr.slice(0, 10)
}

// ── Search & selection ───────────────────────────────────────────────
const searchQuery = ref('')
const selectedVideoId = ref<number | null>(null)

const filteredVideos = computed(() => {
  if (!searchQuery.value.trim()) return videos.value
  const q = searchQuery.value.toLowerCase()
  return videos.value.filter((v) => v.title.toLowerCase().includes(q))
})

function selectVideo(video: Video): void {
  selectedVideoId.value = video.id
}

function confirmSelection(): void {
  const video = videos.value.find((v) => v.id === selectedVideoId.value)
  if (video) {
    emit('select', {
      id: video.id,
      title: video.title,
      thumbnail: video.thumbnailUrl ?? undefined,
    })
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="video-assign-modal-title"
      @click.self="emit('close')"
    >
      <div
        class="mx-4 flex max-h-[80vh] w-full max-w-md flex-col rounded-xl bg-white shadow-xl dark:bg-gray-800"
        @keydown.escape="emit('close')"
      >
        <!-- Header -->
        <div class="flex items-center justify-between border-b border-gray-200 px-5 py-4 dark:border-gray-700">
          <h2 id="video-assign-modal-title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            영상 배정
          </h2>
          <button
            class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
            aria-label="닫기"
            @click="emit('close')"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- Search -->
        <div class="border-b border-gray-200 px-5 py-3 dark:border-gray-700">
          <div class="relative">
            <MagnifyingGlassIcon class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              v-model="searchQuery"
              type="text"
              placeholder="영상 제목 검색..."
              class="block w-full rounded-lg border border-gray-300 bg-white py-2 pl-9 pr-3 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 dark:placeholder-gray-500"
            />
          </div>
        </div>

        <!-- Video list -->
        <div class="flex-1 overflow-y-auto px-2 py-2">
          <!-- Loading -->
          <div v-if="isLoading" class="flex items-center justify-center py-10">
            <LoadingSpinner size="md" />
          </div>

          <!-- Error -->
          <div
            v-else-if="loadError"
            class="flex flex-col items-center justify-center py-10"
          >
            <p class="text-sm text-red-500">{{ loadError }}</p>
            <button class="mt-2 text-sm text-primary-600 hover:underline" @click="loadVideos">다시 시도</button>
          </div>

          <!-- Empty -->
          <div
            v-else-if="filteredVideos.length === 0"
            class="flex flex-col items-center justify-center py-10"
          >
            <FilmIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
            <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
              {{ searchQuery.trim() ? '검색 결과가 없습니다' : '등록된 영상이 없습니다' }}
            </p>
          </div>

          <button
            v-for="video in filteredVideos"
            :key="video.id"
            type="button"
            :class="[
              'flex w-full items-center gap-3 rounded-lg p-2 text-left transition-colors',
              selectedVideoId === video.id
                ? 'bg-primary-50 ring-2 ring-primary-500 dark:bg-primary-900/20'
                : 'hover:bg-gray-50 dark:hover:bg-gray-700/50',
            ]"
            @click="selectVideo(video)"
          >
            <!-- Thumbnail -->
            <div class="relative h-14 w-24 shrink-0 overflow-hidden rounded-lg bg-gray-200 dark:bg-gray-700">
              <img
                v-if="video.thumbnailUrl"
                :src="video.thumbnailUrl"
                :alt="video.title"
                class="h-full w-full object-cover"
              />
              <FilmIcon v-else class="mx-auto mt-3 h-8 w-8 text-gray-400" />
              <!-- Duration badge -->
              <span class="absolute bottom-0.5 right-0.5 rounded bg-black/75 px-1 py-0.5 text-[10px] font-medium text-white">
                {{ formatDuration(video.duration) }}
              </span>
            </div>

            <!-- Info -->
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                {{ video.title }}
              </p>
              <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                {{ formatDate(video.createdAt) }}
              </p>
            </div>

            <!-- Selected indicator -->
            <div
              v-if="selectedVideoId === video.id"
              class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary-500 text-white"
            >
              <CheckIcon class="h-4 w-4" />
            </div>
          </button>
        </div>

        <!-- Footer -->
        <div class="flex items-center justify-end gap-2 border-t border-gray-200 px-5 py-4 dark:border-gray-700">
          <button
            class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            @click="emit('close')"
          >
            취소
          </button>
          <button
            :disabled="selectedVideoId === null"
            :class="[
              'rounded-lg px-4 py-2 text-sm font-medium text-white transition-colors',
              selectedVideoId !== null
                ? 'bg-primary-500 hover:bg-primary-600'
                : 'cursor-not-allowed bg-gray-300 dark:bg-gray-600',
            ]"
            @click="confirmSelection"
          >
            배정하기
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
