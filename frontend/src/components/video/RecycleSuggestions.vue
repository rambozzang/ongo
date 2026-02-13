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
import { ref, computed, onMounted } from 'vue'
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

interface RecycleSuggestion {
  video: Video
  reason: string
  missingPlatforms: Platform[]
}

const emit = defineEmits<{
  recycle: [video: Video]
}>()

const isDismissed = ref(false)
const mockVideos = ref<Video[]>([])

// Mock data for demo
const suggestions = computed<RecycleSuggestion[]>(() => {
  if (mockVideos.value.length === 0) return []

  const result: RecycleSuggestion[] = []

  // Strategy 1: Old videos with high views
  const oldHighPerformers = mockVideos.value
    .filter((v) => {
      const daysSincePublish = Math.floor(
        (Date.now() - new Date(v.createdAt).getTime()) / (1000 * 60 * 60 * 24)
      )
      return daysSincePublish > 30 && (v as any).totalViews > 10000
    })
    .slice(0, 2)

  oldHighPerformers.forEach((video) => {
    const daysSincePublish = Math.floor(
      (Date.now() - new Date(video.createdAt).getTime()) / (1000 * 60 * 60 * 24)
    )
    result.push({
      video,
      reason: `${daysSincePublish}일 전 게시, 높은 조회수 (${formatViews((video as any).totalViews || 0)})`,
      missingPlatforms: getMissingPlatforms(video),
    })
  })

  // Strategy 2: Videos posted to limited platforms
  const limitedPlatformVideos = mockVideos.value
    .filter((v) => v.uploads.length > 0 && v.uploads.length < 3)
    .slice(0, 1)

  limitedPlatformVideos.forEach((video) => {
    const missing = getMissingPlatforms(video)
    result.push({
      video,
      reason: `${video.uploads.length}개 플랫폼만 게시됨`,
      missingPlatforms: missing,
    })
  })

  return result
})

function getMissingPlatforms(video: Video): Platform[] {
  const allPlatforms: Platform[] = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP']
  const uploadedPlatforms = video.uploads.map((u) => u.platform)
  return allPlatforms.filter((p) => !uploadedPlatforms.includes(p))
}

function formatMissingPlatforms(platforms: Platform[]): string {
  return platforms.map((p) => PLATFORM_CONFIG[p].label).join(', ')
}

function formatViews(views: number): string {
  if (views >= 1_000_000) return `${(views / 1_000_000).toFixed(1)}M`
  if (views >= 1_000) return `${(views / 1_000).toFixed(1)}K`
  return views.toString()
}

function handleRecycle(video: Video) {
  emit('recycle', video)
}

function dismiss() {
  isDismissed.value = true
  localStorage.setItem('recycleSuggestionsDismissed', Date.now().toString())
}

onMounted(() => {
  // Check if dismissed recently (within 7 days)
  const dismissedAt = localStorage.getItem('recycleSuggestionsDismissed')
  if (dismissedAt) {
    const daysSinceDismissed = Math.floor(
      (Date.now() - parseInt(dismissedAt)) / (1000 * 60 * 60 * 24)
    )
    if (daysSinceDismissed < 7) {
      isDismissed.value = true
    }
  }

  // Mock data - in real app, this would come from video store
  mockVideos.value = [
    {
      id: 101,
      userId: 1,
      title: '서울 여행 브이로그 - 강남 맛집 투어',
      description: '강남역 주변 맛집을 소개합니다',
      tags: ['브이로그', '여행', '맛집'],
      category: '브이로그',
      fileUrl: 'https://example.com/video1.mp4',
      thumbnailUrl: null,
      thumbnailCandidates: [],
      duration: 600,
      fileSize: 50000000,
      resolution: '1080p',
      status: 'PUBLISHED',
      visibility: 'PUBLIC',
      createdAt: new Date(Date.now() - 40 * 24 * 60 * 60 * 1000).toISOString(),
      updatedAt: new Date().toISOString(),
      uploads: [
        {
          id: 1001,
          videoId: 101,
          platform: 'YOUTUBE' as Platform,
          status: 'PUBLISHED',
          platformVideoId: 'yt123',
          platformUrl: 'https://youtube.com/watch?v=yt123',
          title: '서울 여행 브이로그',
          description: null,
          tags: [],
          errorMessage: null,
          publishedAt: new Date(Date.now() - 40 * 24 * 60 * 60 * 1000).toISOString(),
          createdAt: new Date().toISOString(),
        },
      ],
    } as any,
    {
      id: 102,
      userId: 1,
      title: '홈 카페 만들기 - 라떼아트 기초',
      description: '집에서 쉽게 따라할 수 있는 라떼아트',
      tags: ['카페', '라떼아트', '홈카페'],
      category: '교육',
      fileUrl: 'https://example.com/video2.mp4',
      thumbnailUrl: null,
      thumbnailCandidates: [],
      duration: 480,
      fileSize: 40000000,
      resolution: '1080p',
      status: 'PUBLISHED',
      visibility: 'PUBLIC',
      createdAt: new Date(Date.now() - 35 * 24 * 60 * 60 * 1000).toISOString(),
      updatedAt: new Date().toISOString(),
      uploads: [
        {
          id: 1002,
          videoId: 102,
          platform: 'YOUTUBE' as Platform,
          status: 'PUBLISHED',
          platformVideoId: 'yt124',
          platformUrl: 'https://youtube.com/watch?v=yt124',
          title: '홈 카페 만들기',
          description: null,
          tags: [],
          errorMessage: null,
          publishedAt: new Date(Date.now() - 35 * 24 * 60 * 60 * 1000).toISOString(),
          createdAt: new Date().toISOString(),
        },
        {
          id: 1003,
          videoId: 102,
          platform: 'INSTAGRAM' as Platform,
          status: 'PUBLISHED',
          platformVideoId: 'ig124',
          platformUrl: 'https://instagram.com/p/ig124',
          title: '홈 카페 만들기',
          description: null,
          tags: [],
          errorMessage: null,
          publishedAt: new Date(Date.now() - 35 * 24 * 60 * 60 * 1000).toISOString(),
          createdAt: new Date().toISOString(),
        },
      ],
    } as any,
  ]

  // Add mock totalViews
  ;(mockVideos.value[0] as any).totalViews = 15000
  ;(mockVideos.value[1] as any).totalViews = 8500
})
</script>
