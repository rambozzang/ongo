<template>
  <div class="grid gap-6 lg:grid-cols-5">
    <!-- Left: Video preview + Thumbnail -->
    <div class="lg:col-span-2">
      <div class="overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700 bg-black">
        <video
          v-if="videoPreviewUrl"
          ref="videoEl"
          class="aspect-video w-full object-contain"
          :src="videoPreviewUrl"
          controls
        />
        <div v-else class="flex aspect-video items-center justify-center">
          <FilmIcon class="h-12 w-12 text-gray-600" />
        </div>
      </div>

      <!-- Thumbnail selector -->
      <div class="mt-4">
        <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">썸네일 선택</label>
        <div class="grid grid-cols-3 gap-2">
          <button
            v-for="(thumb, i) in thumbnailCandidates"
            :key="i"
            class="overflow-hidden rounded-lg border-2 transition-colors"
            :class="selectedThumbnail === i ? 'border-primary-500' : 'border-transparent hover:border-gray-300 dark:hover:border-gray-600'"
            @click="selectedThumbnail = i; emit('update:thumbnailUrl', thumb)"
          >
            <img :src="thumb" class="aspect-video w-full object-cover" :alt="`썸네일 ${i + 1}`" />
          </button>
          <div
            v-if="thumbnailCandidates.length === 0"
            class="col-span-3 flex aspect-video items-center justify-center rounded-lg border border-dashed border-gray-300 dark:border-gray-600 text-sm text-gray-400 dark:text-gray-500"
          >
            업로드 완료 후 생성됩니다
          </div>
        </div>
      </div>
    </div>

    <!-- Right: Form fields -->
    <div class="space-y-5 lg:col-span-3">
      <!-- Header Actions -->
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">메타데이터</h3>
        <button
          class="inline-flex items-center gap-2 rounded-lg border border-primary-300 dark:border-primary-700 bg-primary-50 dark:bg-primary-900/20 px-4 py-2 text-sm font-medium text-primary-700 dark:text-primary-400 transition-colors hover:bg-primary-100 dark:hover:bg-primary-900/40"
          @click="showTemplateManager = true"
        >
          <BookmarkIcon class="h-4 w-4" />
          템플릿
        </button>
      </div>

      <!-- Title -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          제목 <span class="text-red-500">*</span>
        </label>
        <input
          :value="modelValue.title"
          type="text"
          maxlength="100"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2.5 text-sm transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
          placeholder="영상 제목을 입력하세요"
          @input="updateField('title', ($event.target as HTMLInputElement).value)"
        />
        <p class="mt-1 text-right text-xs text-gray-400 dark:text-gray-500">
          {{ modelValue.title.length }}/100
        </p>
      </div>

      <!-- Description -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">설명</label>
        <textarea
          :value="modelValue.description"
          rows="4"
          class="w-full resize-none rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2.5 text-sm transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
          placeholder="영상 설명을 입력하세요"
          @input="updateField('description', ($event.target as HTMLTextAreaElement).value)"
        />
      </div>

      <!-- Tags -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          태그 <span class="text-xs text-gray-400 dark:text-gray-500">(최대 30개, Enter로 추가)</span>
        </label>
        <div
          class="flex min-h-[42px] flex-wrap gap-1.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 focus-within:border-primary-500 focus-within:ring-1 focus-within:ring-primary-500"
        >
          <span
            v-for="(tag, i) in modelValue.tags"
            :key="i"
            class="inline-flex items-center gap-1 rounded-full bg-primary-100 dark:bg-primary-900/30 px-2.5 py-0.5 text-xs font-medium text-primary-700 dark:text-primary-400"
          >
            {{ tag }}
            <button
              type="button"
              class="text-primary-500 hover:text-primary-700"
              @click="removeTag(i)"
            >
              <XMarkIcon class="h-3.5 w-3.5" />
            </button>
          </span>
          <input
            v-if="modelValue.tags.length < 30"
            v-model="tagInput"
            type="text"
            class="min-w-[120px] flex-1 border-none bg-transparent text-gray-900 dark:text-gray-100 p-0 text-sm focus:outline-none focus:ring-0"
            placeholder="태그 입력 후 Enter"
            @keydown.enter.prevent="addTag"
            @keydown.,.prevent="addTag"
          />
        </div>
        <p class="mt-1 text-right text-xs text-gray-400 dark:text-gray-500">
          {{ modelValue.tags.length }}/30
        </p>
      </div>

      <!-- Category -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">카테고리</label>
        <select
          :value="modelValue.category"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2.5 text-sm transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
          @change="updateField('category', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">카테고리 선택</option>
          <option v-for="cat in CATEGORIES" :key="cat" :value="cat">{{ cat }}</option>
        </select>
      </div>

      <!-- Visibility -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">공개 범위</label>
        <div class="flex gap-4">
          <label
            v-for="opt in VISIBILITY_OPTIONS"
            :key="opt.value"
            class="flex cursor-pointer items-center gap-2"
          >
            <input
              type="radio"
              name="visibility"
              :value="opt.value"
              :checked="modelValue.visibility === opt.value"
              class="text-primary-600 focus:ring-primary-500"
              @change="updateField('visibility', opt.value)"
            />
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ opt.label }}</span>
          </label>
        </div>
      </div>

      <!-- AI Generate -->
      <div class="rounded-xl border border-primary-200 dark:border-primary-800 bg-primary-50 dark:bg-primary-900/20 p-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <p class="text-sm font-medium text-primary-800 dark:text-primary-400">AI 자동 생성</p>
            <p class="mt-0.5 text-xs text-primary-600">
              영상 분석 후 제목, 설명, 태그를 자동 생성합니다
            </p>
          </div>
          <button
            :disabled="aiGenerating || !hasEnoughCredits"
            class="inline-flex shrink-0 items-center gap-1.5 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            @click="emit('generateAi')"
          >
            <template v-if="aiGenerating">
              <LoadingSpinner size="sm" />
              생성 중...
            </template>
            <template v-else>
              <SparklesIcon class="h-4 w-4" />
              AI 자동 생성 (5 크레딧)
            </template>
          </button>
        </div>

        <!-- STT option -->
        <div class="mt-3 border-t border-primary-200 dark:border-primary-800 pt-3">
          <label class="flex cursor-pointer items-center gap-2">
            <input
              type="checkbox"
              :checked="useStt"
              class="rounded text-primary-600 focus:ring-primary-500"
              @change="emit('update:useStt', ($event.target as HTMLInputElement).checked)"
            />
            <span class="text-sm text-primary-700 dark:text-primary-400">
              음성 인식(STT) 활용 (+10 크레딧)
            </span>
            <span class="text-xs text-primary-500 dark:text-primary-400">더 정확한 메타데이터 생성</span>
          </label>
        </div>

        <p v-if="!hasEnoughCredits" class="mt-2 text-xs text-red-600">
          크레딧이 부족합니다. 크레딧을 충전해주세요.
        </p>
      </div>
    </div>
  </div>

  <!-- Template Manager Modal -->
  <TemplateManager
    v-if="showTemplateManager"
    :current-metadata="modelValue"
    @apply="handleApplyTemplate"
    @close="showTemplateManager = false"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { XMarkIcon, BookmarkIcon } from '@heroicons/vue/24/outline'
