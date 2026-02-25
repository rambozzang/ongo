<template>
  <div class="space-y-6">
    <!-- 영상 선택 -->
    <div v-if="!selectedVideo" class="rounded-xl border-2 border-dashed border-gray-300 dark:border-gray-600 p-8 text-center">
      <PhotoIcon class="mx-auto h-12 w-12 text-gray-400 dark:text-gray-500" />
      <h3 class="mt-3 text-sm font-semibold text-gray-900 dark:text-gray-100">{{ $t('contentStudio.thumbnails.selectVideo') }}</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('contentStudio.thumbnails.selectVideoDesc') }}</p>
      <div class="mt-4">
        <select
          v-model="selectedVideoId"
          class="input-field mx-auto max-w-xs"
          @change="handleVideoSelect"
        >
          <option value="">{{ $t('contentStudio.thumbnails.chooseVideo') }}</option>
          <option v-for="v in videos" :key="v.id" :value="v.id">
            {{ v.title }}
          </option>
        </select>
      </div>
    </div>

    <template v-else>
      <!-- 선택된 영상 정보 -->
      <div class="card flex items-center gap-4 p-4">
        <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-800">
          <img
            v-if="selectedVideo.thumbnailUrl"
            :src="selectedVideo.thumbnailUrl"
            :alt="selectedVideo.title"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <FilmIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <h4 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ selectedVideo.title }}</h4>
        </div>
        <button class="btn-secondary text-xs" @click="$emit('changeVideo')">
          {{ $t('contentStudio.thumbnails.changeVideo') }}
        </button>
      </div>

      <!-- 썸네일 생성 옵션 -->
      <div class="card space-y-4 p-5">
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('contentStudio.thumbnails.aiGenerate') }}
          </h3>
          <span class="text-xs text-gray-500 dark:text-gray-400">
            5 {{ $t('contentStudio.credits') }}
          </span>
        </div>

        <!-- 스타일 선택 -->
        <div>
          <label class="mb-2 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('contentStudio.thumbnails.style') }}
          </label>
          <div class="grid grid-cols-2 gap-2 tablet:grid-cols-5">
            <button
              v-for="style in thumbnailStyles"
              :key="style.value"
              class="rounded-lg border p-3 text-center transition-all"
              :class="form.style === style.value
                ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 ring-1 ring-primary-300 dark:ring-primary-600'
                : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'"
              @click="form.style = style.value"
            >
              <component :is="style.icon" class="mx-auto h-6 w-6 mb-1" :class="form.style === style.value ? 'text-primary-600 dark:text-primary-400' : 'text-gray-400 dark:text-gray-500'" />
              <span class="block text-xs font-medium" :class="form.style === style.value ? 'text-primary-700 dark:text-primary-300' : 'text-gray-600 dark:text-gray-300'">
                {{ style.label }}
              </span>
            </button>
          </div>
        </div>

        <!-- 텍스트 오버레이 -->
        <div>
          <label class="mb-1.5 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('contentStudio.thumbnails.overlayText') }}
          </label>
          <input
            v-model="form.overlayText"
            type="text"
            class="input-field"
            :placeholder="$t('contentStudio.thumbnails.overlayPlaceholder')"
          />
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
            {{ $t('contentStudio.thumbnails.overlayHint') }}
          </p>
        </div>

        <!-- 생성 개수 -->
        <div>
          <label class="mb-1.5 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('contentStudio.thumbnails.count') }}
          </label>
          <div class="flex gap-2">
            <button
              v-for="n in [1, 2, 4]"
              :key="n"
              class="flex-1 rounded-lg border px-3 py-2 text-center text-sm font-medium transition-colors"
              :class="form.count === n
                ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'"
              @click="form.count = n"
            >
              {{ n }}{{ $t('contentStudio.thumbnails.countUnit') }}
            </button>
          </div>
        </div>

        <!-- 생성 버튼 -->
        <div class="flex justify-end pt-2">
          <button
            class="btn-primary inline-flex items-center gap-2"
            :disabled="processing"
            @click="handleGenerate"
          >
            <SparklesIcon class="h-4 w-4" />
            {{ $t('contentStudio.thumbnails.generate') }}
          </button>
        </div>
      </div>

      <!-- 생성된 썸네일 그리드 -->
      <div v-if="thumbnails.length > 0" class="card p-5">
        <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentStudio.thumbnails.generated') }} ({{ thumbnails.length }})
        </h3>
        <div class="grid grid-cols-2 gap-3 tablet:grid-cols-3 desktop:grid-cols-4">
          <div
            v-for="thumb in thumbnails"
            :key="thumb.id"
            class="group relative overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700"
          >
            <div class="aspect-video bg-gray-100 dark:bg-gray-800">
              <img
                v-if="thumb.imageUrl"
                :src="thumb.imageUrl"
                :alt="thumb.overlayText ?? ''"
                class="h-full w-full object-cover"
              />
              <div v-else class="flex h-full w-full items-center justify-center">
                <PhotoIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
              </div>
            </div>
            <!-- 호버 오버레이 -->
            <div class="absolute inset-0 flex items-end bg-gradient-to-t from-black/60 to-transparent opacity-0 transition-opacity group-hover:opacity-100">
              <div class="w-full p-2">
                <div class="flex items-center justify-between">
                  <span class="rounded-full bg-white/20 px-2 py-0.5 text-xs text-white backdrop-blur-sm">
                    {{ thumb.style }}
                  </span>
                  <button
                    class="rounded-full bg-white/20 p-1 text-white backdrop-blur-sm hover:bg-white/30 transition-colors"
                    @click="$emit('deleteThumbnail', thumb.id)"
                  >
                    <TrashIcon class="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>
            </div>
            <!-- 상태 뱃지 -->
            <div
              v-if="thumb.status !== 'COMPLETED'"
              class="absolute right-1 top-1 rounded-full px-2 py-0.5 text-xs font-medium"
              :class="thumbStatusClass(thumb.status)"
            >
              {{ thumb.status }}
            </div>
          </div>
        </div>
      </div>

      <!-- 빈 상태 -->
      <div
        v-else-if="!processing"
        class="rounded-lg border border-dashed border-gray-300 dark:border-gray-600 p-8 text-center"
      >
        <PhotoIcon class="mx-auto h-8 w-8 text-gray-400 dark:text-gray-500" />
        <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">{{ $t('contentStudio.thumbnails.empty') }}</p>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  PhotoIcon,
  FilmIcon,
  SparklesIcon,
  TrashIcon,
  // 스타일 아이콘
  MinusIcon,
  BoltIcon,
  FilmIcon as CinemaIcon,
  FaceSmileIcon,
  BriefcaseIcon,
} from '@heroicons/vue/24/outline'
import type { Thumbnail, ThumbnailStyle, VideoSummary } from '@/types/contentStudio'

