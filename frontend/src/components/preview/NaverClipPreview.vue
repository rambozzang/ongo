<template>
  <div class="naver-preview">
    <div class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">
      {{ t('preview.howItLooks') }}
    </div>

    <div class="overflow-hidden rounded-xl bg-white shadow-lg dark:bg-gray-900">
      <!-- Naver Header Bar -->
      <div class="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-2.5 dark:border-gray-700 dark:bg-gray-900">
        <span class="text-lg font-extrabold tracking-tight text-[#03C75A]">NAVER</span>
        <div class="flex items-center gap-3">
          <svg class="h-5 w-5 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <svg class="h-5 w-5 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
        </div>
      </div>

      <!-- Clip Tab indicator -->
      <div class="flex items-center border-b border-gray-200 dark:border-gray-700">
        <div class="flex-1 py-2 text-center text-sm font-medium text-gray-400 dark:text-gray-500">추천</div>
        <div class="flex-1 border-b-2 border-[#03C75A] py-2 text-center text-sm font-bold text-[#03C75A]">클립</div>
        <div class="flex-1 py-2 text-center text-sm font-medium text-gray-400 dark:text-gray-500">구독</div>
      </div>

      <!-- Video Thumbnail Area -->
      <div class="relative aspect-video bg-black">
        <img
          v-if="thumbnail"
          :src="thumbnail"
          alt="썸네일"
          class="h-full w-full object-cover"
        />
        <div
          v-else
          class="flex h-full w-full items-center justify-center bg-gradient-to-br from-gray-800 to-gray-900"
        >
          <svg class="h-20 w-20 text-[#03C75A] opacity-80" fill="currentColor" viewBox="0 0 24 24">
            <path d="M10 16.5l6-4.5-6-4.5v9zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" />
          </svg>
        </div>
        <!-- Duration badge -->
        <div class="absolute bottom-2 right-2 rounded bg-black/80 px-1.5 py-0.5 text-xs font-semibold text-white">
          0:00
        </div>
        <!-- Play overlay -->
        <div class="absolute inset-0 flex items-center justify-center">
          <div class="flex h-14 w-14 items-center justify-center rounded-full bg-[#03C75A]/90 shadow-lg transition-transform hover:scale-105">
            <svg class="ml-0.5 h-7 w-7 text-white" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
          </div>
        </div>
      </div>

      <!-- Video Info -->
      <div class="p-4">
        <!-- Title with char count -->
        <div class="mb-1">
          <h3 class="line-clamp-2 text-[15px] font-bold leading-snug text-gray-900 dark:text-gray-100">
            {{ title || t('preview.enterTitle') }}
          </h3>
          <div class="mt-1 flex items-center justify-between">
            <span
              v-if="title && title.length > 100"
              class="text-[10px] text-red-500"
            >{{ t('preview.titleExceeded', { max: 100 }) }}</span>
            <span
              class="ml-auto text-[10px]"
              :class="(title?.length || 0) > 100 ? 'text-red-500' : 'text-gray-400 dark:text-gray-500'"
            >{{ title?.length || 0 }}{{ t('preview.charCount', { max: 100 }) }}</span>
          </div>
        </div>

        <!-- Channel Info Row -->
        <div class="mb-3 flex items-center justify-between">
          <div class="flex items-center gap-2.5">
            <div class="flex h-9 w-9 items-center justify-center rounded-full bg-[#03C75A]/15">
              <svg class="h-5 w-5 text-[#03C75A]" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
              </svg>
            </div>
            <div>
              <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                {{ channelName || t('preview.creator') }}
              </div>
              <div class="flex items-center gap-2 text-[11px] text-gray-500 dark:text-gray-400">
                <span>조회 0</span>
                <span>·</span>
                <span>방금 전</span>
              </div>
            </div>
          </div>
          <button class="rounded-full border border-[#03C75A] px-3.5 py-1 text-xs font-bold text-[#03C75A] transition-colors hover:bg-[#03C75A]/10">
            구독
          </button>
        </div>

        <!-- Description with expand/collapse -->
        <div class="mb-3">
          <div
            v-if="description"
            class="relative text-[13px] leading-relaxed text-gray-700 dark:text-gray-300"
          >
            <div v-if="!descExpanded" class="line-clamp-2">
              {{ description }}
            </div>
            <div v-else class="whitespace-pre-wrap">
              {{ description }}
            </div>
            <button
              v-if="description.length > 80"
              class="mt-0.5 text-xs font-medium text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              @click="descExpanded = !descExpanded"
            >
              {{ descExpanded ? '접기' : '더보기' }}
            </button>
          </div>
          <div class="mt-1 text-right">
            <span
              class="text-[10px]"
              :class="(description?.length || 0) > 1000 ? 'text-red-500' : 'text-gray-400 dark:text-gray-500'"
            >{{ description?.length || 0 }}{{ t('preview.charCount', { max: '1,000' }) }}</span>
          </div>
        </div>

        <!-- Tags as Naver-style green badges -->
        <div v-if="tags && tags.length > 0" class="mb-3 flex flex-wrap gap-1.5">
          <span
            v-for="(tag, idx) in tags"
            :key="idx"
            class="inline-flex items-center rounded-full bg-[#03C75A]/10 px-2.5 py-1 text-xs font-medium text-[#03C75A] dark:bg-[#03C75A]/20"
          >
            #{{ tag }}
          </span>
        </div>

        <!-- Action Bar (Naver style) -->
        <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
          <!-- Like -->
          <button class="flex items-center gap-1.5 text-gray-500 transition-colors hover:text-[#03C75A] dark:text-gray-400">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
            </svg>
            <span class="text-xs font-medium">좋아요</span>
          </button>

          <!-- Comment -->
          <button class="flex items-center gap-1.5 text-gray-500 transition-colors hover:text-[#03C75A] dark:text-gray-400">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
            <span class="text-xs font-medium">댓글</span>
          </button>

          <!-- Share -->
          <button class="flex items-center gap-1.5 text-gray-500 transition-colors hover:text-[#03C75A] dark:text-gray-400">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
            </svg>
            <span class="text-xs font-medium">공유</span>
          </button>

          <!-- Save -->
          <button class="flex items-center gap-1.5 text-gray-500 transition-colors hover:text-[#03C75A] dark:text-gray-400">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
            </svg>
            <span class="text-xs font-medium">저장</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n({ useScope: 'global' })

interface Props {
  title?: string
  description?: string
  thumbnail?: string
  channelName?: string
  tags?: string[]
}

withDefaults(defineProps<Props>(), {
  title: '',
  description: '',
  thumbnail: '',
  channelName: '',
  tags: () => [],
})

const descExpanded = ref(false)
</script>

<style scoped>
.naver-preview {
  @apply w-full;
}
</style>