import { SparklesIcon, FilmIcon } from '@heroicons/vue/24/solid'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import TemplateManager from './TemplateManager.vue'
import type { Visibility } from '@/types/video'

export interface MetadataFormData {
  title: string
  description: string
  tags: string[]
  category: string
  visibility: Visibility
  thumbnailUrl: string
}

const props = defineProps<{
  modelValue: MetadataFormData
  videoPreviewUrl: string | null
  thumbnailCandidates: string[]
  aiGenerating: boolean
  useStt: boolean
  hasEnoughCredits: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: MetadataFormData]
  'update:thumbnailUrl': [url: string]
  'update:useStt': [value: boolean]
  generateAi: []
}>()

const tagInput = ref('')
const selectedThumbnail = ref(0)
const showTemplateManager = ref(false)

const CATEGORIES = [
  '엔터테인먼트',
  '음악',
  '게임',
  '스포츠',
  '뉴스/정치',
  '교육',
  '과학기술',
  '여행/이벤트',
  '인물/블로그',
  '코미디',
  '영화/애니메이션',
  '자동차',
  '동물',
  '패션/뷰티',
  '푸드',
  '일상/Vlog',
]

const VISIBILITY_OPTIONS: { value: Visibility; label: string }[] = [
  { value: 'PUBLIC', label: '공개' },
  { value: 'UNLISTED', label: '일부 공개' },
  { value: 'PRIVATE', label: '비공개' },
]

function updateField<K extends keyof MetadataFormData>(key: K, value: MetadataFormData[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !props.modelValue.tags.includes(tag) && props.modelValue.tags.length < 30) {
    emit('update:modelValue', {
      ...props.modelValue,
      tags: [...props.modelValue.tags, tag],
    })
  }
  tagInput.value = ''
}

function removeTag(index: number) {
  const tags = [...props.modelValue.tags]
  tags.splice(index, 1)
  emit('update:modelValue', { ...props.modelValue, tags })
}

function handleApplyTemplate(metadata: MetadataFormData) {
  emit('update:modelValue', {
    ...props.modelValue,
    title: metadata.title,
    description: metadata.description,
    tags: metadata.tags,
    category: metadata.category,
    visibility: metadata.visibility,
  })
}
</script>
