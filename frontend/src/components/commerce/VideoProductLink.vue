<template>
  <div class="space-y-4">
    <div
      v-for="link in links"
      :key="link.id"
      class="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 sm:flex-row sm:items-center dark:border-gray-700 dark:bg-gray-800"
    >
      <!-- 영상 정보 -->
      <div class="flex items-center gap-3 sm:w-2/5">
        <div class="flex h-16 w-24 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
          <img
            v-if="link.videoThumbnail"
            :src="link.videoThumbnail"
            :alt="link.videoTitle"
            class="h-full w-full object-cover"
          />
          <VideoCameraIcon v-else class="h-6 w-6 text-gray-300 dark:text-gray-500" />
        </div>
        <div class="min-w-0">
          <h4 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ link.videoTitle }}
          </h4>
          <span class="text-xs text-gray-500 dark:text-gray-400">
            {{ formatDate(link.linkedAt) }}
          </span>
        </div>
      </div>

      <!-- 연결 아이콘 -->
      <div class="hidden items-center justify-center sm:flex sm:w-1/12">
        <ArrowRightIcon class="h-5 w-5 text-gray-300 dark:text-gray-600" />
      </div>

      <!-- 상품 정보 -->
      <div class="flex items-center gap-3 sm:w-2/5">
        <div class="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
          <img
            v-if="link.productImageUrl"
            :src="link.productImageUrl"
            :alt="link.productName"
            class="h-full w-full object-cover"
          />
          <ShoppingBagIcon v-else class="h-5 w-5 text-gray-300 dark:text-gray-500" />
        </div>
        <div class="min-w-0">
          <h4 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ link.productName }}
          </h4>
          <span
            class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
            :class="platformBadgeClass(link.platform)"
          >
            {{ platformLabel(link.platform) }}
          </span>
        </div>
      </div>

      <!-- 성과 -->
      <div class="flex items-center gap-3 text-xs sm:w-auto">
        <div class="text-center">
          <p class="text-gray-500 dark:text-gray-400">{{ $t('commerce.clicks') }}</p>
          <p class="font-semibold text-gray-900 dark:text-gray-100">{{ link.clicks.toLocaleString() }}</p>
        </div>
        <div class="text-center">
          <p class="text-gray-500 dark:text-gray-400">{{ $t('commerce.revenue') }}</p>
          <p class="font-semibold text-gray-900 dark:text-gray-100">{{ formatCompact(link.revenue) }}</p>
        </div>
      </div>
    </div>

    <!-- 빈 상태 -->
    <div v-if="links.length === 0" class="py-12 text-center text-gray-400 dark:text-gray-500">
      <LinkIcon class="mx-auto h-10 w-10 mb-2" />
      <p class="text-sm">{{ $t('commerce.emptyVideoLinks') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { VideoCameraIcon, ShoppingBagIcon, ArrowRightIcon, LinkIcon } from '@heroicons/vue/24/outline'
import type { VideoProductLink as VideoProductLinkType, CommercePlatform } from '@/types/commerce'
import { COMMERCE_PLATFORM_CONFIG } from './commerceConstants'

defineProps<{
  links: VideoProductLinkType[]
}>()

function platformLabel(platform: CommercePlatform): string {
  return COMMERCE_PLATFORM_CONFIG[platform]?.label ?? platform
}

function platformBadgeClass(platform: CommercePlatform): string {
  return COMMERCE_PLATFORM_CONFIG[platform]?.badgeClass ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
}

function formatCompact(value: number): string {
  if (value >= 10000) return `₩${(value / 10000).toFixed(0)}만`
  return `₩${value.toLocaleString('ko-KR')}`
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR')
}
</script>
