<script setup lang="ts">
import { computed } from 'vue'
import {
  UserGroupIcon,
  EyeIcon,
  VideoCameraIcon,
  ClockIcon,
  CurrencyDollarIcon,
  SparklesIcon,
  CalendarDaysIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'
import type { CreatorMilestone } from '@/types/creatorMilestone'
import MilestoneProgress from './MilestoneProgress.vue'
import AchievementBadge from './AchievementBadge.vue'

interface Props {
  milestone: CreatorMilestone
}

const props = defineProps<Props>()

const typeConfig = computed(() => {
  const configs: Record<string, { icon: typeof UserGroupIcon; label: string; color: string }> = {
    SUBSCRIBERS: {
      icon: UserGroupIcon,
      label: '구독자',
      color: 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30',
    },
    VIEWS: {
      icon: EyeIcon,
      label: '조회수',
      color: 'text-purple-600 dark:text-purple-400 bg-purple-100 dark:bg-purple-900/30',
    },
    VIDEOS: {
      icon: VideoCameraIcon,
      label: '영상 수',
      color: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30',
    },
    WATCH_HOURS: {
      icon: ClockIcon,
      label: '시청시간',
      color: 'text-orange-600 dark:text-orange-400 bg-orange-100 dark:bg-orange-900/30',
    },
    REVENUE: {
      icon: CurrencyDollarIcon,
      label: '수익',
      color: 'text-yellow-600 dark:text-yellow-400 bg-yellow-100 dark:bg-yellow-900/30',
    },
    CUSTOM: {
      icon: SparklesIcon,
      label: '사용자 정의',
      color: 'text-indigo-600 dark:text-indigo-400 bg-indigo-100 dark:bg-indigo-900/30',
    },
  }
  return configs[props.milestone.type] || configs.CUSTOM
})

const statusConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    PENDING: {
      label: '대기',
      color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
    },
    IN_PROGRESS: {
      label: '진행중',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    ACHIEVED: {
      label: '달성',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    EXPIRED: {
      label: '만료',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
  }
  return configs[props.milestone.status] || configs.PENDING
})

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.milestone.platform] || props.milestone.platform
})

const platformColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.milestone.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const formatNumber = (value: number): string => {
  return value.toLocaleString('ko-KR')
}

const formatDate = (dateStr: string): string => {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
</script>

<template>
  <div
    class="relative rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all duration-200 hover:shadow-lg dark:border-gray-700 dark:bg-gray-800 dark:hover:shadow-gray-900/50"
  >
    <!-- Header -->
    <div class="mb-4 flex items-start justify-between">
      <div class="flex items-start gap-3 flex-1 min-w-0">
        <div :class="['rounded-lg p-2', typeConfig.color]">
          <component :is="typeConfig.icon" class="h-5 w-5" />
        </div>
        <div class="flex-1 min-w-0">
          <h3 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ milestone.title }}
          </h3>
          <p class="mt-0.5 line-clamp-2 text-sm text-gray-500 dark:text-gray-400">
            {{ milestone.description }}
          </p>
        </div>
      </div>
    </div>

    <!-- Badges -->
    <div class="mb-4 flex flex-wrap items-center gap-2">
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', typeConfig.color]">
        {{ typeConfig.label }}
      </span>
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', platformColor]">
        {{ platformLabel }}
      </span>
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', statusConfig.color]">
        {{ statusConfig.label }}
      </span>
    </div>

    <!-- Progress Section -->
    <div class="mb-4 flex items-center gap-4">
      <MilestoneProgress :percentage="milestone.progress" size="md" />
      <div class="flex-1">
        <div class="mb-1 flex items-baseline justify-between">
          <span class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatNumber(milestone.currentValue) }}
          </span>
          <span class="text-sm text-gray-500 dark:text-gray-400">
            / {{ formatNumber(milestone.targetValue) }}
          </span>
        </div>
        <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            class="h-full rounded-full bg-gradient-to-r from-blue-500 to-purple-500 transition-all duration-500 dark:from-blue-400 dark:to-purple-400"
            :style="{ width: `${Math.min(milestone.progress, 100)}%` }"
          />
        </div>
      </div>
    </div>

    <!-- Dates -->
    <div class="flex items-center justify-between border-t border-gray-200 pt-3 text-sm dark:border-gray-700">
      <div class="flex items-center gap-1 text-gray-500 dark:text-gray-400">
        <CalendarDaysIcon class="h-4 w-4" />
        <span v-if="milestone.targetDate">목표: {{ formatDate(milestone.targetDate) }}</span>
        <span v-else>목표일 미설정</span>
      </div>
      <div v-if="milestone.achievedAt" class="flex items-center gap-1 text-green-600 dark:text-green-400">
        <CheckCircleIcon class="h-4 w-4" />
        <span>{{ formatDate(milestone.achievedAt) }}</span>
      </div>
    </div>

    <!-- Achievement Badge -->
    <div v-if="milestone.status === 'ACHIEVED' && milestone.achievedAt" class="mt-3">
      <AchievementBadge
        :title="milestone.title"
        :achieved-at="milestone.achievedAt"
        :type="milestone.type"
      />
    </div>
  </div>
</template>
