<template>
  <!-- Mobile Layout -->
  <div v-if="!isTablet" class="space-y-4">
    <!-- Header -->
    <div>
      <h1 class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ $t('videoResizer.title') }}
      </h1>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('videoResizer.description') }}
      </p>
    </div>

    <PageGuide
      :title="$t('videoResizer.pageGuideTitle')"
      :items="($tm('videoResizer.pageGuideMobile') as string[])"
    />

    <!-- Credit Display -->
    <div
      class="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs"
      :class="isLow
        ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
    >
      <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
      <span class="text-gray-600 dark:text-gray-300">{{ $t('videoResizer.creditsRemaining') }}</span>
      <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
        {{ balance.toLocaleString() }}
      </span>
    </div>

    <!-- Video Selector -->
    <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
      <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('videoResizer.selectVideo') }}
      </label>
      <div class="relative">
        <button
          type="button"
          class="flex w-full items-center justify-between rounded-lg border border-gray-300 bg-white px-3 py-2.5 text-left text-sm transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:hover:bg-gray-700"
          @click="showVideoDropdown = !showVideoDropdown"
        >
          <div v-if="selectedVideo" class="flex items-center gap-2">
            <div class="h-8 w-12 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-700">
              <img :src="selectedVideo.thumbnailUrl" :alt="selectedVideo.title" class="h-full w-full object-cover" />
            </div>
            <span class="truncate text-gray-900 dark:text-gray-100">{{ selectedVideo.title }}</span>
          </div>
          <span v-else class="text-gray-400">{{ $t('videoResizer.chooseVideo') }}</span>
          <ChevronDownIcon class="h-5 w-5 flex-shrink-0 text-gray-400" />
        </button>

        <!-- Dropdown -->
        <div
          v-if="showVideoDropdown"
          class="absolute z-20 mt-1 max-h-60 w-full overflow-y-auto rounded-lg border border-gray-200 bg-white shadow-lg dark:border-gray-700 dark:bg-gray-800"
        >
          <button
            v-for="video in videos"
            :key="video.id"
            type="button"
            class="flex w-full items-center gap-3 p-3 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
            @click="handleSelectVideo(video)"
          >
            <div class="h-10 w-16 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-700">
              <img :src="video.thumbnailUrl" :alt="video.title" class="h-full w-full object-cover" />
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ video.title }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ video.aspectRatio }} &middot; {{ formatDuration(video.duration) }}</p>
            </div>
          </button>
          <div v-if="videos.length === 0" class="p-4 text-center text-sm text-gray-400">
            {{ $t('videoResizer.noVideos') }}
          </div>
        </div>
      </div>
    </div>

    <!-- Platform Presets -->
    <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
      <h3 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('videoResizer.platformPresets') }}
      </h3>
      <div class="grid grid-cols-3 gap-2">
        <PlatformPresetCard
          v-for="preset in platformPresets"
          :key="preset.platform"
          :preset="preset"
          :is-selected="selectedPresets.includes(preset.platform)"
          :disabled="!selectedVideo"
          @select="togglePreset(preset.platform)"
        />
      </div>
    </div>

    <!-- Smart Crop Toggle -->
    <div class="flex items-center justify-between rounded-xl border border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-800">
      <div class="flex items-center gap-2">
        <BoltIcon class="h-4 w-4 text-yellow-500" />
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('videoResizer.smartCrop') }}</span>
      </div>
      <button
        type="button"
        class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
        :class="smartCropEnabled ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'"
        @click="smartCropEnabled = !smartCropEnabled"
      >
        <span
          class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
          :class="smartCropEnabled ? 'translate-x-5' : 'translate-x-0'"
        />
      </button>
    </div>

    <!-- Preview -->
    <ResizePreview
      v-if="selectedVideo && selectedPresets.length > 0"
      :original-aspect-ratio="selectedVideo.aspectRatio"
      :target-aspect-ratio="activePreviewRatio"
      :focus-point="focusPoint"
      :smart-crop="smartCropEnabled"
      @update:focus-point="focusPoint = $event"
    />

    <!-- Resize Button -->
    <button
      class="w-full rounded-xl bg-primary-600 px-4 py-3 text-sm font-semibold text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
      :disabled="!canResize || processing"
      @click="handleResize"
    >
      <span v-if="processing" class="inline-flex items-center gap-2">
        <svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        {{ $t('videoResizer.resizing') }}
      </span>
      <span v-else>
        {{ $t('videoResizer.startResize') }} ({{ selectedPresets.length }} {{ $t('videoResizer.targets') }})
      </span>
    </button>

    <!-- Job History -->
    <div>
      <h3 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('videoResizer.jobHistory') }}
      </h3>
      <div v-if="selectedVideoJobs.length > 0" class="space-y-3">
        <ResizeJobCard
          v-for="job in selectedVideoJobs"
          :key="job.id"
          :job="job"
          @cancel="handleCancelJob"
          @download="handleDownloadJob"
          @delete="handleDeleteJob"
        />
      </div>
      <div v-else class="rounded-xl border border-dashed border-gray-300 bg-gray-50 py-8 text-center dark:border-gray-600 dark:bg-gray-800/50">
        <FilmIcon class="mx-auto h-8 w-8 text-gray-400 dark:text-gray-500" />
        <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">{{ $t('videoResizer.noJobs') }}</p>
      </div>
    </div>
  </div>

  <!-- Desktop/Tablet Layout -->
  <div v-else>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('videoResizer.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('videoResizer.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <div
          class="flex items-center gap-2 rounded-lg border px-4 py-2 text-sm"
          :class="isLow
            ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
            : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
        >
          <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
          <span class="text-gray-600 dark:text-gray-300">{{ $t('videoResizer.creditsRemaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <PageGuide
      :title="$t('videoResizer.pageGuideTitle')"
      :items="($tm('videoResizer.pageGuideDesktop') as string[])"
    />

    <!-- Error Message -->
    <div
      v-if="error"
      class="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-400"
    >
      {{ error }}
      <button class="ml-2 underline" @click="store.error = null">{{ $t('videoResizer.dismiss') }}</button>
    </div>

    <!-- Main Content: Side by Side -->
    <div class="grid gap-6 desktop:grid-cols-5">
      <!-- Left: Controls (3 cols) -->
      <div class="space-y-6 desktop:col-span-3">
        <!-- Video Selector -->
        <div class="rounded-xl border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
          <label class="mb-3 block text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('videoResizer.selectVideo') }}
          </label>
          <div class="relative">
            <button
              type="button"
              class="flex w-full items-center justify-between rounded-lg border border-gray-300 bg-white px-4 py-3 text-left text-sm transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:hover:bg-gray-700"
              @click="showVideoDropdown = !showVideoDropdown"
            >
              <div v-if="selectedVideo" class="flex items-center gap-3">
                <div class="h-10 w-16 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
                  <img :src="selectedVideo.thumbnailUrl" :alt="selectedVideo.title" class="h-full w-full object-cover" />
                </div>
                <div>
                  <p class="font-medium text-gray-900 dark:text-gray-100">{{ selectedVideo.title }}</p>
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    {{ selectedVideo.aspectRatio }} &middot; {{ formatDuration(selectedVideo.duration) }}
                  </p>
                </div>
              </div>
              <span v-else class="text-gray-400">{{ $t('videoResizer.chooseVideo') }}</span>
              <ChevronDownIcon class="h-5 w-5 flex-shrink-0 text-gray-400" />
            </button>

            <!-- Dropdown -->
            <div
              v-if="showVideoDropdown"
              class="absolute z-20 mt-1 max-h-80 w-full overflow-y-auto rounded-lg border border-gray-200 bg-white shadow-lg dark:border-gray-700 dark:bg-gray-800"
            >
              <button
                v-for="video in videos"
                :key="video.id"
                type="button"
                class="flex w-full items-center gap-3 p-3 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                @click="handleSelectVideo(video)"
              >
                <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
                  <img :src="video.thumbnailUrl" :alt="video.title" class="h-full w-full object-cover" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ video.title }}</p>
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    {{ video.aspectRatio }} &middot; {{ formatDuration(video.duration) }}
                  </p>
                </div>
              </button>
              <div v-if="videos.length === 0" class="p-6 text-center text-sm text-gray-400">
                {{ $t('videoResizer.noVideos') }}
              </div>
            </div>
          </div>
        </div>

        <!-- Platform Presets -->
        <div class="rounded-xl border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
          <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('videoResizer.platformPresets') }}
          </h3>
          <div class="grid grid-cols-5 gap-3">
            <PlatformPresetCard
              v-for="preset in platformPresets"
              :key="preset.platform"
              :preset="preset"
              :is-selected="selectedPresets.includes(preset.platform)"
              :disabled="!selectedVideo"
              @select="togglePreset(preset.platform)"
            />
          </div>
        </div>

        <!-- Smart Crop Toggle + Resize Button -->
        <div class="flex items-center gap-4">
          <div class="flex flex-1 items-center justify-between rounded-xl border border-gray-200 bg-white px-5 py-3.5 dark:border-gray-700 dark:bg-gray-800">
            <div class="flex items-center gap-2">
              <BoltIcon class="h-5 w-5 text-yellow-500" />
              <div>
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('videoResizer.smartCrop') }}</span>
                <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videoResizer.smartCropDesc') }}</p>
              </div>
            </div>
            <button
              type="button"
              class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
              :class="smartCropEnabled ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'"
              @click="smartCropEnabled = !smartCropEnabled"
            >
              <span
                class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                :class="smartCropEnabled ? 'translate-x-5' : 'translate-x-0'"
              />
            </button>
          </div>

          <button
            class="flex-shrink-0 rounded-xl bg-primary-600 px-6 py-3.5 text-sm font-semibold text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="!canResize || processing"
            @click="handleResize"
          >
            <span v-if="processing" class="inline-flex items-center gap-2">
              <svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              {{ $t('videoResizer.resizing') }}
            </span>
            <span v-else>
              {{ $t('videoResizer.startResize') }} ({{ selectedPresets.length }})
            </span>
          </button>
        </div>
      </div>

      <!-- Right: Preview (2 cols) -->
      <div class="space-y-6 desktop:col-span-2">
        <!-- Preview -->
        <ResizePreview
          v-if="selectedVideo && selectedPresets.length > 0"
          :original-aspect-ratio="selectedVideo.aspectRatio"
          :target-aspect-ratio="activePreviewRatio"
          :focus-point="focusPoint"
          :smart-crop="smartCropEnabled"
          @update:focus-point="focusPoint = $event"
        />

        <!-- Preview Preset Tabs (when multiple selected) -->
        <div v-if="selectedPresets.length > 1" class="flex flex-wrap gap-2">
          <button
            v-for="platform in selectedPresets"
            :key="platform"
            class="rounded-lg px-3 py-1.5 text-xs font-medium transition-colors"
            :class="activePreviewPlatform === platform
              ? 'bg-primary-600 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600'"
            @click="activePreviewPlatform = platform"
          >
            {{ getPresetLabel(platform) }}
          </button>
        </div>

        <!-- No Preview Placeholder -->
        <div
          v-if="!selectedVideo || selectedPresets.length === 0"
          class="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-gray-300 bg-gray-50 py-16 text-center dark:border-gray-600 dark:bg-gray-800/50"
        >
          <ArrowsPointingOutIcon class="mx-auto h-10 w-10 text-gray-400 dark:text-gray-500" />
          <p class="mt-3 text-sm font-medium text-gray-500 dark:text-gray-400">
            {{ $t('videoResizer.previewPlaceholder') }}
          </p>
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
            {{ $t('videoResizer.previewHint') }}
          </p>
        </div>
      </div>
    </div>

    <!-- Job History -->
    <div class="mt-8">
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('videoResizer.jobHistory') }}
        </h3>
        <span class="text-sm text-gray-500 dark:text-gray-400">
          {{ selectedVideoJobs.length }} {{ $t('videoResizer.jobCount') }}
        </span>
      </div>
      <div v-if="selectedVideoJobs.length > 0" class="grid gap-4 tablet:grid-cols-2">
        <ResizeJobCard
          v-for="job in selectedVideoJobs"
          :key="job.id"
          :job="job"
          @cancel="handleCancelJob"
          @download="handleDownloadJob"
          @delete="handleDeleteJob"
        />
      </div>
      <div v-else class="rounded-xl border border-dashed border-gray-300 bg-gray-50 py-12 text-center dark:border-gray-600 dark:bg-gray-800/50">
        <FilmIcon class="mx-auto h-10 w-10 text-gray-400 dark:text-gray-500" />
        <p class="mt-3 text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('videoResizer.noJobs') }}</p>
        <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ $t('videoResizer.noJobsHint') }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  SparklesIcon,
  ChevronDownIcon,
  FilmIcon,
  BoltIcon,
  ArrowsPointingOutIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import PlatformPresetCard from '@/components/videoresizer/PlatformPresetCard.vue'
