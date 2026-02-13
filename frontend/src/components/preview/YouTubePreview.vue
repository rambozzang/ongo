<template>
  <div class="youtube-preview">
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
          <svg class="h-20 w-20 text-red-600" fill="currentColor" viewBox="0 0 24 24">
            <path d="M10 16.5l6-4.5-6-4.5v9zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" />
          </svg>
        </div>
        <!-- Duration badge (mockup) -->
        <div class="absolute bottom-2 right-2 rounded bg-black bg-opacity-80 px-1.5 py-0.5 text-xs font-semibold text-white">
          0:00
        </div>
      </div>

      <!-- Video Info -->
      <div class="p-4">
        <h3 class="mb-2 line-clamp-2 text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ title || '영상 제목을 입력하세요' }}
        </h3>

        <!-- Channel Info -->
        <div class="mb-3 flex items-center gap-3">
          <div class="flex h-9 w-9 items-center justify-center rounded-full bg-red-100 dark:bg-red-900">
            <svg class="h-5 w-5 text-red-600 dark:text-red-400" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
            </svg>
          </div>
          <div>
            <div class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ channelName || '채널명' }}
            </div>
            <div class="text-xs text-gray-600 dark:text-gray-400">
              조회수 0회 · 방금 전
            </div>
          </div>
        </div>

        <!-- Description -->
        <div
          v-if="description"
          class="mb-3 line-clamp-3 text-sm text-gray-700 dark:text-gray-300"
        >
          {{ description }}
        </div>

        <!-- Tags -->
        <div v-if="tags && tags.length > 0" class="flex flex-wrap gap-2">
          <span
            v-for="(tag, idx) in tags.slice(0, 5)"
            :key="idx"
            class="rounded-full bg-blue-100 px-2.5 py-1 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300"
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
.youtube-preview {
  @apply w-full;
}
</style>
