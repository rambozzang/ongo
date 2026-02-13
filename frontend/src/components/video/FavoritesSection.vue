<template>
  <div class="card">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">즐겨찾기</h3>
      <router-link
        v-if="favoriteVideos.length > 0"
        to="/videos?favorites=true"
        class="text-sm text-primary-600 hover:underline"
      >
        전체 보기 →
      </router-link>
    </div>

    <!-- Empty State -->
    <div
      v-if="favoriteVideos.length === 0"
      class="flex flex-col items-center justify-center py-8 text-center"
    >
      <StarIcon class="mb-2 h-10 w-10 text-gray-300 dark:text-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">즐겨찾기한 영상이 없습니다</p>
      <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
        자주 보는 영상을 즐겨찾기에 추가해보세요
      </p>
    </div>

    <!-- Favorites List - Horizontal Scroll -->
    <div
      v-else
      class="flex gap-3 overflow-x-auto pb-2 scrollbar-hide"
      style="scroll-snap-type: x mandatory"
    >
      <div
        v-for="video in displayedVideos"
        :key="video.id"
        class="group relative flex-shrink-0 cursor-pointer"
        style="width: 200px; scroll-snap-align: start"
        @click="goToVideo(video.id)"
      >
        <!-- Thumbnail -->
        <div class="relative aspect-video w-full overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-800">
          <img
            v-if="video.thumbnailUrl"
            :src="video.thumbnailUrl"
            :alt="video.title"
            class="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <FilmIcon class="h-8 w-8 text-gray-300 dark:text-gray-600" />
          </div>

          <!-- Remove Button (visible on hover) -->
          <button
            class="absolute right-1.5 top-1.5 rounded-full bg-black/60 p-1 opacity-0 transition-opacity hover:bg-black/80 group-hover:opacity-100"
            @click.stop="handleRemove(video.id)"
          >
            <XMarkIcon class="h-3.5 w-3.5 text-white" />
          </button>
        </div>

        <!-- Info -->
        <div class="mt-2">
          <p class="line-clamp-2 text-xs font-medium text-gray-900 dark:text-gray-100" :title="video.title">
            {{ video.title }}
          </p>

          <!-- Platform Badges -->
          <div v-if="video.uploads && video.uploads.length > 0" class="mt-1.5 flex flex-wrap gap-1">
            <PlatformBadge
              v-for="upload in video.uploads.slice(0, 3)"
              :key="upload.platform"
              :platform="upload.platform"
              class="scale-75 origin-left"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { StarIcon, FilmIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import { useFavoritesStore } from '@/stores/favorites'
import { useVideoStore } from '@/stores/video'
import type { Video } from '@/types/video'

const router = useRouter()
const favoritesStore = useFavoritesStore()
const videoStore = useVideoStore()

const favoriteVideos = ref<Video[]>([])
const loading = ref(false)

const MAX_DISPLAY = 10

const displayedVideos = computed(() => favoriteVideos.value.slice(0, MAX_DISPLAY))

// Load favorite videos from the video store
async function loadFavoriteVideos() {
  loading.value = true
  try {
    // Get all favorite IDs
    const favoriteIds = favoritesStore.favorites

    if (favoriteIds.length === 0) {
      favoriteVideos.value = []
      return
    }

    // Get videos from the main video store
    // In a real implementation, you might want to call an API to fetch these specific videos
    // For now, we'll filter from the existing videos in the store
    if (videoStore.videos && videoStore.videos.content) {
      favoriteVideos.value = videoStore.videos.content.filter((video) =>
        favoriteIds.includes(video.id)
      )
    } else {
      favoriteVideos.value = []
    }
  } finally {
    loading.value = false
  }
}

function goToVideo(videoId: number) {
  router.push(`/videos/${videoId}`)
}

function handleRemove(videoId: number) {
  favoritesStore.removeFavorite(videoId)
  // Remove from local display
  favoriteVideos.value = favoriteVideos.value.filter((v) => v.id !== videoId)
}

// Watch for changes in favorites
watch(
  () => favoritesStore.favoriteCount,
  () => {
    loadFavoriteVideos()
  },
  { immediate: true }
)

// Watch for changes in video store
watch(
  () => videoStore.videos,
  () => {
    loadFavoriteVideos()
  }
)
</script>

<style scoped>
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

.scrollbar-hide::-webkit-scrollbar {
  display: none;
}
</style>
