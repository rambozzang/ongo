<template>
  <div class="naver-preview">
    <div class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">
      이렇게 보여요
    </div>

    <div class="overflow-hidden rounded-lg bg-white shadow-lg dark:bg-gray-900">
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
          <svg class="h-20 w-20 text-[#03C75A]" fill="currentColor" viewBox="0 0 24 24">
            <path d="M10 16.5l6-4.5-6-4.5v9zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" />
          </svg>
        </div>
        <!-- Duration badge (mockup) -->
        <div class="absolute bottom-2 right-2 rounded bg-black bg-opacity-80 px-1.5 py-0.5 text-xs font-semibold text-white">
          0:00
        </div>
        <!-- Play overlay -->
        <div class="absolute inset-0 flex items-center justify-center bg-black bg-opacity-20">
          <div class="flex h-14 w-14 items-center justify-center rounded-full bg-[#03C75A] shadow-lg">
            <svg class="h-7 w-7 text-white" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
          </div>
        </div>
      </div>

      <!-- Video Info -->
      <div class="p-4">
        <h3 class="mb-2 line-clamp-2 text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ title || '영상 제목을 입력하세요' }}
        </h3>

        <!-- Channel Info and Stats -->
        <div class="mb-3 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <div class="flex h-8 w-8 items-center justify-center rounded-full bg-[#03C75A] bg-opacity-20">
              <svg class="h-4 w-4 text-[#03C75A]" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
              </svg>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-900 dark:text-gray-100">
                {{ channelName || '크리에이터' }}
              </div>
            </div>
          </div>
          <div class="flex items-center gap-3 text-xs text-gray-600 dark:text-gray-400">
            <span>조회 0</span>
            <span>•</span>
            <span>방금 전</span>
          </div>
        </div>

        <!-- Description -->
        <div
          v-if="description"
          class="mb-3 line-clamp-2 text-sm text-gray-700 dark:text-gray-300"
        >
          {{ description }}
        </div>

        <!-- Tags -->
        <div v-if="tags && tags.length > 0" class="flex flex-wrap gap-2">
          <span
            v-for="(tag, idx) in tags.slice(0, 5)"
            :key="idx"
            class="rounded-full bg-[#03C75A] bg-opacity-10 px-2.5 py-1 text-xs font-medium text-[#03C75A] dark:bg-[#03C75A] dark:bg-opacity-20"
          >
            #{{ tag }}
          </span>
          <span
            v-if="tags.length > 5"
            class="rounded-full bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-400"
          >
            +{{ tags.length - 5 }}
          </span>
        </div>

        <!-- Action Buttons -->
        <div class="mt-4 flex items-center justify-between border-t border-gray-200 pt-3 dark:border-gray-700">
          <button class="flex items-center gap-1 text-sm text-gray-600 dark:text-gray-400">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
            </svg>
            <span>0</span>
          </button>
          <button class="flex items-center gap-1 text-sm text-gray-600 dark:text-gray-400">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
            <span>0</span>
          </button>
          <button class="text-sm text-gray-600 dark:text-gray-400">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
            </svg>
          </button>
          <button class="text-sm text-gray-600 dark:text-gray-400">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
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
</script>

<style scoped>
.naver-preview {
  @apply w-full;
}
</style>
