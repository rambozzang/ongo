<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900">
    <!-- Top: Avatar + Name + Match Score -->
    <div class="mb-3 flex items-start gap-3">
      <!-- Avatar -->
      <div
        class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br text-lg font-bold text-white"
        :class="avatarGradient"
      >
        <img
          v-if="creator.avatarUrl"
          :src="creator.avatarUrl"
          :alt="creator.name"
          class="h-full w-full rounded-full object-cover"
        />
        <span v-else>{{ creator.name.charAt(0) }}</span>
      </div>

      <!-- Name + Platform -->
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ creator.name }}
        </h3>
        <div class="mt-0.5 flex items-center gap-2">
          <span class="text-xs text-gray-500 dark:text-gray-400">
            {{ platformLabel }}
          </span>
          <span class="text-xs text-gray-400 dark:text-gray-500">
            {{ formattedSubscribers }}
          </span>
        </div>
      </div>

      <!-- Match Score -->
      <MatchScoreBadge :score="creator.matchScore" />
    </div>

    <!-- Category -->
    <div class="mb-3">
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="categoryBadgeClass"
      >
        {{ categoryLabel }}
      </span>
    </div>

    <!-- Stats row -->
    <div class="mb-3 grid grid-cols-2 gap-2">
      <div class="rounded-lg bg-gray-50 px-2.5 py-1.5 dark:bg-gray-800">
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('creatorNetwork.avgViews') }}</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(creator.avgViews) }}</p>
      </div>
      <div class="rounded-lg bg-gray-50 px-2.5 py-1.5 dark:bg-gray-800">
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('creatorNetwork.engagement') }}</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ creator.engagementRate.toFixed(1) }}%</p>
      </div>
    </div>

    <!-- Connection action -->
    <div class="border-t border-gray-100 pt-3 dark:border-gray-700">
      <button
        v-if="creator.connectionStatus === 'NONE'"
        class="w-full rounded-lg bg-primary-600 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-primary-700"
        @click="$emit('connect', creator.id)"
      >
        {{ $t('creatorNetwork.connect') }}
      </button>
      <button
        v-else-if="creator.connectionStatus === 'PENDING'"
        class="w-full cursor-not-allowed rounded-lg bg-yellow-100 px-3 py-2 text-xs font-medium text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400"
        disabled
      >
        {{ $t('creatorNetwork.pending') }}
      </button>
      <div v-else class="flex items-center justify-between">
        <span class="inline-flex items-center gap-1 text-xs font-medium text-green-600 dark:text-green-400">
          <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
          </svg>
          {{ $t('creatorNetwork.connected') }}
        </span>
        <button
          class="rounded-lg px-2.5 py-1 text-xs font-medium text-red-500 transition-colors hover:bg-red-50 dark:hover:bg-red-900/20"
          @click="$emit('disconnect', creator.id)"
        >
          {{ $t('creatorNetwork.disconnect') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Creator } from '@/types/creatorNetwork'
import MatchScoreBadge from './MatchScoreBadge.vue'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  creator: Creator
}>()

defineEmits<{
  connect: [id: number]
  disconnect: [id: number]
}>()

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    youtube: 'YouTube',
    tiktok: 'TikTok',
    instagram: 'Instagram',
    naverclip: 'Naver Clip',
  }
  return labels[props.creator.platform.toLowerCase()] ?? props.creator.platform
})

const avatarGradient = computed(() => {
  const gradients = [
    'from-blue-500 to-cyan-400',
    'from-purple-500 to-pink-400',
    'from-emerald-500 to-teal-400',
    'from-orange-500 to-amber-400',
    'from-red-500 to-rose-400',
    'from-indigo-500 to-blue-400',
  ]
  return gradients[props.creator.id % gradients.length]
})

const categoryBadgeClass = computed(() => {
  const map: Record<string, string> = {
    BEAUTY: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    GAMING: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    FOOD: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    TECH: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    TRAVEL: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
    MUSIC: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
    EDUCATION: 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/30 dark:text-cyan-400',
    LIFESTYLE: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
    FITNESS: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    OTHER: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
  }
  return map[props.creator.category] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const categoryLabel = computed(() => {
  const labels: Record<string, string> = {
    BEAUTY: t('creatorNetwork.catBeauty'),
    GAMING: t('creatorNetwork.catGaming'),
    FOOD: t('creatorNetwork.catFood'),
    TECH: t('creatorNetwork.catTech'),
    TRAVEL: t('creatorNetwork.catTravel'),
    MUSIC: t('creatorNetwork.catMusic'),
    EDUCATION: t('creatorNetwork.catEducation'),
    LIFESTYLE: t('creatorNetwork.catLifestyle'),
    FITNESS: t('creatorNetwork.catFitness'),
    OTHER: t('creatorNetwork.catOther'),
  }
  return labels[props.creator.category] ?? props.creator.category
})

const formattedSubscribers = computed(() => {
  const num = props.creator.subscriberCount
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
})

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}
</script>
