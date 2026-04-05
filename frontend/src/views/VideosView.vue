<template>
  <div>
    <!-- Header -->
    <PageHeader :title="$t('videos.title')" :description="$t('videos.description')">
      <template #actions>
        <router-link to="/upload" class="btn-primary inline-flex items-center gap-2">
          <PlusIcon class="h-5 w-5" />
          {{ $t('videos.uploadNew') }}
        </router-link>
      </template>
    </PageHeader>

    <!-- Platform Filter Tabs + Sort -->
    <div class="card mb-6">
      <div class="flex flex-col gap-3 tablet:flex-row tablet:items-center tablet:justify-between">
        <!-- Platform Tabs -->
        <div class="flex flex-wrap gap-2">
          <button
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="
              !selectedPlatform
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
            "
            @click="selectPlatform(undefined)"
          >
            {{ $t('videos.allPlatforms') }}
          </button>
          <button
            v-for="p in availablePlatforms"
            :key="p"
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="
              selectedPlatform === p
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
            "
            @click="selectPlatform(p)"
          >
            {{ PLATFORM_CONFIG[p]?.label || p }}
          </button>
        </div>

        <!-- Sort -->
        <select v-model="sortBy" class="input-field text-sm w-auto" @change="loadFeed">
          <option value="recent">{{ $t('videos.sortRecent') }}</option>
          <option value="views">{{ $t('videos.sortViews') }}</option>
          <option value="likes">{{ $t('videos.sortLikes') }}</option>
          <option value="comments">{{ $t('videos.sortComments') }}</option>
        </select>
      </div>
    </div>

    <!-- Platform Errors -->
    <div
      v-if="feedErrors?.length"
      class="mb-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-700 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-300"
    >
      <span v-for="(err, i) in feedErrors" :key="i">
        {{ $t('videos.platformError', { platform: err }) }}
        <span v-if="i < feedErrors.length - 1">, </span>
      </span>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" />

    <!-- Empty: No channels -->
    <EmptyState
      v-else-if="feedItems.length === 0 && !loading && availablePlatforms.length === 0"
      :title="$t('videos.noChannels')"
      :description="$t('videos.noChannelsDesc')"
    >
      <template #action>
        <router-link to="/channels" class="btn-primary">
          {{ $t('videos.connectChannel') }}
        </router-link>
      </template>
    </EmptyState>

    <!-- Empty: No videos -->
    <EmptyState
      v-else-if="feedItems.length === 0 && !loading"
      :title="$t('videos.noVideos')"
      :description="''"
    />

    <!-- Feed Table (tablet+) -->
    <div v-else class="hidden tablet:block">
      <div class="card overflow-hidden">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50">
            <tr>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">{{ $t('videos.title') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-28 text-center">{{ $t('videos.views') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-24 text-center">{{ $t('videos.likes') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-24 text-center">{{ $t('videos.comments') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-24 text-center">{{ $t('videos.shares') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-28">{{ $t('videos.publishedAt') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
            <tr
              v-for="item in feedItems"
              :key="`${item.platform}-${item.platformVideoId}`"
              class="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors"
              @click="openDetail(item)"
            >
              <td class="px-4 py-3">
                <div class="flex items-center gap-3">
                  <!-- Thumbnail -->
                  <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded-md bg-gray-200 dark:bg-gray-700">
                    <img
                      v-if="item.thumbnailUrl"
                      :src="item.thumbnailUrl"
                      :alt="item.title"
                      class="h-full w-full object-cover"
                      loading="lazy"
                    />
                    <div v-else class="flex h-full w-full items-center justify-center">
                      <FilmIcon class="h-5 w-5 text-gray-400" />
                    </div>
                  </div>
                  <!-- Title + Platform -->
                  <div class="min-w-0">
                    <p class="truncate font-medium text-gray-900 dark:text-white">{{ item.title }}</p>
                    <div class="flex items-center gap-1.5 mt-0.5">
                      <span
                        class="inline-block h-2 w-2 rounded-full"
                        :style="{ backgroundColor: PLATFORM_CONFIG[item.platform]?.color || '#666' }"
                      ></span>
                      <span class="text-xs text-gray-500 dark:text-gray-400">
                        {{ PLATFORM_CONFIG[item.platform]?.label || item.platform }} · {{ item.channelName }}
                      </span>
                    </div>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3 text-center tabular-nums text-gray-700 dark:text-gray-300">{{ formatCount(item.viewCount) }}</td>
              <td class="px-4 py-3 text-center tabular-nums text-gray-700 dark:text-gray-300">{{ formatCount(item.likeCount) }}</td>
              <td class="px-4 py-3 text-center tabular-nums text-gray-700 dark:text-gray-300">{{ formatCount(item.commentCount) }}</td>
              <td class="px-4 py-3 text-center tabular-nums text-gray-700 dark:text-gray-300">{{ formatCount(item.shareCount) }}</td>
              <td class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">{{ formatDate(item.publishedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Feed Cards (mobile) -->
    <div class="tablet:hidden space-y-3">
      <div
        v-for="item in feedItems"
        :key="`m-${item.platform}-${item.platformVideoId}`"
        class="card cursor-pointer p-3"
        @click="openDetail(item)"
      >
        <div class="flex gap-3">
          <!-- Thumbnail -->
          <div class="h-16 w-24 flex-shrink-0 overflow-hidden rounded-md bg-gray-200 dark:bg-gray-700">
            <img
              v-if="item.thumbnailUrl"
              :src="item.thumbnailUrl"
              :alt="item.title"
              class="h-full w-full object-cover"
              loading="lazy"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <FilmIcon class="h-5 w-5 text-gray-400" />
            </div>
          </div>
          <!-- Info -->
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ item.title }}</p>
            <div class="flex items-center gap-1.5 mt-0.5">
              <span
                class="inline-block h-2 w-2 rounded-full"
                :style="{ backgroundColor: PLATFORM_CONFIG[item.platform]?.color || '#666' }"
              ></span>
              <span class="text-xs text-gray-500 dark:text-gray-400">{{ PLATFORM_CONFIG[item.platform]?.label || item.platform }}</span>
            </div>
            <!-- Metrics -->
            <div class="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-gray-500 dark:text-gray-400">
              <span>
                <EyeIcon class="inline h-3.5 w-3.5 mr-0.5" />
                {{ formatCount(item.viewCount) }}
              </span>
              <span>
                <HeartIcon class="inline h-3.5 w-3.5 mr-0.5" />
                {{ formatCount(item.likeCount) }}
              </span>
              <span>
                <ChatBubbleLeftIcon class="inline h-3.5 w-3.5 mr-0.5" />
                {{ formatCount(item.commentCount) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Detail Panel (slide) -->
    <VideoDetailPanel
      v-if="selectedItem"
      :item="selectedItem"
      @close="selectedItem = null"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  PlusIcon,
  FilmIcon,
  EyeIcon,
  HeartIcon,
  ChatBubbleLeftIcon,
} from '@heroicons/vue/24/outline'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { VideoFeedItem } from '@/types/video'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import VideoDetailPanel from '@/components/video/VideoDetailPanel.vue'
import { useVideoStore } from '@/stores/video'
import { storeToRefs } from 'pinia'
import { formatCount, formatDate } from '@/utils/format'

const videoStore = useVideoStore()
const { feedItems, feedPlatforms, feedErrors, isFeedLoading: loading } = storeToRefs(videoStore)

const selectedPlatform = ref<Platform | undefined>()
const sortBy = ref('recent')
const selectedItem = ref<VideoFeedItem | null>(null)

const availablePlatforms = computed(() => feedPlatforms.value || [])

function selectPlatform(p: Platform | undefined) {
  selectedPlatform.value = p
  loadFeed()
}

function loadFeed() {
  videoStore.feedFilter.platform = selectedPlatform.value
  videoStore.feedFilter.sort = sortBy.value
  videoStore.fetchFeed()
}

function openDetail(item: VideoFeedItem) {
  selectedItem.value = item
}

onMounted(() => {
  loadFeed()
})
</script>
