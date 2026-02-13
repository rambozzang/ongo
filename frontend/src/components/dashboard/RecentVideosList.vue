<template>
  <div class="card">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">최근 업로드</h3>
      <router-link to="/videos" class="text-sm text-primary-600 hover:underline">
        전체 보기
      </router-link>
    </div>
    <div v-if="videos.length === 0" class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
      업로드된 영상이 없습니다
    </div>
    <ul v-else class="divide-y divide-gray-100 dark:divide-gray-700">
      <li v-for="video in videos" :key="video.id" class="flex items-center gap-3 py-3">
        <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-800">
          <img
            v-if="video.thumbnailUrl"
            :src="video.thumbnailUrl"
            :alt="video.title"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <FilmIcon class="h-5 w-5 text-gray-300 dark:text-gray-600" />
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ video.title }}</p>
          <div class="mt-1 flex flex-wrap items-center gap-1">
            <PlatformBadge
              v-for="upload in video.uploads"
              :key="upload.platform"
              :platform="upload.platform"
            />
            <StatusBadge :status="video.status" />
          </div>
        </div>
        <div class="flex-shrink-0 text-right">
          <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ formatViews(video) }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ timeAgo(video.createdAt) }}</p>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { FilmIcon } from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import type { Video } from '@/types/video'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ko'

dayjs.extend(relativeTime)
dayjs.locale('ko')

defineProps<{
  videos: Video[]
}>()

function formatViews(video: Video): string {
  return video.status === 'PUBLISHED' ? '게시완료' : video.status === 'UPLOADING' ? '업로드중' : ''
}

function timeAgo(date: string): string {
  return dayjs(date).fromNow()
}
</script>
