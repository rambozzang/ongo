<template>
  <div v-if="history.length > 0 || loading" class="card p-5">
    <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('contentStudio.history.title') }}
    </h3>

    <!-- 로딩 -->
    <div v-if="loading" class="space-y-3">
      <div v-for="i in 3" :key="i" class="animate-pulse">
        <div class="flex items-center gap-3">
          <div class="h-8 w-8 rounded-lg bg-gray-200 dark:bg-gray-700" />
          <div class="flex-1 space-y-1">
            <div class="h-3 w-1/3 rounded bg-gray-200 dark:bg-gray-700" />
            <div class="h-2.5 w-2/3 rounded bg-gray-200 dark:bg-gray-700" />
          </div>
        </div>
      </div>
    </div>

    <!-- 히스토리 목록 -->
    <div v-else class="divide-y divide-gray-100 dark:divide-gray-700">
      <div
        v-for="item in history"
        :key="item.id"
        class="flex items-start gap-3 py-3 first:pt-0 last:pb-0"
      >
        <div
          class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg"
          :class="typeIconBg(item.type)"
        >
          <component :is="typeIcon(item.type)" class="h-4 w-4" :class="typeIconColor(item.type)" />
        </div>
        <div class="min-w-0 flex-1">
          <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ item.videoTitle }}</p>
          <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{{ item.description }}</p>
          <div class="mt-1 flex items-center gap-2 text-xs text-gray-400 dark:text-gray-500">
            <span>{{ formatDate(item.createdAt) }}</span>
            <span>·</span>
            <span>{{ item.creditsUsed }} {{ $t('contentStudio.credits') }}</span>
          </div>
        </div>
        <span
          class="shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
          :class="typeBadge(item.type)"
        >
          {{ typeLabel(item.type) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { ScissorsIcon, LanguageIcon, PhotoIcon } from '@heroicons/vue/24/outline'
import type { ContentStudioHistory } from '@/types/contentStudio'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ko'

dayjs.extend(relativeTime)
dayjs.locale('ko')

const { t } = useI18n({ useScope: 'global' })

defineProps<{
  history: ContentStudioHistory[]
  loading: boolean
}>()

function typeIcon(type: string): Component {
  switch (type) {
    case 'CLIP': return ScissorsIcon
    case 'CAPTION': return LanguageIcon
    case 'THUMBNAIL': return PhotoIcon
    default: return ScissorsIcon
  }
}

function typeIconBg(type: string): string {
  switch (type) {
    case 'CLIP': return 'bg-blue-100 dark:bg-blue-900/30'
    case 'CAPTION': return 'bg-purple-100 dark:bg-purple-900/30'
    case 'THUMBNAIL': return 'bg-amber-100 dark:bg-amber-900/30'
    default: return 'bg-gray-100 dark:bg-gray-800'
  }
}

function typeIconColor(type: string): string {
  switch (type) {
    case 'CLIP': return 'text-blue-600 dark:text-blue-400'
    case 'CAPTION': return 'text-purple-600 dark:text-purple-400'
    case 'THUMBNAIL': return 'text-amber-600 dark:text-amber-400'
    default: return 'text-gray-500'
  }
}

function typeBadge(type: string): string {
  switch (type) {
    case 'CLIP': return 'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-400'
    case 'CAPTION': return 'bg-purple-50 dark:bg-purple-900/20 text-purple-700 dark:text-purple-400'
    case 'THUMBNAIL': return 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400'
    default: return 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-400'
  }
}

function typeLabel(type: string): string {
  switch (type) {
    case 'CLIP': return t('contentStudio.history.typeClip')
    case 'CAPTION': return t('contentStudio.history.typeCaption')
    case 'THUMBNAIL': return t('contentStudio.history.typeThumbnail')
    default: return type
  }
}

function formatDate(date: string): string {
  return dayjs(date).fromNow()
}
</script>
