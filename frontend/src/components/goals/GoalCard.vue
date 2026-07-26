<script setup lang="ts">
import { computed } from 'vue'
import {
  PencilIcon,
  TrashIcon,
  PauseIcon,
  PlayIcon,
  UserGroupIcon,
  EyeIcon,
  CloudArrowUpIcon,
  CurrencyDollarIcon,
  HeartIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'
import type { Goal } from '@/types/goal'
import GoalProgress from './GoalProgress.vue'

interface Props {
  goal: Goal
}

interface Emits {
  (e: 'edit', goal: Goal): void
  (e: 'delete', goalId: number): void
  (e: 'pause', goalId: number): void
  (e: 'resume', goalId: number): void
  (e: 'view-details', goal: Goal): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const progressPercentage = computed(() => {
  return Math.min((props.goal.currentValue / props.goal.targetValue) * 100, 100)
})

const typeConfig = computed(() => {
  const configs = {
    subscribers: {
      icon: UserGroupIcon,
      label: '구독자',
      color: 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30',
    },
    views: {
      icon: EyeIcon,
      label: '조회수',
      color: 'text-purple-600 dark:text-purple-400 bg-purple-100 dark:bg-purple-900/30',
    },
    uploads: {
      icon: CloudArrowUpIcon,
      label: '업로드',
      color: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30',
    },
    revenue: {
      icon: CurrencyDollarIcon,
      label: '수익',
      color: 'text-yellow-600 dark:text-yellow-400 bg-yellow-100 dark:bg-yellow-900/30',
    },
    engagement: {
      icon: HeartIcon,
      label: '참여율',
      color: 'text-pink-600 dark:text-pink-400 bg-pink-100 dark:bg-pink-900/30',
    },
    custom: {
      icon: SparklesIcon,
      label: '사용자 정의',
      color: 'text-primary-600 dark:text-primary-400 bg-primary-100 dark:bg-primary-900/30',
    },
  }
  return configs[props.goal.type]
})

const statusConfig = computed(() => {
  const configs = {
    active: {
      label: '진행중',
      color: 'bg-success-subtle text-success-strong',
    },
    completed: {
      label: '완료',
      color: 'bg-warning-subtle text-warning-strong',
    },
    failed: {
      label: '실패',
      color: 'bg-error-subtle text-error-strong',
    },
    paused: {
      label: '일시정지',
      color: 'bg-muted-subtle text-muted-strong',
    },
  }
  return configs[props.goal.status]
})

const periodLabel = computed(() => {
  const labels = {
    weekly: '주간',
    monthly: '월간',
    quarterly: '분기',
    yearly: '연간',
    custom: '사용자 정의',
  }
  return labels[props.goal.period]
})

const daysLeft = computed(() => {
  const now = new Date()
  const end = new Date(props.goal.endDate)
  const diff = Math.ceil((end.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  return diff
})

const completedMilestones = computed(() => {
  return props.goal.milestones.filter(m => m.isCompleted).length
})

const formatNumber = (value: number): string => {
  if (props.goal.type === 'revenue') {
    return value.toLocaleString('ko-KR')
  }
  return value.toLocaleString('ko-KR')
}

const formatDate = (dateStr: string): string => {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}
</script>

<template>
  <div
    class="relative bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer group"
    @click="emit('view-details', goal)"
  >
    <!-- Header -->
    <div class="flex items-start justify-between mb-4">
      <div class="flex items-start gap-3 flex-1 min-w-0">
        <div :class="['p-2 rounded-lg', typeConfig.color]">
          <component :is="typeConfig.icon" class="w-5 h-5" />
        </div>
        <div class="flex-1 min-w-0">
          <h3 class="text-title font-semibold text-gray-900 dark:text-gray-100 truncate">
            {{ goal.title }}
          </h3>
          <p class="text-body text-gray-600 dark:text-gray-400 line-clamp-2 mt-1">
            {{ goal.description }}
          </p>
        </div>
      </div>

      <div class="flex items-center gap-2 ml-2">
        <span :class="['inline-flex items-center px-2 py-1 rounded text-caption', statusConfig.color]">
          {{ statusConfig.label }}
        </span>
      </div>
    </div>

    <!-- Progress Section -->
    <div class="flex items-center gap-6 mb-4">
      <GoalProgress :percentage="progressPercentage" size="lg" />

      <div class="flex-1">
        <div class="flex items-baseline justify-between mb-1">
          <span class="text-h1 font-bold text-gray-900 dark:text-gray-100">
            {{ formatNumber(goal.currentValue) }}
          </span>
          <span class="text-body text-gray-500 dark:text-gray-400">
            / {{ formatNumber(goal.targetValue) }} {{ goal.unit }}
          </span>
        </div>

        <!-- Progress bar -->
        <div class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
          <div
            class="h-full bg-primary-600 transition-all duration-500 rounded-full"
            :style="{ width: `${progressPercentage}%` }"
          ></div>
        </div>

        <!-- Milestones mini progress -->
        <div class="flex items-center gap-1 mt-2">
          <div
            v-for="milestone in goal.milestones"
            :key="milestone.id"
            :class="[
              'w-2 h-2 rounded-full transition-colors',
              milestone.isCompleted
                ? 'bg-success'
                : goal.currentValue >= milestone.targetValue
                ? 'bg-info animate-pulse'
                : 'bg-gray-300 dark:bg-gray-600'
            ]"
            :title="milestone.title"
          ></div>
          <span class="text-body-xs text-gray-500 dark:text-gray-400 ml-1">
            {{ completedMilestones }}/{{ goal.milestones.length }} 마일스톤
          </span>
        </div>
      </div>
    </div>

    <!-- Info Section -->
    <div class="flex items-center justify-between text-body border-t border-gray-200 dark:border-gray-700 pt-4">
      <div class="flex items-center gap-4">
        <span class="inline-flex items-center px-2 py-1 rounded bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300">
          {{ periodLabel }}
        </span>
        <span class="text-gray-600 dark:text-gray-400">
          {{ formatDate(goal.endDate) }} 마감
        </span>
        <span
          v-if="goal.status === 'active'"
          :class="[
            'font-medium',
            daysLeft <= 3 ? 'text-error-strong' :
            daysLeft <= 7 ? 'text-warning-strong' :
            'text-gray-600 dark:text-gray-400'
          ]"
        >
          D-{{ daysLeft }}
        </span>
      </div>
    </div>

    <!-- Action Buttons -->
    <div
      class="absolute top-4 right-4 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"
      @click.stop
    >
      <button
        v-if="goal.status === 'paused'"
        class="p-2 text-success-strong hover:bg-success-subtle rounded-lg transition-colors"
        title="재개"
        @click="emit('resume', goal.id)"
      >
        <PlayIcon class="w-4 h-4" />
      </button>
      <button
        v-else-if="goal.status === 'active'"
        class="p-2 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
        title="일시정지"
        @click="emit('pause', goal.id)"
      >
        <PauseIcon class="w-4 h-4" />
      </button>
      <button
        class="p-2 text-info-strong hover:bg-info-subtle rounded-lg transition-colors"
        title="수정"
        @click="emit('edit', goal)"
      >
        <PencilIcon class="w-4 h-4" />
      </button>
      <button
        class="p-2 text-error-strong hover:bg-error-subtle rounded-lg transition-colors"
        title="삭제"
        @click="emit('delete', goal.id)"
      >
        <TrashIcon class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
