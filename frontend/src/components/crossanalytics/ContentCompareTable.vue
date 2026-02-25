<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CrossPlatformContent, PlatformKey } from '@/types/crossAnalytics'

const props = defineProps<{
  contents: CrossPlatformContent[]
}>()

const { t } = useI18n({ useScope: 'global' })

const allPlatforms: PlatformKey[] = ['youtube', 'tiktok', 'instagram', 'naverClip']

function platformLabel(platform: PlatformKey): string {
  return t(`crossAnalytics.platform.${platform}`)
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString()
}

function getPlatformData(content: CrossPlatformContent, platform: PlatformKey) {
  return content.platforms.find((p) => p.platform === platform) ?? null
}

// Compute best per metric per content for highlighting
interface BestMetrics {
  views: PlatformKey | null
  likes: PlatformKey | null
  ctr: PlatformKey | null
}

function getBestMetrics(content: CrossPlatformContent): BestMetrics {
  let bestViews: PlatformKey | null = null
  let maxViews = -1
  let bestLikes: PlatformKey | null = null
  let maxLikes = -1
  let bestCtr: PlatformKey | null = null
  let maxCtr = -1

  for (const p of content.platforms) {
    if (p.views > maxViews) {
      maxViews = p.views
      bestViews = p.platform
    }
    if (p.likes > maxLikes) {
      maxLikes = p.likes
      bestLikes = p.platform
    }
    if (p.ctr > maxCtr) {
      maxCtr = p.ctr
      bestCtr = p.platform
    }
  }

  return { views: bestViews, likes: bestLikes, ctr: bestCtr }
}

const bestMetricsMap = computed(() => {
  const map = new Map<number, BestMetrics>()
  for (const c of props.contents) {
    map.set(c.id, getBestMetrics(c))
  }
  return map
})

function isBest(contentId: number, platform: PlatformKey, metric: 'views' | 'likes' | 'ctr'): boolean {
  const best = bestMetricsMap.value.get(contentId)
  if (!best) return false
  return best[metric] === platform
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('crossAnalytics.contents.title') }}
    </h3>

    <div v-if="contents.length > 0" class="overflow-x-auto">
      <table class="w-full text-left text-sm">
        <thead>
          <tr class="border-b border-gray-200 dark:border-gray-700">
            <th
              class="whitespace-nowrap px-3 py-3 text-xs font-medium uppercase text-gray-500 dark:text-gray-400"
              rowspan="2"
            >
              {{ $t('crossAnalytics.contents.contentTitle') }}
            </th>
            <th
              v-for="platform in allPlatforms"
              :key="platform"
              class="whitespace-nowrap border-b border-gray-200 px-3 py-2 text-center text-xs font-medium uppercase text-gray-500 dark:border-gray-700 dark:text-gray-400"
              colspan="3"
            >
              {{ platformLabel(platform) }}
            </th>
          </tr>
          <tr class="border-b border-gray-200 dark:border-gray-700">
            <template v-for="platform in allPlatforms" :key="'sub-' + platform">
              <th class="whitespace-nowrap px-2 py-2 text-center text-[10px] font-medium uppercase text-gray-400 dark:text-gray-500">
                {{ $t('crossAnalytics.contents.views') }}
              </th>
              <th class="whitespace-nowrap px-2 py-2 text-center text-[10px] font-medium uppercase text-gray-400 dark:text-gray-500">
                {{ $t('crossAnalytics.contents.likes') }}
              </th>
              <th class="whitespace-nowrap px-2 py-2 text-center text-[10px] font-medium uppercase text-gray-400 dark:text-gray-500">
                {{ $t('crossAnalytics.contents.ctr') }}
              </th>
            </template>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
          <tr
            v-for="content in contents"
            :key="content.id"
            class="transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50"
          >
            <td class="whitespace-nowrap px-3 py-3">
              <div class="flex items-center gap-3">
                <div
                  v-if="content.thumbnailUrl"
                  class="h-8 w-12 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-800"
                >
                  <img
                    :src="content.thumbnailUrl"
                    :alt="content.title"
                    class="h-full w-full object-cover"
                  />
                </div>
                <span class="max-w-[180px] truncate font-medium text-gray-900 dark:text-gray-100" :title="content.title">
                  {{ content.title }}
                </span>
              </div>
            </td>
            <template v-for="platform in allPlatforms" :key="content.id + '-' + platform">
              <td
                class="whitespace-nowrap px-2 py-3 text-center text-sm"
                :class="isBest(content.id, platform, 'views')
                  ? 'bg-green-50 font-semibold text-green-700 dark:bg-green-900/20 dark:text-green-400'
                  : 'text-gray-700 dark:text-gray-300'"
              >
                {{ getPlatformData(content, platform) ? formatNumber(getPlatformData(content, platform)!.views) : $t('crossAnalytics.contents.noPlatformData') }}
              </td>
              <td
                class="whitespace-nowrap px-2 py-3 text-center text-sm"
                :class="isBest(content.id, platform, 'likes')
                  ? 'bg-green-50 font-semibold text-green-700 dark:bg-green-900/20 dark:text-green-400'
                  : 'text-gray-700 dark:text-gray-300'"
              >
                {{ getPlatformData(content, platform) ? formatNumber(getPlatformData(content, platform)!.likes) : $t('crossAnalytics.contents.noPlatformData') }}
              </td>
              <td
                class="whitespace-nowrap px-2 py-3 text-center text-sm"
                :class="isBest(content.id, platform, 'ctr')
                  ? 'bg-green-50 font-semibold text-green-700 dark:bg-green-900/20 dark:text-green-400'
                  : 'text-gray-700 dark:text-gray-300'"
              >
                {{ getPlatformData(content, platform) ? `${getPlatformData(content, platform)!.ctr}%` : $t('crossAnalytics.contents.noPlatformData') }}
              </td>
            </template>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="flex h-32 items-center justify-center text-sm text-gray-400 dark:text-gray-500">
      {{ $t('crossAnalytics.contents.noData') }}
    </div>
  </div>
</template>
