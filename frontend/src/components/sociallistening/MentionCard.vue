<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'
import type { BrandMention, MentionSource, SentimentType } from '@/types/socialListening'

const props = defineProps<{
  mention: BrandMention
}>()

const { t } = useI18n({ useScope: 'global' })

const sourceConfig = computed<{ label: string; color: string }>(() => {
  const configs: Record<MentionSource, { label: string; color: string }> = {
    YOUTUBE: { label: 'YouTube', color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400' },
    TIKTOK: { label: 'TikTok', color: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400' },
    INSTAGRAM: { label: 'Instagram', color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400' },
    TWITTER: { label: 'Twitter', color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400' },
    BLOG: { label: 'Blog', color: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400' },
    NEWS: { label: 'News', color: 'bg-teal-100 text-teal-800 dark:bg-teal-900/30 dark:text-teal-400' },
  }
  return configs[props.mention.source] ?? { label: props.mention.source, color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300' }
})

const sentimentConfig = computed<{ label: string; dotColor: string; textColor: string }>(() => {
  const configs: Record<SentimentType, { label: string; dotColor: string; textColor: string }> = {
    POSITIVE: { label: t('socialListening.positive'), dotColor: 'bg-green-500', textColor: 'text-green-600 dark:text-green-400' },
    NEUTRAL: { label: t('socialListening.neutral'), dotColor: 'bg-gray-400', textColor: 'text-gray-500 dark:text-gray-400' },
    NEGATIVE: { label: t('socialListening.negative'), dotColor: 'bg-red-500', textColor: 'text-red-600 dark:text-red-400' },
  }
  return configs[props.mention.sentiment]
})

const formattedReach = computed(() => {
  const reach = props.mention.reach
  if (reach >= 1_000_000) return `${(reach / 1_000_000).toFixed(1)}M`
  if (reach >= 1_000) return `${(reach / 1_000).toFixed(1)}K`
  return reach.toLocaleString()
})

const timeAgo = computed(() => {
  const now = Date.now()
  const created = new Date(props.mention.createdAt).getTime()
  const diffMs = now - created
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return t('socialListening.justNow')
  if (diffMin < 60) return t('socialListening.minutesAgo', { n: diffMin })
  if (diffHour < 24) return t('socialListening.hoursAgo', { n: diffHour })
  if (diffDay < 30) return t('socialListening.daysAgo', { n: diffDay })
  return t('socialListening.daysAgo', { n: diffDay })
})
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Top: Source badge + Sentiment + Time -->
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span
          :class="['inline-block rounded-full px-2.5 py-0.5 text-xs font-medium', sourceConfig.color]"
        >
          {{ sourceConfig.label }}
        </span>
        <div class="flex items-center gap-1">
          <span :class="['inline-block h-2 w-2 rounded-full', sentimentConfig.dotColor]" />
          <span :class="['text-xs font-medium', sentimentConfig.textColor]">
            {{ sentimentConfig.label }}
          </span>
        </div>
      </div>
      <span class="text-xs text-gray-400 dark:text-gray-500">{{ timeAgo }}</span>
    </div>

    <!-- Author -->
    <p class="mb-1 text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ mention.author }}
    </p>

    <!-- Text -->
    <p class="mb-3 line-clamp-3 text-sm text-gray-600 dark:text-gray-400">
      {{ mention.text }}
    </p>

    <!-- Bottom: Reach + Link -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <span class="font-medium">{{ $t('socialListening.reach') }}:</span>
        <span class="font-semibold text-gray-900 dark:text-gray-100">{{ formattedReach }}</span>
      </div>
      <a
        v-if="mention.url"
        :href="mention.url"
        target="_blank"
        rel="noopener noreferrer"
        class="inline-flex items-center gap-1 text-xs font-medium text-primary-600 transition-colors hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
      >
        {{ $t('socialListening.viewOriginal') }}
        <ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" />
      </a>
    </div>
  </div>
</template>
