<script setup lang="ts">
import {
  FolderIcon,
  CheckCircleIcon,
  ClockIcon,
  XCircleIcon,
  CurrencyDollarIcon,
} from '@heroicons/vue/24/outline'
import type { ContentRight } from '@/types/contentRights'

interface Props {
  rights: ContentRight[]
}

const props = defineProps<Props>()

import { computed } from 'vue'

const totalAssets = computed(() => props.rights.length)

const activeCount = computed(() =>
  props.rights.filter((r) => r.licenseStatus === 'ACTIVE').length,
)

const expiringCount = computed(() =>
  props.rights.filter((r) => r.licenseStatus === 'EXPIRING_SOON').length,
)

const expiredCount = computed(() =>
  props.rights.filter((r) => r.licenseStatus === 'EXPIRED').length,
)

const totalCost = computed(() =>
  props.rights.reduce((sum, r) => sum + r.cost, 0),
)

function formatCost(cost: number): string {
  if (cost >= 10000) return `${(cost / 10000).toFixed(1)}만`
  return cost.toLocaleString()
}

// Type breakdown
const typeBreakdown = computed(() => {
  const map = new Map<string, number>()
  props.rights.forEach((r) => {
    map.set(r.assetType, (map.get(r.assetType) ?? 0) + 1)
  })
  const labels: Record<string, string> = {
    MUSIC: '음악', IMAGE: '이미지', FONT: '폰트',
    VIDEO_CLIP: '비디오', SOUND_EFFECT: '효과음', OTHER: '기타',
  }
  const colors: Record<string, string> = {
    MUSIC: 'bg-purple-500', IMAGE: 'bg-blue-500', FONT: 'bg-gray-500',
    VIDEO_CLIP: 'bg-red-500', SOUND_EFFECT: 'bg-amber-500', OTHER: 'bg-gray-400',
  }
  return Array.from(map.entries()).map(([type, count]) => ({
    type,
    label: labels[type] ?? type,
    count,
    color: colors[type] ?? 'bg-gray-400',
    percentage: totalAssets.value > 0 ? (count / totalAssets.value) * 100 : 0,
  }))
})

// License breakdown
const licenseBreakdown = computed(() => {
  const map = new Map<string, number>()
  props.rights.forEach((r) => {
    map.set(r.licenseType, (map.get(r.licenseType) ?? 0) + 1)
  })
  const labels: Record<string, string> = {
    FREE: '무료', ROYALTY_FREE: '로열티 프리', CREATIVE_COMMONS: 'CC',
    PAID: '유료', SUBSCRIPTION: '구독', CUSTOM: '기타',
  }
  const colors: Record<string, string> = {
    FREE: 'bg-green-500', ROYALTY_FREE: 'bg-blue-500', CREATIVE_COMMONS: 'bg-purple-500',
    PAID: 'bg-amber-500', SUBSCRIPTION: 'bg-indigo-500', CUSTOM: 'bg-gray-400',
  }
  return Array.from(map.entries()).map(([license, count]) => ({
    license,
    label: labels[license] ?? license,
    count,
    color: colors[license] ?? 'bg-gray-400',
    percentage: totalAssets.value > 0 ? (count / totalAssets.value) * 100 : 0,
  }))
})
</script>

<template>
  <div class="mb-6 space-y-4">
    <!-- Top stats row -->
    <div class="grid grid-cols-2 gap-4 tablet:grid-cols-5">
      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-blue-100 p-2 dark:bg-blue-900/30">
            <FolderIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">전체 에셋</p>
            <p class="text-xl font-bold text-gray-900 dark:text-white">{{ totalAssets }}</p>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-green-100 p-2 dark:bg-green-900/30">
            <CheckCircleIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">활성</p>
            <p class="text-xl font-bold text-green-600 dark:text-green-400">{{ activeCount }}</p>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-yellow-100 p-2 dark:bg-yellow-900/30">
            <ClockIcon class="h-5 w-5 text-yellow-600 dark:text-yellow-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">만료임박</p>
            <p class="text-xl font-bold text-yellow-600 dark:text-yellow-400">{{ expiringCount }}</p>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-red-100 p-2 dark:bg-red-900/30">
            <XCircleIcon class="h-5 w-5 text-red-600 dark:text-red-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">만료</p>
            <p class="text-xl font-bold text-red-600 dark:text-red-400">{{ expiredCount }}</p>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800 col-span-2 tablet:col-span-1">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-indigo-100 p-2 dark:bg-indigo-900/30">
            <CurrencyDollarIcon class="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">총 비용</p>
            <p class="text-xl font-bold text-gray-900 dark:text-white">{{ formatCost(totalCost) }}원</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Breakdown mini charts -->
    <div class="grid gap-4 tablet:grid-cols-2">
      <!-- Type breakdown -->
      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <h4 class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">유형별 분포</h4>
        <div v-if="typeBreakdown.length > 0" class="space-y-2">
          <div v-for="item in typeBreakdown" :key="item.type" class="flex items-center gap-3">
            <span class="w-16 text-xs text-gray-600 dark:text-gray-400 shrink-0">{{ item.label }}</span>
            <div class="flex-1 h-2 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                :class="[item.color, 'h-full rounded-full transition-all duration-500']"
                :style="{ width: `${item.percentage}%` }"
              />
            </div>
            <span class="text-xs font-medium text-gray-700 dark:text-gray-300 w-6 text-right">{{ item.count }}</span>
          </div>
        </div>
        <p v-else class="text-xs text-gray-400 dark:text-gray-500">데이터 없음</p>
      </div>

      <!-- License breakdown -->
      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <h4 class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">라이선스별 분포</h4>
        <div v-if="licenseBreakdown.length > 0" class="space-y-2">
          <div v-for="item in licenseBreakdown" :key="item.license" class="flex items-center gap-3">
            <span class="w-20 text-xs text-gray-600 dark:text-gray-400 shrink-0">{{ item.label }}</span>
            <div class="flex-1 h-2 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                :class="[item.color, 'h-full rounded-full transition-all duration-500']"
                :style="{ width: `${item.percentage}%` }"
              />
            </div>
            <span class="text-xs font-medium text-gray-700 dark:text-gray-300 w-6 text-right">{{ item.count }}</span>
          </div>
        </div>
        <p v-else class="text-xs text-gray-400 dark:text-gray-500">데이터 없음</p>
      </div>
    </div>
  </div>
</template>
