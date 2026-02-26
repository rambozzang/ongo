<script setup lang="ts">
import { computed } from 'vue'
import {
  PencilIcon,
  TrashIcon,
  MagnifyingGlassIcon,
  MusicalNoteIcon,
  PhotoIcon,
  FilmIcon,
  SpeakerWaveIcon,
} from '@heroicons/vue/24/outline'
import type { ContentRight } from '@/types/contentRights'

interface Props {
  right: ContentRight
}

interface Emits {
  (e: 'edit', right: ContentRight): void
  (e: 'delete', id: number): void
  (e: 'find-alternatives', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const assetTypeConfig = computed(() => {
  const configs: Record<string, { icon: typeof MusicalNoteIcon; label: string; color: string }> = {
    MUSIC: { icon: MusicalNoteIcon, label: '음악', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' },
    IMAGE: { icon: PhotoIcon, label: '이미지', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' },
    FONT: {
      icon: MusicalNoteIcon, // We'll use a text-based badge instead
      label: '폰트',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
    VIDEO_CLIP: { icon: FilmIcon, label: '비디오 클립', color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
    SOUND_EFFECT: { icon: SpeakerWaveIcon, label: '효과음', color: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400' },
    OTHER: { icon: PhotoIcon, label: '기타', color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' },
  }
  return configs[props.right.assetType] ?? configs.OTHER
})

const licenseTypeColor = computed(() => {
  const map: Record<string, string> = {
    FREE: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    ROYALTY_FREE: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    CREATIVE_COMMONS: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    PAID: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
    SUBSCRIPTION: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
    CUSTOM: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
  }
  return map[props.right.licenseType] ?? map.CUSTOM
})

const licenseTypeLabel = computed(() => {
  const map: Record<string, string> = {
    FREE: '무료', ROYALTY_FREE: '로열티 프리', CREATIVE_COMMONS: 'CC',
    PAID: '유료', SUBSCRIPTION: '구독', CUSTOM: '기타',
  }
  return map[props.right.licenseType] ?? props.right.licenseType
})

const statusConfig = computed(() => {
  const map: Record<string, { color: string; label: string }> = {
    ACTIVE: { color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400', label: '활성' },
    EXPIRING_SOON: { color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400', label: '만료임박' },
    EXPIRED: { color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400', label: '만료' },
    UNKNOWN: { color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300', label: '미확인' },
  }
  return map[props.right.licenseStatus] ?? map.UNKNOWN
})

const daysRemaining = computed(() => {
  if (!props.right.expiresAt) return null
  const now = new Date()
  const expires = new Date(props.right.expiresAt)
  return Math.ceil((expires.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
})

const formattedExpiry = computed(() => {
  if (!props.right.expiresAt) return '만료일 없음'
  return new Date(props.right.expiresAt).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
})

const formattedCost = computed(() => {
  if (props.right.cost === 0) return '무료'
  return `${props.right.cost.toLocaleString()} ${props.right.currency}`
})
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-800">
    <!-- Header -->
    <div class="flex items-start justify-between mb-3">
      <div class="flex items-center gap-3 min-w-0">
        <div :class="['rounded-lg p-2', assetTypeConfig.color]">
          <component :is="assetTypeConfig.icon" class="w-5 h-5" />
        </div>
        <div class="min-w-0">
          <h3 class="font-semibold text-gray-900 dark:text-white truncate">
            {{ right.assetName }}
          </h3>
          <div class="flex items-center gap-2 mt-1 flex-wrap">
            <span
              :class="['inline-block px-2 py-0.5 text-xs font-medium rounded-full', assetTypeConfig.color]"
            >
              {{ assetTypeConfig.label }}
            </span>
            <span
              :class="['inline-block px-2 py-0.5 text-xs font-medium rounded-full', licenseTypeColor]"
            >
              {{ licenseTypeLabel }}
            </span>
          </div>
        </div>
      </div>

      <!-- Status badge -->
      <span
        :class="['inline-flex items-center shrink-0 px-2.5 py-0.5 text-xs font-medium rounded-full', statusConfig.color]"
      >
        {{ statusConfig.label }}
      </span>
    </div>

    <!-- Details -->
    <div class="space-y-2 mb-4">
      <div class="flex items-center justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-400">사용 영상</span>
        <span class="font-medium text-gray-900 dark:text-white truncate max-w-[180px]">
          {{ right.videoTitle }}
        </span>
      </div>
      <div class="flex items-center justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-400">출처</span>
        <span class="font-medium text-gray-900 dark:text-white">{{ right.source }}</span>
      </div>
      <div class="flex items-center justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-400">만료일</span>
        <span
          :class="[
            'font-medium',
            daysRemaining !== null && daysRemaining <= 0
              ? 'text-red-600 dark:text-red-400'
              : daysRemaining !== null && daysRemaining <= 30
                ? 'text-yellow-600 dark:text-yellow-400'
                : 'text-gray-900 dark:text-white',
          ]"
        >
          {{ formattedExpiry }}
          <span v-if="daysRemaining !== null" class="text-xs">
            ({{ daysRemaining > 0 ? `${daysRemaining}일 남음` : `${Math.abs(daysRemaining)}일 지남` }})
          </span>
        </span>
      </div>
      <div class="flex items-center justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-400">비용</span>
        <span class="font-medium text-gray-900 dark:text-white">{{ formattedCost }}</span>
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-end gap-1 border-t border-gray-200 dark:border-gray-700 pt-3">
      <button
        @click.stop="emit('find-alternatives', right.id)"
        class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
        title="대체 에셋 찾기"
      >
        <MagnifyingGlassIcon class="h-4 w-4" />
        대체 에셋
      </button>
      <button
        @click.stop="emit('edit', right)"
        class="rounded-lg p-1.5 text-blue-600 transition-colors hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20"
        title="수정"
      >
        <PencilIcon class="h-4 w-4" />
      </button>
      <button
        @click.stop="emit('delete', right.id)"
        class="rounded-lg p-1.5 text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
        title="삭제"
      >
        <TrashIcon class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>