import ResizePreview from '@/components/videoresizer/ResizePreview.vue'
import ResizeJobCard from '@/components/videoresizer/ResizeJobCard.vue'
import { useVideoResizerStore } from '@/stores/videoResizer'
import { useCredit } from '@/composables/useCredit'
import type { PlatformTarget, ResizePreset, VideoForResize } from '@/types/videoResizer'

const { t } = useI18n({ useScope: 'global' })
const store = useVideoResizerStore()
const { balance, isLow, fetchBalance } = useCredit()
const isTablet = useMediaQuery('(min-width: 768px)')

const {
  videos,
  selectedVideo,
  selectedVideoJobs,
  processing,
  error,
} = storeToRefs(store)

// Local state
const showVideoDropdown = ref(false)
const selectedPresets = ref<PlatformTarget[]>([])
const smartCropEnabled = ref(true)
const focusPoint = ref({ x: 0.5, y: 0.5 })
const activePreviewPlatform = ref<PlatformTarget>('YOUTUBE')

// Platform presets
const platformPresets: ResizePreset[] = [
  { platform: 'YOUTUBE', label: 'YouTube', aspectRatio: '16:9', width: 1920, height: 1080 },
  { platform: 'TIKTOK', label: 'TikTok', aspectRatio: '9:16', width: 1080, height: 1920 },
  { platform: 'INSTAGRAM_REELS', label: t('videoResizer.presetReels'), aspectRatio: '9:16', width: 1080, height: 1920 },
  { platform: 'NAVER_CLIP', label: t('videoResizer.presetNaverClip'), aspectRatio: '9:16', width: 1080, height: 1920 },
  { platform: 'YOUTUBE_SHORTS', label: t('videoResizer.presetShorts'), aspectRatio: '9:16', width: 1080, height: 1920 },
]

