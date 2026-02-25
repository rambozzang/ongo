<template>
  <div class="space-y-6">
    <!-- 영상 선택 -->
    <div v-if="!selectedVideo" class="rounded-xl border-2 border-dashed border-gray-300 dark:border-gray-600 p-8 text-center">
      <FilmIcon class="mx-auto h-12 w-12 text-gray-400 dark:text-gray-500" />
      <h3 class="mt-3 text-sm font-semibold text-gray-900 dark:text-gray-100">{{ $t('contentStudio.clips.selectVideo') }}</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('contentStudio.clips.selectVideoDesc') }}</p>
      <div class="mt-4">
        <select
          v-model="selectedVideoId"
          class="input-field mx-auto max-w-xs"
          @change="handleVideoSelect"
        >
          <option value="">{{ $t('contentStudio.clips.chooseVideo') }}</option>
          <option v-for="v in videos" :key="v.id" :value="v.id">
            {{ v.title }} {{ v.duration ? `(${formatDuration(v.duration)})` : '' }}
          </option>
        </select>
      </div>
    </div>

    <!-- 선택된 영상 & 클립 편집 -->
    <template v-else>
      <!-- 선택된 영상 정보 -->
      <div class="card flex items-center gap-4 p-4">
        <div class="h-16 w-28 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-800">
          <img
            v-if="selectedVideo.thumbnailUrl"
            :src="selectedVideo.thumbnailUrl"
            :alt="selectedVideo.title"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <FilmIcon class="h-6 w-6 text-gray-400 dark:text-gray-500" />
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <h4 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ selectedVideo.title }}</h4>
          <p v-if="selectedVideo.duration" class="text-xs text-gray-500 dark:text-gray-400">
            {{ formatDuration(selectedVideo.duration) }}
          </p>
        </div>
        <button
          class="btn-secondary text-xs"
          @click="$emit('changeVideo')"
        >
          {{ $t('contentStudio.clips.changeVideo') }}
        </button>
      </div>

      <!-- 타임라인 구간 선택 -->
      <div class="card space-y-4 p-5">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentStudio.clips.timelineSelect') }}
        </h3>

        <!-- 타임라인 바 -->
        <div class="relative">
          <div class="h-12 w-full rounded-lg bg-gray-100 dark:bg-gray-800 overflow-hidden">
            <!-- 선택 구간 하이라이트 -->
            <div
              class="absolute top-0 h-full bg-primary-200 dark:bg-primary-900/40 transition-all"
              :style="{
                left: `${startPercent}%`,
                width: `${endPercent - startPercent}%`,
              }"
            />
            <!-- 시작 핸들 -->
            <div
              class="absolute top-0 h-full w-1 cursor-ew-resize bg-primary-500"
              :style="{ left: `${startPercent}%` }"
            />
            <!-- 끝 핸들 -->
            <div
              class="absolute top-0 h-full w-1 cursor-ew-resize bg-primary-500"
              :style="{ left: `${endPercent}%` }"
            />
          </div>
        </div>

        <!-- 시간 입력 -->
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1.5 block text-xs font-medium text-gray-700 dark:text-gray-300">
              {{ $t('contentStudio.clips.startTime') }}
            </label>
            <input
              v-model="clipForm.startTimeStr"
              type="text"
              class="input-field"
              placeholder="00:00"
              @change="parseStartTime"
            />
          </div>
          <div>
            <label class="mb-1.5 block text-xs font-medium text-gray-700 dark:text-gray-300">
              {{ $t('contentStudio.clips.endTime') }}
            </label>
            <input
              v-model="clipForm.endTimeStr"
              type="text"
              class="input-field"
              placeholder="00:30"
              @change="parseEndTime"
            />
          </div>
        </div>

        <!-- 클립 길이 표시 -->
        <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
          <span>{{ $t('contentStudio.clips.clipDuration') }}: {{ formatDuration(clipDuration) }}</span>
          <span v-if="clipDuration > 60" class="text-amber-600 dark:text-amber-400">
            {{ $t('contentStudio.clips.shortFormHint') }}
          </span>
        </div>

        <!-- 클립 제목 & 비율 -->
        <div>
          <label class="mb-1.5 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('contentStudio.clips.clipTitle') }}
          </label>
          <input
            v-model="clipForm.title"
            type="text"
            class="input-field"
            :placeholder="$t('contentStudio.clips.clipTitlePlaceholder')"
          />
        </div>

        <div>
          <label class="mb-1.5 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('contentStudio.clips.aspectRatio') }}
          </label>
          <div class="flex gap-2">
            <button
              v-for="ratio in aspectRatios"
              :key="ratio.value"
              class="flex-1 rounded-lg border px-3 py-2 text-center text-xs font-medium transition-colors"
              :class="clipForm.aspectRatio === ratio.value
                ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'"
              @click="clipForm.aspectRatio = ratio.value"
            >
              {{ ratio.label }}
            </button>
          </div>
        </div>

        <!-- 생성 버튼 -->
        <div class="flex justify-end gap-3 pt-2">
          <button
            class="btn-primary inline-flex items-center gap-2"
            :disabled="!canCreate || processing"
            @click="handleCreate"
          >
            <ScissorsIcon class="h-4 w-4" />
            {{ $t('contentStudio.clips.createClip') }}
            <span class="text-xs opacity-75">(10 {{ $t('contentStudio.credits') }})</span>
          </button>
        </div>
      </div>

      <!-- 생성된 클립 목록 -->
      <div v-if="clips.length > 0" class="card p-5">
        <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentStudio.clips.generatedClips') }} ({{ clips.length }})
        </h3>
        <div class="space-y-3">
          <div
            v-for="clip in clips"
            :key="clip.id"
            class="flex items-center gap-3 rounded-lg border border-gray-200 dark:border-gray-700 p-3"
          >
            <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-800">
              <img
                v-if="clip.thumbnailUrl"
                :src="clip.thumbnailUrl"
                :alt="clip.title"
                class="h-full w-full object-cover"
              />
              <div v-else class="flex h-full w-full items-center justify-center">
                <FilmIcon class="h-4 w-4 text-gray-400 dark:text-gray-500" />
              </div>
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ clip.title }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">
                {{ formatDuration(clip.startTime) }} - {{ formatDuration(clip.endTime) }}
                · {{ clip.aspectRatio }}
              </p>
            </div>
            <span
              class="shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
              :class="statusClass(clip.status)"
            >
              {{ clip.status }}
            </span>
            <button
              class="shrink-0 rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-red-500 transition-colors"
              @click="$emit('deleteClip', clip.id)"
            >
              <TrashIcon class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { FilmIcon, ScissorsIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { Clip, VideoSummary } from '@/types/contentStudio'

const props = defineProps<{
  videos: VideoSummary[]
  selectedVideo: VideoSummary | null
  clips: Clip[]
  processing: boolean
}>()

const emit = defineEmits<{
  changeVideo: []
  selectVideo: [id: number]
  createClip: [data: { title: string; startTime: number; endTime: number; aspectRatio: string }]
  deleteClip: [id: number]
}>()

const selectedVideoId = ref('')

const clipForm = ref({
  title: '',
  startTimeStr: '00:00',
  endTimeStr: '00:30',
  startTime: 0,
  endTime: 30,
  aspectRatio: '9:16',
})

const aspectRatios = [
  { value: '9:16', label: '9:16 (Shorts)' },
  { value: '1:1', label: '1:1 (Square)' },
  { value: '16:9', label: '16:9 (Wide)' },
]

const totalDuration = computed(() => props.selectedVideo?.duration ?? 300)
const clipDuration = computed(() => clipForm.value.endTime - clipForm.value.startTime)

const startPercent = computed(() => (clipForm.value.startTime / totalDuration.value) * 100)
const endPercent = computed(() => (clipForm.value.endTime / totalDuration.value) * 100)

const canCreate = computed(() =>
  clipForm.value.title.trim() !== '' &&
  clipForm.value.startTime < clipForm.value.endTime &&
  clipDuration.value >= 5,
)

function handleVideoSelect() {
  if (selectedVideoId.value) {
    emit('selectVideo', Number(selectedVideoId.value))
  }
}

function parseStartTime() {
  clipForm.value.startTime = parseTimeStr(clipForm.value.startTimeStr)
}

function parseEndTime() {
  clipForm.value.endTime = parseTimeStr(clipForm.value.endTimeStr)
}

function parseTimeStr(str: string): number {
  const parts = str.split(':').map(Number)
  if (parts.length === 2) return (parts[0] || 0) * 60 + (parts[1] || 0)
  if (parts.length === 3) return (parts[0] || 0) * 3600 + (parts[1] || 0) * 60 + (parts[2] || 0)
  return 0
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function handleCreate() {
  emit('createClip', {
    title: clipForm.value.title,
    startTime: clipForm.value.startTime,
    endTime: clipForm.value.endTime,
    aspectRatio: clipForm.value.aspectRatio,
  })
}

function statusClass(status: string): string {
  switch (status) {
    case 'COMPLETED':
      return 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'
    case 'PROCESSING':
      return 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400'
    case 'FAILED':
      return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
    default:
      return 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-400'
  }
}
</script>
