<template>
  <div v-if="!isDismissed && suggestions.length > 0" class="card">
    <div class="mb-4 flex items-start justify-between">
      <div>
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">재활용 추천</h3>
        <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
          다시 게시하면 좋을 콘텐츠를 찾았어요
        </p>
      </div>
      <button
        class="rounded-lg p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
        @click="dismiss"
        title="닫기"
      >
        <XMarkIcon class="h-5 w-5" />
      </button>
    </div>

    <div class="space-y-3">
      <div
        v-for="suggestion in suggestions.slice(0, 3)"
        :key="suggestion.video.id"
        class="group flex gap-3 rounded-lg border border-gray-200 dark:border-gray-700 p-3 transition-all hover:border-primary-300 dark:hover:border-primary-700 hover:bg-primary-50/50 dark:hover:bg-primary-900/10"
      >
        <!-- Thumbnail -->
        <div
          class="h-16 w-28 flex-shrink-0 cursor-pointer overflow-hidden rounded bg-gray-100 dark:bg-gray-800"
          @click="$router.push(`/videos/${suggestion.video.id}`)"
        >
          <img
            v-if="suggestion.video.thumbnailUrl"
            :src="suggestion.video.thumbnailUrl"
            :alt="suggestion.video.title"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <VideoCameraIcon class="h-6 w-6 text-gray-300 dark:text-gray-600" />
          </div>
        </div>

        <!-- Info -->
        <div class="min-w-0 flex-1">
          <p
            class="line-clamp-1 cursor-pointer text-sm font-medium text-gray-900 dark:text-gray-100"
            @click="$router.push(`/videos/${suggestion.video.id}`)"
          >
            {{ suggestion.video.title }}
          </p>
          <div class="mt-1 flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
            <LightBulbIcon class="h-3.5 w-3.5 text-amber-500" />
            <span>{{ suggestion.reason }}</span>
          </div>
          <div class="mt-1.5 flex flex-wrap items-center gap-1">
            <PlatformBadge
              v-for="upload in suggestion.video.uploads"
              :key="upload.platform"
              :platform="upload.platform"
              class="scale-75 origin-left"
            />
            <span v-if="suggestion.missingPlatforms.length > 0" class="text-[11px] text-gray-400 dark:text-gray-500">
              → {{ formatMissingPlatforms(suggestion.missingPlatforms) }}
            </span>
          </div>
        </div>

        <!-- Action -->
        <button
          class="btn-primary self-center text-xs opacity-0 transition-opacity group-hover:opacity-100"
          @click="handleRecycle(suggestion.video)"
        >
          <ArrowPathRoundedSquareIcon class="mr-1 h-3.5 w-3.5" />
          재활용
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  XMarkIcon,
  VideoCameraIcon,
  LightBulbIcon,
  ArrowPathRoundedSquareIcon,
} from '@heroicons/vue/24/outline'
import type { Video } from '@/types/video'
import type { Platform } from '@/types/channel'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import { PLATFORM_CONFIG } from '@/types/channel'
import { recyclingApi } from '@/api/recycling'
import { videoApi } from '@/api/video'

interface RecycleSuggestion {
  video: Video
  reason: string
  missingPlatforms: Platform[]
}

const emit = defineEmits<{
  recycle: [video: Video]
}>()

const isDismissed = ref(false)
const suggestions = ref<RecycleSuggestion[]>([])
const loadingSuggestions = ref(false)

function getMissingPlatforms(video: Video): Platform[] {
  const allPlatforms: Platform[] = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP']
  const uploadedPlatforms = video.uploads.map((u) => u.platform)
  return allPlatforms.filter((p) => !uploadedPlatforms.includes(p))
}

function formatMissingPlatforms(platforms: Platform[]): string {
  return platforms.map((p) => PLATFORM_CONFIG[p].label).join(', ')
}

function handleRecycle(video: Video) {
  emit('recycle', video)
}

function dismiss() {
  isDismissed.value = true
  localStorage.setItem('recycleSuggestionsDismissed', Date.now().toString())
}

onMounted(async () => {
  // Check if dismissed recently (within 7 days)
  const dismissedAt = localStorage.getItem('recycleSuggestionsDismissed')
  if (dismissedAt) {
    const daysSinceDismissed = Math.floor(
      (Date.now() - parseInt(dismissedAt)) / (1000 * 60 * 60 * 24)
    )
    if (daysSinceDismissed < 7) {
      isDismissed.value = true
      return
    }
  }

  loadingSuggestions.value = true
  try {
    let apiSuggestions = await recyclingApi.getSuggestions('PENDING')
    if (apiSuggestions.length === 0) {
      apiSuggestions = await recyclingApi.generateSuggestions()
    }

    // Fetch video details for each suggestion
    // Collect video IDs to fetch detailed info
    const videosResponse = await videoApi.list({ page: 0, size: 50 })
    const videosMap = new Map(videosResponse.content.map(v => [v.id, v]))

    suggestions.value = apiSuggestions
      .filter(s => videosMap.has(s.videoId))
      .map(s => {
        const video = videosMap.get(s.videoId)!
        return {
          video,
          reason: s.reason || '재활용 추천',
          missingPlatforms: getMissingPlatforms(video),
        }
      })
  } catch {
    // silently ignore — suggestions will be empty
  } finally {
    loadingSuggestions.value = false
  }
})
</script>
