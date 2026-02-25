<template>
  <div class="space-y-4">
    <!-- 링크 목록 -->
    <div
      v-for="link in links"
      :key="link.id"
      class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
    >
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div class="min-w-0 flex-1">
          <h4 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ link.productName }}
          </h4>
          <div class="mt-1 flex items-center gap-2">
            <span
              class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
              :class="platformBadgeClass(link.platform)"
            >
              {{ platformLabel(link.platform) }}
            </span>
          </div>
        </div>

        <!-- 통계 -->
        <div class="flex items-center gap-4 text-sm">
          <div class="text-center">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('commerce.clicks') }}</p>
            <p class="font-semibold text-gray-900 dark:text-gray-100">{{ link.clicks.toLocaleString() }}</p>
          </div>
          <div class="text-center">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('commerce.conversions') }}</p>
            <p class="font-semibold text-gray-900 dark:text-gray-100">{{ link.conversions.toLocaleString() }}</p>
          </div>
          <div class="text-center">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('commerce.revenue') }}</p>
            <p class="font-semibold text-gray-900 dark:text-gray-100">{{ formatCompact(link.revenue) }}</p>
          </div>
        </div>
      </div>

      <!-- 링크 URL -->
      <div class="mt-3 flex items-center gap-2 rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900">
        <LinkIcon class="h-4 w-4 shrink-0 text-gray-400" />
        <span class="flex-1 truncate text-xs text-gray-600 dark:text-gray-400">
          {{ link.shortUrl || link.affiliateUrl }}
        </span>
        <button
          class="shrink-0 rounded px-2 py-1 text-xs font-medium text-primary-600 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20"
          @click="copyLink(link.shortUrl || link.affiliateUrl)"
        >
          {{ copied === link.id ? $t('commerce.copied') : $t('commerce.copy') }}
        </button>
      </div>

      <p class="mt-2 text-xs text-gray-400 dark:text-gray-500">
        {{ $t('commerce.createdAt') }} {{ formatDate(link.createdAt) }}
      </p>
    </div>

    <!-- 빈 상태 -->
    <div v-if="links.length === 0" class="py-12 text-center text-gray-400 dark:text-gray-500">
      <LinkIcon class="mx-auto h-10 w-10 mb-2" />
      <p class="text-sm">{{ $t('commerce.emptyLinks') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { LinkIcon } from '@heroicons/vue/24/outline'
import type { AffiliateLink, CommercePlatform } from '@/types/commerce'
import { COMMERCE_PLATFORM_CONFIG } from './commerceConstants'

defineProps<{
  links: AffiliateLink[]
}>()

const copied = ref<number | null>(null)

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

async function copyLink(url: string) {
  try {
    await navigator.clipboard.writeText(url)
    // 간단한 피드백으로 복사 완료 표시
    copied.value = -1 // 임시
    setTimeout(() => { copied.value = null }, 2000)
  } catch {
    // fallback
  }
}
</script>
