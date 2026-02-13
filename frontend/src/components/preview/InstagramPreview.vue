<template>
  <div class="instagram-preview">
    <div class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">
      이렇게 보여요
    </div>

    <!-- Phone Frame -->
    <div class="mx-auto max-w-[360px]">
      <div class="rounded-[2rem] border-4 border-gray-800 bg-white shadow-2xl dark:border-gray-700 dark:bg-gray-900">
        <!-- Phone Screen -->
        <div class="relative aspect-[9/16] overflow-hidden rounded-[1.5rem] bg-white dark:bg-gray-900">
          <!-- Header -->
          <div class="flex items-center justify-between border-b border-gray-200 px-3 py-2 dark:border-gray-700">
            <div class="flex items-center gap-2">
              <div class="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-tr from-yellow-400 via-red-500 to-purple-600 p-0.5">
                <div class="flex h-full w-full items-center justify-center rounded-full bg-white dark:bg-gray-900">
                  <svg class="h-4 w-4 text-gray-600 dark:text-gray-400" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                  </svg>
                </div>
              </div>
              <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                {{ channelName || '사용자명' }}
              </span>
            </div>
            <svg class="h-5 w-5 text-gray-900 dark:text-gray-100" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z" />
            </svg>
          </div>

          <!-- Video Area -->
          <div class="relative aspect-[9/16] bg-black">
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
              <svg class="h-16 w-16 text-gray-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M10 16.5l6-4.5-6-4.5v9zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" />
              </svg>
            </div>

            <!-- Overlay Icons (left side) -->
            <div class="absolute bottom-4 left-3 flex items-center gap-2 text-white">
              <svg class="h-6 w-6" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" />
              </svg>
            </div>

            <!-- Right Side Action Buttons -->
            <div class="absolute bottom-4 right-3 flex flex-col items-center gap-4">
              <!-- Heart -->
              <div class="flex flex-col items-center">
                <svg class="h-7 w-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
                <span class="text-xs text-white">0</span>
              </div>

              <!-- Comment -->
              <div class="flex flex-col items-center">
                <svg class="h-7 w-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                </svg>
                <span class="text-xs text-white">0</span>
              </div>

              <!-- Share -->
              <svg class="h-7 w-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
              </svg>

              <!-- Save -->
              <svg class="h-7 w-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
              </svg>

              <!-- Profile circle -->
              <div class="mt-2 flex h-6 w-6 items-center justify-center rounded-full border-2 border-white bg-gray-700">
                <svg class="h-3 w-3 text-white" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                </svg>
              </div>
            </div>
          </div>

          <!-- Bottom Info -->
          <div class="border-t border-gray-200 px-3 py-2 dark:border-gray-700">
            <!-- Action buttons row -->
            <div class="mb-2 flex items-center gap-4">
              <svg class="h-7 w-7 text-gray-900 dark:text-gray-100" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
              </svg>
              <svg class="h-7 w-7 text-gray-900 dark:text-gray-100" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
              </svg>
              <svg class="h-7 w-7 text-gray-900 dark:text-gray-100" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
              </svg>
              <svg class="ml-auto h-7 w-7 text-gray-900 dark:text-gray-100" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
              </svg>
            </div>

            <div class="mb-1 text-sm font-semibold text-gray-900 dark:text-gray-100">
              좋아요 0개
            </div>

            <div class="mb-2 text-sm text-gray-900 dark:text-gray-100">
              <span class="font-semibold">{{ channelName || '사용자명' }}</span>
              <span class="ml-1">{{ description || title || '영상 설명을 입력하세요' }}</span>
            </div>

            <div v-if="tags && tags.length > 0" class="mb-2 flex flex-wrap gap-1">
              <span
                v-for="(tag, idx) in tags.slice(0, 3)"
                :key="idx"
                class="text-sm text-blue-900 dark:text-blue-400"
              >
                #{{ tag }}
              </span>
            </div>

            <div class="text-xs text-gray-500 dark:text-gray-400">
              방금 전
            </div>
          </div>
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
.instagram-preview {
  @apply w-full;
}
</style>