const props = defineProps<{
  videos: VideoSummary[]
  selectedVideo: VideoSummary | null
  thumbnails: Thumbnail[]
  processing: boolean
}>()

const emit = defineEmits<{
  changeVideo: []
  selectVideo: [id: number]
  generateThumbnail: [data: { videoId: number; style: ThumbnailStyle; overlayText?: string; count: number }]
  deleteThumbnail: [id: number]
}>()

const selectedVideoId = ref('')

const form = ref({
  style: 'BOLD' as ThumbnailStyle,
  overlayText: '',
  count: 2,
})

const thumbnailStyles: { value: ThumbnailStyle; label: string; icon: typeof MinusIcon }[] = [
  { value: 'MINIMAL', label: 'Minimal', icon: MinusIcon },
  { value: 'BOLD', label: 'Bold', icon: BoltIcon },
  { value: 'CINEMATIC', label: 'Cinematic', icon: CinemaIcon },
  { value: 'PLAYFUL', label: 'Playful', icon: FaceSmileIcon },
  { value: 'PROFESSIONAL', label: 'Pro', icon: BriefcaseIcon },
]

function handleVideoSelect() {
  if (selectedVideoId.value) {
    emit('selectVideo', Number(selectedVideoId.value))
  }
}

function handleGenerate() {
  if (!props.selectedVideo) return
  emit('generateThumbnail', {
    videoId: props.selectedVideo.id,
    style: form.value.style,
    overlayText: form.value.overlayText || undefined,
    count: form.value.count,
  })
}

function thumbStatusClass(status: string): string {
  switch (status) {
    case 'GENERATING':
      return 'bg-blue-500/80 text-white'
    case 'FAILED':
      return 'bg-red-500/80 text-white'
    default:
      return 'bg-gray-500/80 text-white'
  }
}
</script>
