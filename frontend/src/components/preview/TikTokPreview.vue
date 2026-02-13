<template>
  <div class="tiktok-preview">
    <div class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">
      이렇게 보여요
    </div>

    <!-- Phone Frame -->
    <div class="mx-auto max-w-[360px]">
      <div class="rounded-[2rem] border-4 border-gray-800 bg-black shadow-2xl dark:border-gray-700">
        <!-- Phone Screen -->
        <div class="relative aspect-[9/16] overflow-hidden rounded-[1.5rem] bg-gradient-to-br from-gray-900 to-black">
          <!-- Video Area with Thumbnail -->
          <div class="absolute inset-0">
            <img
              v-if="thumbnail"
              :src="thumbnail"
              alt="썸네일"
              class="h-full w-full object-cover"
            />
            <div
              v-else
              class="flex h-full w-full items-center justify-center"
            >
              <svg class="h-16 w-16 text-gray-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M10 16.5l6-4.5-6-4.5v9zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" />
              </svg>
            </div>
          </div>

          <!-- Overlay Gradient -->
          <div class="absolute inset-0 bg-gradient-to-t from-black/70 via-transparent to-black/20" />

          <!-- Top Bar -->
          <div class="absolute left-0 right-0 top-0 flex items-center justify-between px-4 py-3">
            <div class="text-sm font-medium text-white">팔로잉</div>
            <div class="text-sm font-medium text-white opacity-60">추천</div>
            <svg class="h-5 w-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>

          <!-- Right Side Action Buttons -->
          <div class="absolute bottom-20 right-3 flex flex-col items-center gap-5">
            <!-- Profile Picture -->
            <div class="relative">
              <div class="flex h-12 w-12 items-center justify-center rounded-full border-2 border-white bg-gray-700">
                <svg class="h-6 w-6 text-white" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                </svg>
              </div>
              <div class="absolute -bottom-2 left-1/2 flex h-5 w-5 -translate-x-1/2 items-center justify-center rounded-full bg-[#FE2C55]">
                <svg class="h-3 w-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M12 4v16m8-8H4" />
                </svg>
              </div>
            </div>

            <!-- Heart -->
            <div class="flex flex-col items-center gap-1">
              <svg class="h-8 w-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
              </svg>
              <span class="text-xs text-white">0</span>
            </div>

            <!-- Comment -->
            <div class="flex flex-col items-center gap-1">
              <svg class="h-8 w-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
              </svg>
              <span class="text-xs text-white">0</span>
            </div>

            <!-- Share -->
            <div class="flex flex-col items-center gap-1">
              <svg class="h-8 w-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
              </svg>
              <span class="text-xs text-white">0</span>
            </div>

            <!-- Music disc -->
            <div class="mt-2 flex h-10 w-10 items-center justify-center rounded-full bg-gray-800 shadow-lg">
              <svg class="h-5 w-5 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z" />
              </svg>
            </div>
          </div>

          <!-- Bottom Info -->
          <div class="absolute bottom-0 left-0 right-0 px-4 pb-4">
            <div class="mb-2 font-semibold text-white">
              @{{ channelName || '사용자명' }}
            </div>
            <div class="mb-2 line-clamp-2 text-sm text-white">
              {{ description || title || '영상 설명을 입력하세요' }}
            </div>
            <div v-if="tags && tags.length > 0" class="flex flex-wrap gap-1">
              <span
                v-for="(tag, idx) in tags.slice(0, 3)"
                :key="idx"
                class="text-sm font-semibold text-white"
              >
                #{{ tag }}
              </span>
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
.tiktok-preview {
  @apply w-full;
}
</style>
