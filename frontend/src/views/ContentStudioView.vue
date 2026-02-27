<template>
  <div>
  <!-- Mobile Layout -->
  <div v-if="!isTablet" class="space-y-4">
    <!-- Header -->
    <div>
      <h1 class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ $t('contentStudio.title') }}
      </h1>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('contentStudio.description') }}
      </p>
    </div>

    <PageGuide
      :title="$t('contentStudio.pageGuideTitle')"
      :items="($tm('contentStudio.pageGuideMobile') as string[])"
    />

    <!-- 크레딧 표시 -->
    <div
      class="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs"
      :class="isLow
        ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
    >
      <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
      <span class="text-gray-600 dark:text-gray-300">{{ $t('contentStudio.remaining') }}</span>
      <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
        {{ balance.toLocaleString() }}
      </span>
    </div>

    <!-- Tab Navigation (모바일) -->
    <div class="border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="flex-1 border-b-2 px-1 py-3 text-center text-xs font-medium transition-colors"
          :class="activeTab === tab.key
            ? 'border-primary-500 text-primary-600 dark:text-primary-400'
            : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'"
          @click="store.setActiveTab(tab.key)"
        >
          <component :is="tab.icon" class="mx-auto mb-1 h-5 w-5" />
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Tab Content -->
    <div>
      <ClipEditor
        v-show="activeTab === 'clips'"
        :videos="videos"
        :selected-video="selectedVideo"
        :clips="selectedVideoClips"
        :processing="processing"
        @select-video="handleSelectVideo"
        @change-video="handleChangeVideo"
        @create-clip="handleCreateClip"
        @delete-clip="handleDeleteClip"
      />
      <CaptionEditor
        v-show="activeTab === 'captions'"
        :videos="videos"
        :selected-video="selectedVideo"
        :captions="selectedVideoCaptions"
        :processing="processing"
        @select-video="handleSelectVideo"
        @change-video="handleChangeVideo"
        @generate-caption="handleGenerateCaption"
        @update-caption="handleUpdateCaption"
        @delete-caption="handleDeleteCaption"
      />
      <ThumbnailGenerator
        v-show="activeTab === 'thumbnails'"
        :videos="videos"
        :selected-video="selectedVideo"
        :thumbnails="selectedVideoThumbnails"
        :processing="processing"
        @select-video="handleSelectVideo"
        @change-video="handleChangeVideo"
        @generate-thumbnail="handleGenerateThumbnail"
        @delete-thumbnail="handleDeleteThumbnail"
      />
    </div>

    <!-- 히스토리 -->
    <HistorySection
      :history="history"
      :loading="loadingHistory"
    />
  </div>

  <!-- Desktop/Tablet Layout -->
  <div v-else>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('contentStudio.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('contentStudio.description') }}
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
          <span class="text-gray-600 dark:text-gray-300">{{ $t('contentStudio.remaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <PageGuide
      :title="$t('contentStudio.pageGuideTitle')"
      :items="($tm('contentStudio.pageGuideDesktop') as string[])"
    />

    <!-- 에러 메시지 -->
    <div
      v-if="error"
      class="mb-4 rounded-lg border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 px-4 py-3 text-sm text-red-700 dark:text-red-400"
    >
      {{ error }}
      <button class="ml-2 underline" @click="store.clearError()">{{ $t('contentStudio.dismiss') }}</button>
    </div>

    <!-- Tab Navigation (데스크탑) -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="inline-flex items-center gap-2 border-b-2 px-1 py-3 text-sm font-medium transition-colors"
          :class="activeTab === tab.key
            ? 'border-primary-500 text-primary-600 dark:text-primary-400'
            : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'"
          @click="store.setActiveTab(tab.key)"
        >
          <component :is="tab.icon" class="h-5 w-5" />
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Tab Content -->
    <div class="mt-6">
      <ClipEditor
        v-show="activeTab === 'clips'"
        :videos="videos"
        :selected-video="selectedVideo"
        :clips="selectedVideoClips"
        :processing="processing"
        @select-video="handleSelectVideo"
        @change-video="handleChangeVideo"
        @create-clip="handleCreateClip"
        @delete-clip="handleDeleteClip"
      />
      <CaptionEditor
        v-show="activeTab === 'captions'"
        :videos="videos"
        :selected-video="selectedVideo"
        :captions="selectedVideoCaptions"
        :processing="processing"
        @select-video="handleSelectVideo"
        @change-video="handleChangeVideo"
        @generate-caption="handleGenerateCaption"
        @update-caption="handleUpdateCaption"
        @delete-caption="handleDeleteCaption"
      />
      <ThumbnailGenerator
        v-show="activeTab === 'thumbnails'"
        :videos="videos"
        :selected-video="selectedVideo"
        :thumbnails="selectedVideoThumbnails"
        :processing="processing"
        @select-video="handleSelectVideo"
        @change-video="handleChangeVideo"
        @generate-thumbnail="handleGenerateThumbnail"
        @delete-thumbnail="handleDeleteThumbnail"
      />
    </div>

    <!-- 히스토리 -->
    <HistorySection
      :history="history"
      :loading="loadingHistory"
      class="mt-8"
    />
  </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  SparklesIcon,
  ScissorsIcon,
  LanguageIcon,
  PhotoIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import ClipEditor from '@/components/contentstudio/ClipEditor.vue'
import CaptionEditor from '@/components/contentstudio/CaptionEditor.vue'
import ThumbnailGenerator from '@/components/contentstudio/ThumbnailGenerator.vue'
import HistorySection from '@/components/contentstudio/HistorySection.vue'
import { useContentStudioStore } from '@/stores/contentStudio'
import { useCredit } from '@/composables/useCredit'
import type { ThumbnailStyle, CaptionSegment } from '@/types/contentStudio'

const { t } = useI18n({ useScope: 'global' })
const store = useContentStudioStore()
const { balance, isLow, fetchBalance } = useCredit()

const isTablet = useMediaQuery('(min-width: 768px)')

const {
  videos,
  selectedVideo,
  selectedVideoClips,
  selectedVideoCaptions,
  selectedVideoThumbnails,
  history,
  activeTab,
  processing,
  loadingHistory,
  error,
} = storeToRefs(store)

const tabs = computed(() => [
  { key: 'clips' as const, label: t('contentStudio.tabs.clips'), icon: ScissorsIcon },
  { key: 'captions' as const, label: t('contentStudio.tabs.captions'), icon: LanguageIcon },
  { key: 'thumbnails' as const, label: t('contentStudio.tabs.thumbnails'), icon: PhotoIcon },
])

// 영상 선택
function handleSelectVideo(id: number) {
  const video = videos.value.find(v => v.id === id)
  if (video) store.selectVideo(video)
}

function handleChangeVideo() {
  store.selectVideo(null)
}

// 클립 생성
async function handleCreateClip(data: { title: string; startTime: number; endTime: number; aspectRatio: string }) {
  if (!selectedVideo.value) return
  await store.createClip({
    videoId: selectedVideo.value.id,
    title: data.title,
    startTime: data.startTime,
    endTime: data.endTime,
    aspectRatio: data.aspectRatio,
  })
  await fetchBalance()
}

async function handleDeleteClip(id: number) {
  await store.deleteClip(id)
}

// 자막 생성
async function handleGenerateCaption(data: { videoId: number; language: string }) {
  await store.generateCaption({ videoId: data.videoId, language: data.language })
  await fetchBalance()
}

async function handleUpdateCaption(id: number, data: { segments: CaptionSegment[] }) {
  await store.updateCaption(id, data)
}

async function handleDeleteCaption(id: number) {
  await store.deleteCaption(id)
}

// 썸네일 생성
async function handleGenerateThumbnail(data: { videoId: number; style: ThumbnailStyle; overlayText?: string; count: number }) {
  await store.generateThumbnail({
    videoId: data.videoId,
    style: data.style,
    overlayText: data.overlayText,
    count: data.count,
  })
  await fetchBalance()
}

async function handleDeleteThumbnail(id: number) {
  await store.deleteThumbnail(id)
}

onMounted(() => {
  store.fetchVideos()
  store.fetchHistory()
  fetchBalance()
})
</script>
