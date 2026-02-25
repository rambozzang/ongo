<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Portfolio, PlatformType } from '@/types/portfolio'

defineProps<{
  portfolio: Portfolio
}>()

const { t } = useI18n()

const platformColors: Record<PlatformType, string> = {
  youtube: 'bg-youtube',
  tiktok: 'bg-gray-900 dark:bg-gray-100',
  instagram: 'bg-instagram',
  naver: 'bg-naver',
}

const platformLabels: Record<PlatformType, string> = {
  youtube: 'YouTube',
  tiktok: 'TikTok',
  instagram: 'Instagram',
  naver: 'Naver',
}

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}
</script>

<template>
  <div class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-lg dark:border-gray-700 dark:bg-gray-800">
    <!-- Preview Header -->
    <div class="bg-gradient-to-br from-primary-500 to-primary-700 p-6 text-center text-white">
      <div class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-white/20 text-3xl font-bold backdrop-blur-sm">
        {{ portfolio.profile.displayName.charAt(0) }}
      </div>
      <h2 class="mt-3 text-xl font-bold">{{ portfolio.profile.displayName }}</h2>
      <p class="mt-1 text-sm text-white/80">{{ portfolio.profile.bio }}</p>
      <div class="mt-3 flex flex-wrap justify-center gap-2">
        <span
          v-for="cat in portfolio.profile.categories"
          :key="cat"
          class="rounded-full bg-white/20 px-2.5 py-0.5 text-xs"
        >
          {{ cat }}
        </span>
      </div>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-2 gap-px bg-gray-200 dark:bg-gray-700">
      <div
        v-for="stat in portfolio.channelStats"
        :key="stat.id"
        class="bg-white p-3 text-center dark:bg-gray-800"
      >
        <div class="mx-auto mb-1 flex h-5 w-5 items-center justify-center rounded-full">
          <div :class="['h-2.5 w-2.5 rounded-full', platformColors[stat.platform]]" />
        </div>
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ platformLabels[stat.platform] }}</div>
        <div class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ formatNumber(stat.subscribers) }}</div>
      </div>
    </div>

    <!-- Showcase -->
    <div v-if="portfolio.showcaseContents.length > 0" class="p-4">
      <h3 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ t('portfolio.previewShowcase') }}
      </h3>
      <div class="space-y-2">
        <div
          v-for="content in portfolio.showcaseContents.slice(0, 3)"
          :key="content.id"
          class="flex items-center gap-2 rounded-lg bg-gray-50 p-2 dark:bg-gray-700/50"
        >
          <div class="flex h-10 w-14 shrink-0 items-center justify-center rounded bg-gray-200 text-xs font-medium text-gray-500 dark:bg-gray-600 dark:text-gray-400">
            {{ platformLabels[content.platform].slice(0, 2) }}
          </div>
          <div class="min-w-0 flex-1">
            <div class="truncate text-xs font-medium text-gray-900 dark:text-gray-100">
              {{ content.title }}
            </div>
            <div class="text-xs text-gray-500 dark:text-gray-400">
              {{ formatNumber(content.viewCount) }} {{ t('portfolio.views') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Collaborations preview -->
    <div v-if="portfolio.brandCollaborations.length > 0" class="border-t border-gray-200 p-4 dark:border-gray-700">
      <h3 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ t('portfolio.previewBrands') }}
      </h3>
      <div class="flex flex-wrap gap-2">
        <span
          v-for="collab in portfolio.brandCollaborations"
          :key="collab.id"
          class="rounded-full border border-gray-200 bg-gray-50 px-3 py-1 text-xs font-medium text-gray-600 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          {{ collab.brandName }}
        </span>
      </div>
    </div>

    <!-- Footer -->
    <div class="border-t border-gray-200 p-3 text-center dark:border-gray-700">
      <span class="text-xs text-gray-400 dark:text-gray-500">powered by onGo</span>
    </div>
  </div>
</template>
