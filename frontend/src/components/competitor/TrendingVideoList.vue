<script setup lang="ts">
import { ref, computed } from 'vue'
import { PlayIcon, HeartIcon, ChatBubbleLeftIcon } from '@heroicons/vue/24/outline'
import type { CompetitorVideo, Competitor } from '@/types/competitor'

interface Props {
  videos: CompetitorVideo[]
  competitors: Competitor[]
}

type SortBy = 'views' | 'recency' | 'engagement'

const props = defineProps<Props>()

const selectedCompetitorId = ref<number | null>(null)
const sortBy = ref<SortBy>('views')

const filteredAndSortedVideos = computed(() => {
  let result = [...props.videos]

  // Filter by competitor
  if (selectedCompetitorId.value !== null) {
    result = result.filter(v => v.competitorId === selectedCompetitorId.value)
  }

  // Sort
  result.sort((a, b) => {
    switch (sortBy.value) {
      case 'views':
        return b.views - a.views
      case 'recency':
        return new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime()
      case 'engagement': {
        const aEngagement = a.likes + a.comments
        const bEngagement = b.likes + b.comments
        return bEngagement - aEngagement
      }
      default:
        return 0
    }
  })

  return result
})

function getCompetitorName(competitorId: number): string {
  const competitor = props.competitors.find(c => c.id === competitorId)
  return competitor?.name || '알 수 없음'
}

function getCompetitorAvatar(competitorId: number): string {
  const competitor = props.competitors.find(c => c.id === competitorId)
  return competitor?.avatarUrl || 'https://i.pravatar.cc/150'
}

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`
  }
  return num.toString()
}

function formatDate(dateString: string): string {
  const date = new Date(dateString)
  const now = new Date()
  const diffInMs = now.getTime() - date.getTime()
  const diffInHours = Math.floor(diffInMs / (1000 * 60 * 60))
  const diffInDays = Math.floor(diffInHours / 24)

  if (diffInHours < 1) {
    return '방금 전'
  }
  if (diffInHours < 24) {
    return `${diffInHours}시간 전`
  }
  if (diffInDays < 7) {
    return `${diffInDays}일 전`
  }
  if (diffInDays < 30) {
    return `${Math.floor(diffInDays / 7)}주 전`
  }
  return date.toLocaleDateString('ko-KR')
}
</script>

<template>
  <div class="space-y-4">
    <!-- Filters -->
    <div class="flex flex-col sm:flex-row gap-3">
      <!-- Competitor filter -->
      <select
        v-model="selectedCompetitorId"
        class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        <option :value="null">모든 채널</option>
        <option
          v-for="competitor in competitors"
          :key="competitor.id"
          :value="competitor.id"
        >
          {{ competitor.name }}
        </option>
      </select>

      <!-- Sort selector -->
      <select
        v-model="sortBy"
        class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        <option value="views">조회수 높은 순</option>
        <option value="recency">최신 순</option>
        <option value="engagement">인기 순 (좋아요+댓글)</option>
      </select>
    </div>

    <!-- Video list -->
    <div class="space-y-3">
      <div
        v-for="video in filteredAndSortedVideos"
        :key="video.id"
        class="flex gap-3 p-3 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg hover:shadow-md transition-shadow"
      >
        <!-- Thumbnail -->
        <div class="relative flex-shrink-0">
          <img
            :src="video.thumbnailUrl"
            :alt="video.title"
            class="w-40 h-24 rounded-lg object-cover"
          />
          <div class="absolute bottom-1 right-1 px-1.5 py-0.5 bg-black/80 text-white text-xs rounded">
            {{ video.duration }}
          </div>
          <div class="absolute inset-0 flex items-center justify-center">
            <PlayIcon class="w-8 h-8 text-white opacity-80" />
          </div>
        </div>

        <!-- Info -->
        <div class="flex-1 min-w-0">
          <!-- Title -->
          <h3 class="font-medium text-gray-900 dark:text-white line-clamp-2 mb-1">
            {{ video.title }}
          </h3>

          <!-- Channel -->
          <div class="flex items-center space-x-2 mb-2">
            <img
              :src="getCompetitorAvatar(video.competitorId)"
              :alt="getCompetitorName(video.competitorId)"
              class="w-6 h-6 rounded-full"
            />
            <span class="text-sm text-gray-600 dark:text-gray-400">
              {{ getCompetitorName(video.competitorId) }}
            </span>
          </div>

          <!-- Stats -->
          <div class="flex items-center flex-wrap gap-3 text-sm text-gray-600 dark:text-gray-400">
            <div class="flex items-center space-x-1">
              <PlayIcon class="w-4 h-4" />
              <span>{{ formatNumber(video.views) }}</span>
            </div>
            <div class="flex items-center space-x-1">
              <HeartIcon class="w-4 h-4" />
              <span>{{ formatNumber(video.likes) }}</span>
            </div>
            <div class="flex items-center space-x-1">
              <ChatBubbleLeftIcon class="w-4 h-4" />
              <span>{{ formatNumber(video.comments) }}</span>
            </div>
            <span class="text-gray-500 dark:text-gray-500">
              {{ formatDate(video.publishedAt) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div
        v-if="filteredAndSortedVideos.length === 0"
        class="text-center py-12 text-gray-500 dark:text-gray-400"
      >
        영상이 없습니다
      </div>
    </div>
  </div>
</template>
