<script setup lang="ts">
import { computed } from 'vue'
import {
  AcademicCapIcon,
  CheckCircleIcon,
  SparklesIcon,
  FireIcon,
  ClockIcon,
  LightBulbIcon,
  ArrowTrendingUpIcon,
} from '@heroicons/vue/24/outline'
import type { LearningProgress } from '@/types/creatorAcademy'

interface Props {
  progress: LearningProgress
}

const props = defineProps<Props>()

const circumference = 2 * Math.PI * 40
const completionOffset = computed(() => {
  return circumference - (props.progress.completionRate / 100) * circumference
})

const formattedWatchTime = computed(() => {
  const hours = Math.floor(props.progress.totalWatchTime / 60)
  const mins = props.progress.totalWatchTime % 60
  if (hours === 0) return `${mins}분`
  return `${hours}시간 ${mins}분`
})

const motivationalMessage = computed(() => {
  const streak = props.progress.currentStreak
  if (streak >= 30) return '한 달 연속 학습! 당신은 진정한 학습 마스터입니다!'
  if (streak >= 14) return '2주 연속 학습 중! 놀라운 꾸준함이에요!'
  if (streak >= 7) return '일주일 연속 학습 달성! 대단해요!'
  if (streak >= 3) return '3일 연속 학습 중! 좋은 습관이 만들어지고 있어요!'
  if (streak >= 1) return '오늘도 학습을 시작했군요! 좋은 시작이에요!'
  return '오늘 학습을 시작해보세요! 작은 시작이 큰 변화를 만듭니다.'
})

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    month: 'short',
    day: 'numeric',
  })
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-6 shadow-sm">
    <!-- Stats Row -->
    <div class="grid grid-cols-2 tablet:grid-cols-4 gap-4 mb-6">
      <div class="flex items-center gap-3 p-3 rounded-lg bg-blue-50 dark:bg-blue-900/20">
        <div class="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/40">
          <AcademicCapIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
        </div>
        <div>
          <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ progress.totalCoursesEnrolled }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">수강 중</p>
        </div>
      </div>

      <div class="flex items-center gap-3 p-3 rounded-lg bg-green-50 dark:bg-green-900/20">
        <div class="p-2 rounded-lg bg-green-100 dark:bg-green-900/40">
          <CheckCircleIcon class="w-5 h-5 text-green-600 dark:text-green-400" />
        </div>
        <div>
          <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ progress.totalCompleted }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">수강 완료</p>
        </div>
      </div>

      <div class="flex items-center gap-3 p-3 rounded-lg bg-yellow-50 dark:bg-yellow-900/20">
        <div class="p-2 rounded-lg bg-yellow-100 dark:bg-yellow-900/40">
          <SparklesIcon class="w-5 h-5 text-yellow-600 dark:text-yellow-400" />
        </div>
        <div>
          <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ progress.totalCreditsEarned }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">획득 크레딧</p>
        </div>
      </div>

      <div class="flex items-center gap-3 p-3 rounded-lg bg-orange-50 dark:bg-orange-900/20">
        <div class="p-2 rounded-lg bg-orange-100 dark:bg-orange-900/40">
          <FireIcon class="w-5 h-5 text-orange-600 dark:text-orange-400" />
        </div>
        <div>
          <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ progress.currentStreak }}일</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">연속 학습</p>
        </div>
      </div>
    </div>

    <!-- Completion Rate + Watch Time + Motivational Message -->
    <div class="grid grid-cols-1 tablet:grid-cols-3 gap-6 mb-6">
      <!-- Circular Progress -->
      <div class="flex items-center gap-4">
        <div class="relative w-24 h-24 flex-shrink-0">
          <svg class="w-24 h-24 -rotate-90" viewBox="0 0 96 96">
            <circle
              cx="48" cy="48" r="40"
              fill="none"
              stroke="currentColor"
              stroke-width="6"
              class="text-gray-200 dark:text-gray-700"
            />
            <circle
              cx="48" cy="48" r="40"
              fill="none"
              stroke="currentColor"
              stroke-width="6"
              stroke-linecap="round"
              class="text-blue-500 dark:text-blue-400 transition-all duration-700"
              :stroke-dasharray="circumference"
              :stroke-dashoffset="completionOffset"
            />
          </svg>
          <div class="absolute inset-0 flex items-center justify-center">
            <span class="text-lg font-bold text-gray-900 dark:text-gray-100">
              {{ Math.round(progress.completionRate) }}%
            </span>
          </div>
        </div>
        <div>
          <p class="text-sm font-medium text-gray-900 dark:text-gray-100">수료율</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">전체 수강 기준</p>
        </div>
      </div>

      <!-- Total Watch Time -->
      <div class="flex items-center gap-4">
        <div class="p-3 rounded-lg bg-purple-50 dark:bg-purple-900/20">
          <ClockIcon class="w-8 h-8 text-purple-600 dark:text-purple-400" />
        </div>
        <div>
          <p class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ formattedWatchTime }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">총 학습 시간</p>
        </div>
      </div>

      <!-- Motivational Message -->
      <div class="flex items-start gap-3 p-3 rounded-lg bg-gradient-to-r from-primary-50 to-purple-50 dark:from-primary-900/20 dark:to-purple-900/20">
        <LightBulbIcon class="w-5 h-5 text-primary-600 dark:text-primary-400 flex-shrink-0 mt-0.5" />
        <p class="text-sm text-gray-700 dark:text-gray-300">{{ motivationalMessage }}</p>
      </div>
    </div>

    <!-- Recent Activity & Weak Areas -->
    <div class="grid grid-cols-1 tablet:grid-cols-2 gap-6">
      <!-- Recent Activity -->
      <div>
        <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">최근 학습 활동</h4>
        <div class="space-y-2">
          <div
            v-for="(activity, index) in progress.recentActivity.slice(0, 4)"
            :key="index"
            class="flex items-start gap-3 p-2 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
          >
            <div class="w-2 h-2 rounded-full bg-blue-500 dark:bg-blue-400 mt-1.5 flex-shrink-0" />
            <div class="flex-1 min-w-0">
              <p class="text-sm text-gray-900 dark:text-gray-100 truncate">{{ activity.lessonTitle }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">
                {{ activity.courseTitle }} &middot; {{ formatDate(activity.completedAt) }}
              </p>
            </div>
          </div>
          <p
            v-if="progress.recentActivity.length === 0"
            class="text-sm text-gray-400 dark:text-gray-500 text-center py-4"
          >
            아직 학습 활동이 없습니다
          </p>
        </div>
      </div>

      <!-- Weak Areas -->
      <div>
        <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">보완이 필요한 영역</h4>
        <div class="space-y-2">
          <div
            v-for="(area, index) in progress.weakAreas"
            :key="index"
            class="flex items-center gap-3 p-2 rounded-lg bg-orange-50 dark:bg-orange-900/10"
          >
            <ArrowTrendingUpIcon class="w-4 h-4 text-orange-600 dark:text-orange-400 flex-shrink-0" />
            <div class="flex-1 min-w-0">
              <p class="text-sm text-gray-900 dark:text-gray-100">{{ area.area }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">
                추천 강좌 {{ area.suggestedCourses.length }}개
              </p>
            </div>
          </div>
          <p
            v-if="progress.weakAreas.length === 0"
            class="text-sm text-gray-400 dark:text-gray-500 text-center py-4"
          >
            모든 영역이 고르게 발전하고 있어요!
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
