<template>
  <div class="tiktok-preview">
    <div class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">
      이렇게 보여요
    </div>

    <!-- Phone Frame -->
    <div class="mx-auto max-w-[320px]">
      <div class="rounded-[2.5rem] border-[3px] border-gray-800 bg-black p-1 shadow-2xl dark:border-gray-600">
        <!-- Phone Screen -->
        <div class="relative aspect-[9/16] overflow-hidden rounded-[2rem] bg-black">
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
              class="flex h-full w-full items-center justify-center bg-gradient-to-b from-gray-900 via-gray-800 to-black"
            >
              <svg class="h-14 w-14 text-gray-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M10 16.5l6-4.5-6-4.5v9zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/>
              </svg>
            </div>
          </div>

          <!-- Overlay Gradient -->
          <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/30" />

          <!-- Top Bar: 팔로잉 | 추천 -->
          <div class="absolute left-0 right-0 top-0 flex items-center justify-center gap-4 px-4 pb-2 pt-4">
            <span class="text-sm text-white/60">팔로잉</span>
            <span class="border-b-2 border-white pb-0.5 text-sm font-bold text-white">추천</span>
          </div>

          <!-- Right Side Action Buttons -->
          <div class="absolute bottom-[100px] right-2 flex flex-col items-center gap-4">
            <!-- Profile Picture + Follow -->
            <div class="relative mb-2">
              <div class="flex h-11 w-11 items-center justify-center rounded-full border-2 border-white bg-gray-700">
                <svg class="h-5 w-5 text-white" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                </svg>
              </div>
              <div class="absolute -bottom-1.5 left-1/2 flex h-4.5 w-4.5 -translate-x-1/2 items-center justify-center rounded-full bg-[#FE2C55]">
                <svg class="h-2.5 w-2.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M12 4v16m8-8H4"/>
                </svg>
              </div>
            </div>

            <!-- Heart -->
            <div class="flex flex-col items-center gap-0.5">
              <svg class="h-7 w-7 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
              </svg>
              <span class="text-[10px] font-semibold text-white">328</span>
            </div>

            <!-- Comment -->
            <div class="flex flex-col items-center gap-0.5">
              <svg class="h-7 w-7 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>
              </svg>
              <span class="text-[10px] font-semibold text-white">12</span>
            </div>

            <!-- Bookmark -->
            <div class="flex flex-col items-center gap-0.5">
              <svg class="h-7 w-7 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/>
              </svg>
              <span class="text-[10px] font-semibold text-white">5</span>
            </div>

            <!-- Share -->
            <div class="flex flex-col items-center gap-0.5">
              <svg class="h-7 w-7 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z"/>
              </svg>
              <span class="text-[10px] font-semibold text-white">24</span>
            </div>

            <!-- Spinning Music Disc -->
            <div class="music-disc mt-1 flex h-10 w-10 items-center justify-center rounded-full border-[3px] border-gray-600 bg-gradient-to-br from-gray-900 via-gray-700 to-gray-900">
              <div class="h-3 w-3 rounded-full bg-gray-400"></div>
            </div>
          </div>

          <!-- Bottom Info -->
          <div class="absolute bottom-0 left-0 right-14 px-3 pb-3">
            <!-- Character Count Indicator -->
            <div class="mb-1.5">
              <span :class="['text-[10px] font-medium', captionCountColor]">
                {{ captionLength }}/150자
              </span>
              <span
                v-if="captionLength > 150"
                class="ml-1.5 rounded bg-red-500/30 px-1.5 py-0.5 text-[9px] font-semibold text-red-300"
              >
                150자 초과
              </span>
            </div>

            <!-- Channel Name -->
            <div class="mb-1 text-sm font-bold text-white">
              @{{ channelName || '사용자명' }}
            </div>

            <!-- Caption with inline hashtags (TikTok style) -->
            <div class="text-[13px] leading-5 text-white">
              <span class="line-clamp-2">
                <span>{{ title || '캡션을 입력하세요' }}</span>
                <template v-if="tags && tags.length > 0">
                  {{ ' ' }}
                  <span
                    v-for="(tag, idx) in tags"
                    :key="idx"
                    class="font-semibold text-white/90"
                  >
                    #{{ tag }}{{ idx < tags.length - 1 ? ' ' : '' }}
                  </span>
                </template>
              </span>
            </div>

            <!-- Music Row -->
            <div class="mt-2 flex items-center gap-1.5">
              <svg class="h-3.5 w-3.5 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/>
              </svg>
              <div class="overflow-hidden">
                <span class="marquee-text text-xs text-white">원본 사운드 - {{ channelName || '사용자명' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  title?: string
  description?: string
  thumbnail?: string
  channelName?: string
  tags?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  description: '',
  thumbnail: '',
  channelName: '',
  tags: () => [],
})

const captionLength = computed(() => {
  const captionText = props.title || ''
  const hashtagText = props.tags && props.tags.length > 0
    ? ' ' + props.tags.map(t => `#${t}`).join(' ')
    : ''
  return (captionText + hashtagText).length
})

const captionCountColor = computed(() => {
  if (captionLength.value > 150) return 'text-red-400 font-semibold'
  if (captionLength.value >= 120) return 'text-yellow-400'
  return 'text-green-400'
})
</script>

<style scoped>
.tiktok-preview {
  @apply w-full;
}

/* Spinning music disc animation */
.music-disc {
  animation: spin-disc 3s linear infinite;
}

@keyframes spin-disc {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* Scrolling marquee text for music */
.marquee-text {
  display: inline-block;
  white-space: nowrap;
  animation: marquee 8s linear infinite;
}

@keyframes marquee {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-100%);
  }
}
</style>
