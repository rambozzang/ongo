<script setup lang="ts">
import { computed } from 'vue'
import {
  QueueListIcon,
  PlayIcon,
  EyeIcon,
} from '@heroicons/vue/24/outline'
import type { PlaylistSummary, PlaylistPlatform } from '@/types/playlistManager'

interface Props {
  summary: PlaylistSummary | null
}

const props = defineProps<Props>()

const platformColors: Record<PlaylistPlatform, { fill: string; label: string }> = {
  YOUTUBE: { fill: '#ef4444', label: 'YouTube' },
  TIKTOK: { fill: '#ec4899', label: 'TikTok' },
  INSTAGRAM: { fill: '#a855f7', label: 'Instagram' },
  NAVER_CLIP: { fill: '#22c55e', label: 'Naver Clip' },
}

function formatNumber(num: number): string {
  return num.toLocaleString('ko-KR')
}

/* ---- Donut chart calculations ---- */
const donutSize = 120
const donutStroke = 18
const donutRadius = (donutSize - donutStroke) / 2
const donutCircumference = 2 * Math.PI * donutRadius

const donutSegments = computed(() => {
  if (!props.summary || props.summary.platformBreakdown.length === 0) return []
  const total = props.summary.platformBreakdown.reduce((sum, p) => sum + p.count, 0)
  if (total === 0) return []

  let accumulated = 0
  return props.summary.platformBreakdown.map((p) => {
    const percent = p.count / total
    const dashLength = percent * donutCircumference
    const dashGap = donutCircumference - dashLength
    const offset = -(accumulated * donutCircumference) + donutCircumference * 0.25
    accumulated += percent
    return {
      platform: p.platform,
      count: p.count,
      percent: Math.round(percent * 100),
      dashArray: `${dashLength} ${dashGap}`,
      dashOffset: offset,
      color: platformColors[p.platform]?.fill ?? '#9ca3af',
      label: platformColors[p.platform]?.label ?? p.platform,
    }
  })
})
</script>

<template>
  <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
    <div class="grid grid-cols-1 tablet:grid-cols-4 gap-6 items-center">
      <!-- Stat cards -->
      <div class="flex flex-col items-center p-4 rounded-xl bg-gray-50 dark:bg-gray-800">
        <QueueListIcon class="w-6 h-6 text-blue-500 dark:text-blue-400 mb-2" />
        <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary ? formatNumber(summary.totalPlaylists) : '0' }}
        </p>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">총 재생목록</p>
      </div>

      <div class="flex flex-col items-center p-4 rounded-xl bg-gray-50 dark:bg-gray-800">
        <PlayIcon class="w-6 h-6 text-green-500 dark:text-green-400 mb-2" />
        <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary ? formatNumber(summary.totalVideos) : '0' }}
        </p>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">총 동영상</p>
      </div>

      <div class="flex flex-col items-center p-4 rounded-xl bg-gray-50 dark:bg-gray-800">
        <EyeIcon class="w-6 h-6 text-purple-500 dark:text-purple-400 mb-2" />
        <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary ? formatNumber(summary.totalViews) : '0' }}
        </p>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">총 조회수</p>
      </div>

      <!-- Donut Chart -->
      <div class="flex flex-col items-center">
        <div class="relative" :style="{ width: donutSize + 'px', height: donutSize + 'px' }">
          <svg :width="donutSize" :height="donutSize" class="transform -rotate-90">
            <!-- Background circle -->
            <circle
              :cx="donutSize / 2"
              :cy="donutSize / 2"
              :r="donutRadius"
              fill="none"
              :stroke-width="donutStroke"
              class="stroke-gray-200 dark:stroke-gray-700"
            />
            <!-- Segments -->
            <circle
              v-for="segment in donutSegments"
              :key="segment.platform"
              :cx="donutSize / 2"
              :cy="donutSize / 2"
              :r="donutRadius"
              fill="none"
              :stroke-width="donutStroke"
              :stroke="segment.color"
              :stroke-dasharray="segment.dashArray"
              :stroke-dashoffset="segment.dashOffset"
              stroke-linecap="butt"
              class="transition-all duration-500"
            />
          </svg>
          <!-- Center label -->
          <div class="absolute inset-0 flex items-center justify-center">
            <span class="text-xs font-semibold text-gray-700 dark:text-gray-300">
              플랫폼
            </span>
          </div>
        </div>

        <!-- Legend -->
        <div class="flex flex-wrap justify-center gap-x-3 gap-y-1 mt-3">
          <div
            v-for="segment in donutSegments"
            :key="'legend-' + segment.platform"
            class="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-400"
          >
            <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" :style="{ backgroundColor: segment.color }" />
            {{ segment.label }} ({{ segment.count }})
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
