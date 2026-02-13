<template>
  <div
    class="card cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
    @click="$router.push('/analytics')"
  >
    <div class="flex items-center justify-between">
      <p class="text-sm font-medium text-gray-500 dark:text-gray-400">이달의 인기 영상</p>
      <FireIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
    </div>

    <div v-if="topVideos.length > 0" class="mt-3 space-y-2">
      <div
        v-for="(video, index) in topVideos"
        :key="video.videoId"
        class="flex items-start gap-2"
      >
        <div class="flex-shrink-0 flex items-center justify-center w-5 h-5 rounded-full text-xs font-bold"
          :class="getRankClass(index)"
        >
          {{ index + 1 }}
        </div>
        <div class="min-w-0 flex-1">
          <p class="line-clamp-1 text-xs font-medium text-gray-900 dark:text-gray-100">
            {{ video.title }}
          </p>
          <p class="mt-0.5 text-[11px] text-gray-500 dark:text-gray-400">
            조회수 {{ formatViews(video.totalViews) }}
          </p>
        </div>
      </div>
    </div>

    <div v-else class="mt-3 py-4 text-center text-xs text-gray-400 dark:text-gray-500">
      아직 데이터가 없습니다
    </div>
  </div>
</template>

<script setup lang="ts">
import { FireIcon } from '@heroicons/vue/24/solid'
import type { TopVideo } from '@/types/analytics'

defineProps<{
  topVideos: TopVideo[]
}>()


function getRankClass(index: number): string {
  switch (index) {
    case 0:
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 1:
      return 'bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
    case 2:
      return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
  }
}

function formatViews(views: number): string {
  if (views >= 10000) {
    return `${(views / 10000).toFixed(1)}만`
  } else if (views >= 1000) {
    return `${(views / 1000).toFixed(1)}천`
  }
  return views.toLocaleString()
}
</script>
