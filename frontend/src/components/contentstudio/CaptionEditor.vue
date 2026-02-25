<template>
  <div class="space-y-6">
    <!-- 영상 선택 -->
    <div v-if="!selectedVideo" class="rounded-xl border-2 border-dashed border-gray-300 dark:border-gray-600 p-8 text-center">
      <LanguageIcon class="mx-auto h-12 w-12 text-gray-400 dark:text-gray-500" />
      <h3 class="mt-3 text-sm font-semibold text-gray-900 dark:text-gray-100">{{ $t('contentStudio.captions.selectVideo') }}</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('contentStudio.captions.selectVideoDesc') }}</p>
      <div class="mt-4">
        <select
          v-model="selectedVideoId"
          class="input-field mx-auto max-w-xs"
          @change="handleVideoSelect"
        >
          <option value="">{{ $t('contentStudio.captions.chooseVideo') }}</option>
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
          {{ $t('contentStudio.captions.changeVideo') }}
        </button>
      </div>

      <!-- 자막 생성 -->
      <div class="card space-y-4 p-5">
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('contentStudio.captions.aiGenerate') }}
          </h3>
          <span class="text-xs text-gray-500 dark:text-gray-400">
            8 {{ $t('contentStudio.credits') }}
          </span>
        </div>

        <div>
          <label class="mb-1.5 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('contentStudio.captions.language') }}
          </label>
          <select v-model="captionLang" class="input-field max-w-xs">
            <option value="ko">한국어</option>
            <option value="en">English</option>
            <option value="ja">日本語</option>
            <option value="zh">中文</option>
          </select>
        </div>

        <div class="flex justify-end">
          <button
            class="btn-primary inline-flex items-center gap-2"
            :disabled="processing"
            @click="$emit('generateCaption', { videoId: selectedVideo.id, language: captionLang })"
          >
            <SparklesIcon class="h-4 w-4" />
            {{ $t('contentStudio.captions.generate') }}
          </button>
        </div>
      </div>

      <!-- 자막 편집 목록 -->
      <div v-if="captions.length > 0" class="space-y-4">
        <div
          v-for="caption in captions"
          :key="caption.id"
          class="card p-5"
        >
          <div class="mb-4 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                {{ languageLabel(caption.language) }}
              </h4>
              <span
                class="rounded-full px-2 py-0.5 text-xs font-medium"
                :class="captionStatusClass(caption.status)"
              >
                {{ caption.status }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <button
                class="btn-secondary text-xs inline-flex items-center gap-1"
                @click="toggleEdit(caption.id)"
              >
                <PencilIcon class="h-3.5 w-3.5" />
                {{ editingId === caption.id ? $t('contentStudio.captions.done') : $t('contentStudio.captions.edit') }}
              </button>
              <button
                class="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-red-500 transition-colors"
                @click="$emit('deleteCaption', caption.id)"
              >
                <TrashIcon class="h-4 w-4" />
              </button>
            </div>
          </div>

          <!-- 자막 세그먼트 -->
          <div class="max-h-80 space-y-2 overflow-y-auto">
            <div
              v-for="(segment, idx) in caption.segments"
              :key="idx"
              class="flex gap-3 rounded-lg border border-gray-100 dark:border-gray-700 p-3 transition-colors"
              :class="editingId === caption.id ? 'bg-white dark:bg-gray-800' : 'bg-gray-50 dark:bg-gray-900'"
            >
              <!-- 타임스탬프 -->
              <div class="flex-shrink-0 text-xs font-mono text-primary-600 dark:text-primary-400">
                <div>{{ formatTimestamp(segment.startTime) }}</div>
                <div class="text-gray-400 dark:text-gray-500">{{ formatTimestamp(segment.endTime) }}</div>
              </div>
              <!-- 텍스트 -->
              <div class="min-w-0 flex-1">
                <template v-if="editingId === caption.id">
                  <textarea
                    v-model="segment.text"
                    class="input-field min-h-[40px] resize-y text-sm"
                    rows="1"
                  />
                </template>
                <p v-else class="text-sm text-gray-700 dark:text-gray-300">{{ segment.text }}</p>
              </div>
            </div>
          </div>

          <!-- 저장 버튼 -->
          <div v-if="editingId === caption.id" class="mt-4 flex justify-end">
            <button
              class="btn-primary text-sm"
              @click="$emit('updateCaption', caption.id, { segments: caption.segments })"
            >
              {{ $t('contentStudio.captions.save') }}
            </button>
          </div>
        </div>
      </div>

      <!-- 빈 상태 -->
      <div
        v-else-if="!processing"
        class="rounded-lg border border-dashed border-gray-300 dark:border-gray-600 p-8 text-center"
      >
        <LanguageIcon class="mx-auto h-8 w-8 text-gray-400 dark:text-gray-500" />
        <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">{{ $t('contentStudio.captions.empty') }}</p>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  FilmIcon,
  SparklesIcon,
  PencilIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { LanguageIcon } from '@heroicons/vue/24/outline'
import type { Caption, VideoSummary } from '@/types/contentStudio'

defineProps<{
  videos: VideoSummary[]
  selectedVideo: VideoSummary | null
  captions: Caption[]
  processing: boolean
}>()

defineEmits<{
  changeVideo: []
  selectVideo: [id: number]
  generateCaption: [data: { videoId: number; language: string }]
  updateCaption: [id: number, data: { segments: Caption['segments'] }]
  deleteCaption: [id: number]
}>()

const selectedVideoId = ref('')
const captionLang = ref('ko')
const editingId = ref<number | null>(null)

function handleVideoSelect() {
  // 부모에서 처리
}

function toggleEdit(id: number) {
  editingId.value = editingId.value === id ? null : id
}

function formatTimestamp(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  const ms = Math.floor((seconds % 1) * 100)
  return `${m}:${s.toString().padStart(2, '0')}.${ms.toString().padStart(2, '0')}`
}

function languageLabel(lang: string): string {
  const labels: Record<string, string> = {
    ko: '한국어',
    en: 'English',
    ja: '日本語',
    zh: '中文',
  }
  return labels[lang] ?? lang
}

function captionStatusClass(status: string): string {
  switch (status) {
    case 'COMPLETED':
      return 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'
    case 'GENERATING':
      return 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400'
    case 'FAILED':
      return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
    default:
      return 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-400'
  }
}
</script>
