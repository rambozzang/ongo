<template>
  <div class="rounded-lg border border-gray-200 bg-white p-4 transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-800">
    <!-- 상품 이미지 -->
    <div class="mb-3 flex h-32 items-center justify-center overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
      <img
        v-if="product.imageUrl"
        :src="product.imageUrl"
        :alt="product.name"
        class="h-full w-full object-cover"
      />
      <ShoppingBagIcon v-else class="h-12 w-12 text-gray-300 dark:text-gray-500" />
    </div>

    <!-- 상품 정보 -->
    <div class="space-y-2">
      <div class="flex items-start justify-between gap-2">
        <h3 class="line-clamp-2 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ product.name }}
        </h3>
        <span
          class="inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="platformBadgeClass"
        >
          {{ platformLabel }}
        </span>
      </div>

      <p class="text-lg font-bold text-primary-600 dark:text-primary-400">
        {{ formatKRW(product.price) }}
      </p>

      <!-- 통계 -->
      <div class="grid grid-cols-3 gap-2 border-t border-gray-100 pt-2 dark:border-gray-700">
        <div class="text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('commerce.clicks') }}</p>
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ product.clicks.toLocaleString() }}
          </p>
        </div>
        <div class="text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('commerce.conversions') }}</p>
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ product.conversions.toLocaleString() }}
          </p>
        </div>
        <div class="text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('commerce.revenue') }}</p>
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ formatCompact(product.revenue) }}
          </p>
        </div>
      </div>

      <!-- 연결된 영상 -->
      <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <VideoCameraIcon class="h-3.5 w-3.5" />
        <span>{{ $t('commerce.linkedVideos', { count: product.linkedVideoCount }) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ShoppingBagIcon, VideoCameraIcon } from '@heroicons/vue/24/outline'
import type { CommerceProduct } from '@/types/commerce'
import { COMMERCE_PLATFORM_CONFIG } from './commerceConstants'

const props = defineProps<{
  product: CommerceProduct
}>()

const platformLabel = computed(() => COMMERCE_PLATFORM_CONFIG[props.product.platform]?.label ?? props.product.platform)

const platformBadgeClass = computed(() => {
  const cfg = COMMERCE_PLATFORM_CONFIG[props.product.platform]
  return cfg?.badgeClass ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

function formatKRW(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}

function formatCompact(value: number): string {
  if (value >= 10000) {
    return `₩${(value / 10000).toFixed(0)}만`
  }
  return `₩${value.toLocaleString('ko-KR')}`
}
</script>
