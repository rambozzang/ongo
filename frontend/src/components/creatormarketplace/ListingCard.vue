<script setup lang="ts">
import {
  StarIcon,
  ClockIcon,
  ShoppingCartIcon,
} from '@heroicons/vue/24/solid'
import { ChatBubbleLeftIcon } from '@heroicons/vue/24/outline'
import type { MarketplaceListing } from '@/types/creatorMarketplace'
import ServiceTypeBadge from './ServiceTypeBadge.vue'

defineProps<{
  listing: MarketplaceListing
}>()

const emit = defineEmits<{
  order: [id: number]
}>()

const formatPrice = (price: number): string => {
  if (price >= 10000) {
    return `${(price / 10000).toFixed(0)}만원`
  }
  return `${price.toLocaleString('ko-KR')}원`
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: Service type badge + Creator name -->
    <div class="mb-3 flex items-center justify-between">
      <ServiceTypeBadge :service-type="listing.serviceType" />
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ listing.creatorName }}
      </span>
    </div>

    <!-- Title -->
    <h3 class="mb-2 text-sm font-semibold text-gray-900 dark:text-gray-100 line-clamp-1">
      {{ listing.title }}
    </h3>

    <!-- Description -->
    <p class="mb-3 text-xs text-gray-500 dark:text-gray-400 line-clamp-2">
      {{ listing.description }}
    </p>

    <!-- Rating + Reviews -->
    <div class="mb-3 flex items-center gap-3">
      <div class="flex items-center gap-1">
        <StarIcon class="h-4 w-4 text-yellow-400" />
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ listing.rating.toFixed(1) }}
        </span>
      </div>
      <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <ChatBubbleLeftIcon class="h-3.5 w-3.5" />
        <span>{{ listing.reviewCount }}개 리뷰</span>
      </div>
      <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <ClockIcon class="h-3.5 w-3.5" />
        <span>{{ listing.deliveryDays }}일</span>
      </div>
    </div>

    <!-- Footer: Price + Order button -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <span class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ formatPrice(listing.price) }}
      </span>
      <button
        class="inline-flex items-center gap-1.5 rounded-lg bg-primary-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-primary-700"
        @click.stop="emit('order', listing.id)"
      >
        <ShoppingCartIcon class="h-4 w-4" />
        주문하기
      </button>
    </div>
  </div>
</template>
