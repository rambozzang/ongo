<template>
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Panel Header -->
    <div class="border-b border-gray-200 px-4 py-3 dark:border-gray-700">
      <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('copyrightCheck.videoSelector') }}
      </h2>
    </div>

    <div class="p-4">
      <!-- Video list -->
      <div v-if="videos.length > 0" class="space-y-2">
        <p class="mb-2 text-xs text-gray-500 dark:text-gray-400">
          {{ $t('copyrightCheck.selectVideo') }}
        </p>
        <button
          v-for="video in videos"
          :key="video.id"
          class="flex w-full items-center gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors"
          :class="selectedVideoId === video.id
            ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
            : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:hover:border-gray-600 dark:hover:bg-gray-800/80'"
          @click="selectedVideoId = video.id"
        >
          <!-- Thumbnail placeholder -->
          <div class="flex h-10 w-14 flex-shrink-0 items-center justify-center rounded bg-gray-200 dark:bg-gray-700">
            <VideoCameraIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ video.title }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              {{ $t('copyrightCheck.duration') }}: {{ formatDuration(video.duration) }}
            </p>
          </div>
          <!-- Check status badge -->
          <span
            v-if="video.hasBeenChecked && video.lastCheckStatus"
            class="flex-shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
            :class="videoStatusClass(video.lastCheckStatus)"
          >
            {{ $t('copyrightCheck.checked') }}
          </span>
          <span
            v-else
            class="flex-shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-400"
          >
            {{ $t('copyrightCheck.unchecked') }}
          </span>
        </button>
      </div>

      <!-- Empty state -->
      <div v-else class="py-8 text-center">
        <VideoCameraIcon class="mx-auto h-10 w-10 text-gray-300 dark:text-gray-600" />
        <p class="mt-2 text-sm font-medium text-gray-900 dark:text-gray-100">
          {{ $t('copyrightCheck.noVideos') }}
        </p>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ $t('copyrightCheck.noVideosDesc') }}
        </p>
      </div>

      <!-- Divider -->
      <div v-if="videos.length > 0" class="my-4 border-t border-gray-200 dark:border-gray-700" />

      <!-- Check Options -->
      <div v-if="videos.length > 0" class="space-y-4">
        <h3 class="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
          {{ $t('copyrightCheck.checkOptions') }}
        </h3>
        <div class="space-y-3">
          <!-- Music toggle -->
          <label class="flex cursor-pointer items-center justify-between">
            <span class="text-sm text-gray-700 dark:text-gray-300">
              {{ $t('copyrightCheck.checkMusic') }}
            </span>
            <button
              type="button"
              role="switch"
              :aria-checked="checkMusic"
              class="relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
              :class="checkMusic ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'"
              @click="checkMusic = !checkMusic"
            >
              <span
                class="pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                :class="checkMusic ? 'translate-x-4' : 'translate-x-0'"
              />
            </button>
          </label>
          <!-- Content toggle -->
          <label class="flex cursor-pointer items-center justify-between">
            <span class="text-sm text-gray-700 dark:text-gray-300">
              {{ $t('copyrightCheck.checkContent') }}
            </span>
            <button
              type="button"
              role="switch"
              :aria-checked="checkContent"
              class="relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
              :class="checkContent ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'"
              @click="checkContent = !checkContent"
            >
              <span
                class="pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                :class="checkContent ? 'translate-x-4' : 'translate-x-0'"
              />
            </button>
          </label>
          <!-- Brand toggle -->
          <label class="flex cursor-pointer items-center justify-between">
            <span class="text-sm text-gray-700 dark:text-gray-300">
              {{ $t('copyrightCheck.checkBrand') }}
            </span>
            <button
              type="button"
              role="switch"
              :aria-checked="checkBrand"
              class="relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
              :class="checkBrand ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'"
              @click="checkBrand = !checkBrand"
            >
              <span
                class="pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                :class="checkBrand ? 'translate-x-4' : 'translate-x-0'"
              />
            </button>
          </label>
        </div>

        <!-- Platform checkboxes -->
        <div>
          <h3 class="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
            {{ $t('copyrightCheck.selectPlatforms') }}
          </h3>
          <div class="grid grid-cols-2 gap-2">
            <label
              v-for="platform in platforms"
              :key="platform.value"
              class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 transition-colors"
              :class="selectedPlatforms.includes(platform.value)
                ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                : 'border-gray-200 hover:border-gray-300 dark:border-gray-700 dark:hover:border-gray-600'"
            >
              <input
                type="checkbox"
                :value="platform.value"
                v-model="selectedPlatforms"
                class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700"
              />
              <span class="text-sm text-gray-700 dark:text-gray-300">{{ platform.label }}</span>
            </label>
          </div>
        </div>

        <!-- Run Check button -->
        <div class="pt-2">
          <button
            class="btn-primary flex w-full items-center justify-center gap-2"
            :disabled="!canRunCheck || checking"
            @click="handleRunCheck"
          >
            <template v-if="checking">
              <svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              {{ $t('copyrightCheck.checking') }}
            </template>
            <template v-else>
              <ShieldCheckIcon class="h-5 w-5" />
              {{ $t('copyrightCheck.runCheck') }}
            </template>
          </button>
          <p class="mt-1.5 text-center text-xs text-gray-500 dark:text-gray-400">
            {{ $t('copyrightCheck.creditCost', { cost: 3 }) }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  VideoCameraIcon,
  ShieldCheckIcon,
} from '@heroicons/vue/24/outline'
import type { VideoForCheck, RunCheckRequest, CheckStatus } from '@/types/copyrightCheck'

defineProps<{
  videos: VideoForCheck[]
  checking: boolean
}>()

const emit = defineEmits<{
  runCheck: [request: RunCheckRequest]
}>()

const selectedVideoId = ref<number | null>(null)
const checkMusic = ref(true)
const checkContent = ref(true)
const checkBrand = ref(false)
const selectedPlatforms = ref<string[]>(['YouTube', 'TikTok'])

const platforms = [
  { value: 'YouTube', label: 'YouTube' },
  { value: 'TikTok', label: 'TikTok' },
  { value: 'Instagram', label: 'Instagram' },
  { value: 'NaverClip', label: 'Naver Clip' },
]

const canRunCheck = computed(() => {
  return selectedVideoId.value !== null && selectedPlatforms.value.length > 0
})

function handleRunCheck() {
  if (!selectedVideoId.value) return
  emit('runCheck', {
    videoId: selectedVideoId.value,
    platforms: selectedPlatforms.value,
    checkMusic: checkMusic.value,
    checkContent: checkContent.value,
    checkBrand: checkBrand.value,
  })
}

function videoStatusClass(status: CheckStatus): string {
  const map: Record<string, string> = {
    PASSED: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    WARNING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    BLOCKED: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  }
  return map[status] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
}

function formatDuration(seconds: number): string {
  const min = Math.floor(seconds / 60)
  const sec = Math.floor(seconds % 60)
  return `${min}:${sec.toString().padStart(2, '0')}`
}
</script>
