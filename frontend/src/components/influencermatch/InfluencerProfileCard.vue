<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header: Avatar + Name + Platform + Category -->
    <div class="mb-3 flex items-start gap-3">
      <div
        class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full text-lg font-bold text-white"
        :class="avatarColorClass"
      >
        {{ initials }}
      </div>
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ profile.name }}
        </h3>
        <div class="mt-1 flex flex-wrap items-center gap-1.5">
          <span
            class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
            :class="platformBadgeClass"
          >
            {{ platformLabel }}
          </span>
          <span class="inline-flex items-center rounded-full bg-gray-100 dark:bg-gray-700 px-2 py-0.5 text-xs font-medium text-gray-600 dark:text-gray-300">
            {{ profile.category }}
          </span>
        </div>
      </div>

      <!-- Match Score Circle -->
      <div class="flex-shrink-0">
        <div class="relative">
          <svg class="h-14 w-14" viewBox="0 0 56 56">
            <circle
              cx="28" cy="28" r="24"
              fill="none"
              class="stroke-gray-200 dark:stroke-gray-700"
              stroke-width="4"
            />
            <circle
              cx="28" cy="28" r="24"
              fill="none"
              :class="scoreStrokeClass"
              stroke-width="4"
              stroke-linecap="round"
              :stroke-dasharray="circumference"
              :stroke-dashoffset="scoreOffset"
              transform="rotate(-90 28 28)"
              class="transition-all duration-500"
            />
          </svg>
          <span
            class="absolute inset-0 flex items-center justify-center text-xs font-bold"
            :class="scoreTextClass"
          >
            {{ profile.matchScore }}
          </span>
        </div>
      </div>
    </div>

    <!-- Match Reason -->
    <p class="mb-3 text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
      {{ profile.matchReason }}
    </p>

    <!-- Stats Row -->
    <div class="mb-3 grid grid-cols-3 gap-2 text-center">
      <div class="rounded-lg bg-gray-50 dark:bg-gray-800 px-2 py-1.5">
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatFollowers(profile.followers) }}</p>
        <p class="text-[10px] text-gray-500 dark:text-gray-400">{{ $t('influencerMatch.card.followers') }}</p>
      </div>
      <div class="rounded-lg bg-gray-50 dark:bg-gray-800 px-2 py-1.5">
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatFollowers(profile.avgViews) }}</p>
        <p class="text-[10px] text-gray-500 dark:text-gray-400">{{ $t('influencerMatch.card.avgViews') }}</p>
      </div>
      <div class="rounded-lg bg-gray-50 dark:bg-gray-800 px-2 py-1.5">
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ profile.engagementRate }}%</p>
        <p class="text-[10px] text-gray-500 dark:text-gray-400">{{ $t('influencerMatch.card.engagement') }}</p>
      </div>
    </div>

    <!-- Audience Overlap Bar -->
    <div class="mb-3">
      <div class="mb-1 flex items-center justify-between">
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('influencerMatch.card.audienceOverlap') }}</span>
        <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ profile.audienceOverlap }}%</span>
      </div>
      <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-full rounded-full bg-primary-500 dark:bg-primary-400 transition-all duration-500"
          :style="{ width: `${profile.audienceOverlap}%` }"
        />
      </div>
    </div>

    <!-- Estimated Cost -->
    <div class="mb-3 flex items-center justify-between">
      <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('influencerMatch.card.estimatedCost') }}</span>
      <span class="text-sm font-semibold text-primary-600 dark:text-primary-400">{{ formatKRW(profile.estimatedCost) }}</span>
    </div>

    <!-- Recent Content -->
    <div v-if="profile.recentContent.length > 0" class="mb-3 border-t border-gray-100 dark:border-gray-800 pt-3">
      <p class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('influencerMatch.card.recentContent') }}</p>
      <ul class="space-y-1">
        <li
          v-for="(content, index) in profile.recentContent.slice(0, 2)"
          :key="index"
          class="flex items-center justify-between text-xs"
        >
          <span class="truncate text-gray-700 dark:text-gray-300">{{ content.title }}</span>
          <span class="ml-2 flex-shrink-0 text-gray-400 dark:text-gray-500">{{ formatFollowers(content.views) }}</span>
        </li>
      </ul>
    </div>

    <!-- Contact Button -->
    <button
      class="btn-primary w-full text-sm"
      @click="emit('contact', profile.id)"
    >
      {{ $t('influencerMatch.card.contact') }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { InfluencerProfile } from '@/types/influencerMatch'

const props = defineProps<{
  profile: InfluencerProfile
}>()

const emit = defineEmits<{
  contact: [influencerId: number]
}>()

const circumference = 2 * Math.PI * 24

const initials = computed(() => {
  const name = props.profile.name
  return name.length >= 2 ? name.slice(0, 2) : name
})

const avatarColors = [
  'bg-blue-500', 'bg-purple-500', 'bg-pink-500', 'bg-indigo-500',
  'bg-teal-500', 'bg-orange-500', 'bg-cyan-500', 'bg-emerald-500',
]

const avatarColorClass = computed(() => {
  const index = props.profile.id % avatarColors.length
  return avatarColors[index]
})

const platformBadgeClass = computed(() => {
  switch (props.profile.platform) {
    case 'youtube':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    case 'instagram':
      return 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'
    case 'tiktok':
      return 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400'
    case 'naverclip':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
  }
})

const platformLabel = computed(() => {
  switch (props.profile.platform) {
    case 'youtube': return 'YouTube'
    case 'instagram': return 'Instagram'
    case 'tiktok': return 'TikTok'
    case 'naverclip': return 'Naver Clip'
    default: return props.profile.platform
  }
})

const scoreStrokeClass = computed(() => {
  if (props.profile.matchScore >= 85) return 'stroke-green-500 dark:stroke-green-400'
  if (props.profile.matchScore >= 70) return 'stroke-yellow-500 dark:stroke-yellow-400'
  return 'stroke-red-500 dark:stroke-red-400'
})

const scoreTextClass = computed(() => {
  if (props.profile.matchScore >= 85) return 'text-green-600 dark:text-green-400'
  if (props.profile.matchScore >= 70) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

const scoreOffset = computed(() => {
  return circumference - (props.profile.matchScore / 100) * circumference
})

function formatFollowers(num: number): string {
  if (num >= 1_000_000) return `${(num / 1_000_000).toFixed(1)}M`
  if (num >= 1_000) return `${(num / 1_000).toFixed(1)}K`
  return num.toLocaleString()
}

function formatKRW(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}
</script>
