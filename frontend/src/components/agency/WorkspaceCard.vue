<template>
  <div
    class="group rounded-xl border border-gray-200 bg-white p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
  >
    <!-- Header -->
    <div class="flex items-start gap-3">
      <div
        class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full text-lg font-bold text-white"
        :style="{ backgroundColor: avatarColor }"
      >
        {{ creator.name.charAt(0) }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <h3 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ creator.name }}
          </h3>
          <span
            class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium"
            :class="statusClass"
          >
            {{ $t(`agency.status.${creator.status}`) }}
          </span>
        </div>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ $t('agency.joinedAt') }} {{ formatDate(creator.joinedAt) }}
        </p>
      </div>
    </div>

    <!-- Channels -->
    <div class="mt-4 flex flex-wrap gap-1.5">
      <span
        v-for="channel in creator.channels"
        :key="channel.id"
        class="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium"
        :class="platformClass(channel.platform)"
      >
        {{ platformLabel(channel.platform) }}
        <span class="opacity-70">{{ formatCompact(channel.subscriberCount) }}</span>
      </span>
    </div>

    <!-- Stats -->
    <div class="mt-4 grid grid-cols-3 gap-3 border-t border-gray-100 pt-4 dark:border-gray-700">
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('agency.subscribers') }}</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatCompact(creator.totalSubscribers) }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('agency.views') }}</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatCompact(creator.totalViews) }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('agency.growth') }}</p>
        <p
          class="text-sm font-semibold"
          :class="creator.recentGrowthPercent >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
        >
          {{ creator.recentGrowthPercent >= 0 ? '+' : '' }}{{ creator.recentGrowthPercent }}%
        </p>
      </div>
    </div>

    <!-- Actions -->
    <div class="mt-4 flex gap-2">
      <button
        class="btn-secondary flex-1 text-xs"
        @click="$emit('viewDetail', creator.id)"
      >
        {{ $t('agency.viewDetail') }}
      </button>
      <button
        class="btn-secondary text-xs"
        @click="$emit('createLink', creator.id)"
      >
        <LinkIcon class="h-3.5 w-3.5" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { LinkIcon } from '@heroicons/vue/24/outline'
import type { AgencyCreator, PlatformType } from '@/types/agency'

const props = defineProps<{
  creator: AgencyCreator
}>()

defineEmits<{
  viewDetail: [creatorId: number]
  createLink: [creatorId: number]
}>()

const colors = ['#6366F1', '#EC4899', '#10B981', '#F59E0B', '#3B82F6', '#8B5CF6']
const avatarColor = computed(() => colors[props.creator.id % colors.length])

const statusClass = computed(() => {
  switch (props.creator.status) {
    case 'active': return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
    case 'inactive': return 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
    case 'pending': return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300'
    default: return 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
  }
})

function platformClass(platform: PlatformType): string {
  const map: Record<PlatformType, string> = {
    YOUTUBE: 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-300',
    TIKTOK: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200',
    INSTAGRAM: 'bg-pink-50 text-pink-700 dark:bg-pink-900/20 dark:text-pink-300',
    NAVER_CLIP: 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-300',
  }
  return map[platform]
}

function platformLabel(platform: PlatformType): string {
  const map: Record<PlatformType, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVER_CLIP: 'Naver',
  }
  return map[platform]
}

function formatCompact(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}

function formatDate(date: string): string {
  return new Date(date).toLocaleDateString('ko-KR', { year: 'numeric', month: 'short' })
}
</script>
