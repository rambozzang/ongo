<script setup lang="ts">
import {
  LightBulbIcon,
  SparklesIcon,
  PhotoIcon,
  ClockIcon,
  SpeakerWaveIcon,
  RocketLaunchIcon,
} from '@heroicons/vue/24/outline'
import type { SegmentInsight } from '@/types/audienceSegment'

interface Props {
  insight: SegmentInsight
  segmentName?: string
}

defineProps<Props>()
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800 shadow-sm">
    <!-- Header -->
    <div class="border-b border-gray-200 dark:border-gray-700 px-6 py-4">
      <div class="flex items-center gap-2">
        <SparklesIcon class="h-5 w-5 text-primary-600 dark:text-primary-400" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-white">
          AI 인사이트
        </h3>
      </div>
      <p v-if="segmentName" class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        "{{ segmentName }}" 세그먼트 분석 결과
      </p>
    </div>

    <div class="p-6 space-y-6">
      <!-- Content Recommendations -->
      <div>
        <div class="flex items-center gap-2 mb-3">
          <LightBulbIcon class="h-5 w-5 text-amber-500 dark:text-amber-400" />
          <h4 class="font-medium text-gray-900 dark:text-white">콘텐츠 추천</h4>
        </div>
        <div class="space-y-3">
          <div
            v-for="(rec, index) in insight.contentRecommendations"
            :key="index"
            class="rounded-lg bg-gray-50 dark:bg-gray-700/50 p-3"
          >
            <div class="flex items-center justify-between mb-1.5">
              <span class="text-sm font-medium text-gray-900 dark:text-white">
                {{ rec.category }}
              </span>
              <span class="text-xs font-semibold text-primary-600 dark:text-primary-400">
                {{ Math.round(rec.confidence * 100) }}%
              </span>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400 mb-2">
              {{ rec.reason }}
            </p>
            <!-- Confidence bar -->
            <div class="w-full h-1.5 bg-gray-200 dark:bg-gray-600 rounded-full overflow-hidden">
              <div
                class="h-full bg-primary-500 dark:bg-primary-400 rounded-full transition-all duration-500"
                :style="{ width: `${rec.confidence * 100}%` }"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Title Style -->
      <div>
        <div class="flex items-center gap-2 mb-2">
          <svg
            class="h-5 w-5 text-blue-500 dark:text-blue-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.5"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M7.5 8.25h9m-9 3H12m-9.75 1.51c0 1.6 1.123 2.994 2.707 3.227 1.129.166 2.27.293 3.423.379.35.026.67.21.865.501L12 21l2.755-4.133a1.14 1.14 0 01.865-.501 48.172 48.172 0 003.423-.379c1.584-.233 2.707-1.626 2.707-3.228V6.741c0-1.602-1.123-2.995-2.707-3.228A48.394 48.394 0 0012 3c-2.392 0-4.744.175-7.043.513C3.373 3.746 2.25 5.14 2.25 6.741v6.018z"
            />
          </svg>
          <h4 class="font-medium text-gray-900 dark:text-white">제목 스타일</h4>
        </div>
        <p class="rounded-lg bg-blue-50 dark:bg-blue-900/20 px-4 py-3 text-sm text-blue-800 dark:text-blue-300">
          {{ insight.titleStyle }}
        </p>
      </div>

      <!-- Thumbnail Style -->
      <div>
        <div class="flex items-center gap-2 mb-2">
          <PhotoIcon class="h-5 w-5 text-pink-500 dark:text-pink-400" />
          <h4 class="font-medium text-gray-900 dark:text-white">썸네일 스타일</h4>
        </div>
        <p class="rounded-lg bg-pink-50 dark:bg-pink-900/20 px-4 py-3 text-sm text-pink-800 dark:text-pink-300">
          {{ insight.thumbnailStyle }}
        </p>
      </div>

      <!-- Optimal Video Length -->
      <div>
        <div class="flex items-center gap-2 mb-2">
          <ClockIcon class="h-5 w-5 text-green-500 dark:text-green-400" />
          <h4 class="font-medium text-gray-900 dark:text-white">최적 영상 길이</h4>
        </div>
        <div class="rounded-lg bg-green-50 dark:bg-green-900/20 px-4 py-3">
          <span class="text-2xl font-bold text-green-700 dark:text-green-300">
            {{ insight.optimalLength.min }}-{{ insight.optimalLength.max }}
          </span>
          <span class="ml-1 text-sm text-green-600 dark:text-green-400">
            {{ insight.optimalLength.unit === 'minutes' ? '분' : insight.optimalLength.unit }}
          </span>
        </div>
      </div>

      <!-- Tone Recommendation -->
      <div>
        <div class="flex items-center gap-2 mb-2">
          <SpeakerWaveIcon class="h-5 w-5 text-purple-500 dark:text-purple-400" />
          <h4 class="font-medium text-gray-900 dark:text-white">톤 추천</h4>
        </div>
        <p class="rounded-lg bg-purple-50 dark:bg-purple-900/20 px-4 py-3 text-sm text-purple-800 dark:text-purple-300">
          {{ insight.toneRecommendation }}
        </p>
      </div>

      <!-- Growth Opportunities -->
      <div>
        <div class="flex items-center gap-2 mb-3">
          <RocketLaunchIcon class="h-5 w-5 text-orange-500 dark:text-orange-400" />
          <h4 class="font-medium text-gray-900 dark:text-white">성장 기회</h4>
        </div>
        <ul class="space-y-2">
          <li
            v-for="(opportunity, index) in insight.growthOpportunities"
            :key="index"
            class="flex items-start gap-2 rounded-lg bg-orange-50 dark:bg-orange-900/20 px-4 py-2.5"
          >
            <span class="mt-1.5 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-orange-400 dark:bg-orange-300" />
            <span class="text-sm text-orange-800 dark:text-orange-300">
              {{ opportunity }}
            </span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