// Computed
const canResize = computed(() => selectedVideo.value && selectedPresets.value.length > 0)

const activePreviewRatio = computed(() => {
  if (selectedPresets.value.length === 0) return '16:9'
  const activePlatform = selectedPresets.value.includes(activePreviewPlatform.value)
    ? activePreviewPlatform.value
    : selectedPresets.value[0]
  const preset = platformPresets.find(p => p.platform === activePlatform)
  return preset?.aspectRatio ?? '16:9'
})

// Methods
function togglePreset(platform: PlatformTarget) {
  const index = selectedPresets.value.indexOf(platform)
  if (index === -1) {
    selectedPresets.value.push(platform)
    activePreviewPlatform.value = platform
  } else {
    selectedPresets.value.splice(index, 1)
  }
}

function handleSelectVideo(video: VideoForResize) {
  store.selectVideo(video)
  showVideoDropdown.value = false
  selectedPresets.value = []
  focusPoint.value = { x: 0.5, y: 0.5 }
  store.fetchJobs()
}

function getPresetLabel(platform: PlatformTarget): string {
  const preset = platformPresets.find(p => p.platform === platform)
  return preset?.label ?? platform
}

async function handleResize() {
  if (!canResize.value) return

  const targets = selectedPresets.value.map(platform => {
    const preset = platformPresets.find(p => p.platform === platform)
    return {
      platform,
      aspectRatio: preset?.aspectRatio ?? '9:16',
      smartCrop: smartCropEnabled.value,
    }
  })

  await store.createResize(targets)
  await fetchBalance()
}

async function handleCancelJob(jobId: number) {
  await store.cancelJob(jobId)
}

function handleDownloadJob(jobId: number) {
  window.open(`/api/v1/video-resizer/jobs/${jobId}/download`, '_blank')
}

async function handleDeleteJob(jobId: number) {
  const job = store.jobs.find(j => j.id === jobId)
  if (job) {
    store.jobs = store.jobs.filter(j => j.id !== jobId)
  }
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

// Lifecycle
onMounted(async () => {
  await store.fetchVideos()
  await store.fetchJobs()
  await fetchBalance()
})
</script>
