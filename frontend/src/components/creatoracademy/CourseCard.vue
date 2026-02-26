<script setup lang="ts">
import { computed } from 'vue'
import {
  AcademicCapIcon,
  ClockIcon,
  StarIcon,
  UserGroupIcon,
  SparklesIcon,
  PlayIcon,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import type { Course, CourseCategory, CourseLevel } from '@/types/creatorAcademy'

interface Props {
  course: Course
}

interface Emits {
  (e: 'click', course: Course): void
  (e: 'enroll', courseId: number): void
  (e: 'continue', courseId: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const categoryConfig = computed(() => {
  const configs: Record<CourseCategory, { label: string; color: string; gradient: string }> = {
    FILMING: { label: '촬영', color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400', gradient: 'from-red-500 to-orange-500' },
    EDITING: { label: '편집', color: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400', gradient: 'from-pink-500 to-rose-500' },
    MARKETING: { label: '마케팅', color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400', gradient: 'from-blue-500 to-cyan-500' },
    AI_TOOLS: { label: 'AI 도구', color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400', gradient: 'from-purple-500 to-violet-500' },
    MONETIZATION: { label: '수익화', color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400', gradient: 'from-yellow-500 to-amber-500' },
    GROWTH: { label: '성장', color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400', gradient: 'from-green-500 to-emerald-500' },
    BRANDING: { label: '브랜딩', color: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-400', gradient: 'from-indigo-500 to-blue-500' },
    ANALYTICS: { label: '분석', color: 'bg-teal-100 text-teal-800 dark:bg-teal-900/30 dark:text-teal-400', gradient: 'from-teal-500 to-cyan-500' },
  }
  return configs[props.course.category]
})

const levelConfig = computed(() => {
  const configs: Record<CourseLevel, { label: string; color: string }> = {
    BEGINNER: { label: '입문', color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300' },
    INTERMEDIATE: { label: '중급', color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300' },
    ADVANCED: { label: '고급', color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300' },
  }
  return configs[props.course.level]
})

const progressPercent = computed(() => {
  if (props.course.totalLessons === 0) return 0
  return Math.min(Math.round((props.course.completedLessons / props.course.totalLessons) * 100), 100)
})

const isEnrolled = computed(() => props.course.completedLessons > 0)
const isCompleted = computed(() => props.course.completedLessons === props.course.totalLessons)

const ratingStars = computed(() => {
  const full = Math.floor(props.course.rating)
  const half = props.course.rating % 1 >= 0.5 ? 1 : 0
  const empty = 5 - full - half
  return { full, half, empty }
})

function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  if (hours === 0) return `${mins}분`
  if (mins === 0) return `${hours}시간`
  return `${hours}시간 ${mins}분`
}

function handleAction() {
  if (isEnrolled.value && !isCompleted.value) {
    emit('continue', props.course.id)
  } else {
    emit('enroll', props.course.id)
  }
}
</script>

<template>
  <div
    class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer group overflow-hidden"
    @click="emit('click', course)"
  >
    <!-- Thumbnail with gradient overlay -->
    <div class="relative h-40 overflow-hidden">
      <div
        :class="['w-full h-full bg-gradient-to-br', categoryConfig.gradient, 'flex items-center justify-center']"
      >
        <AcademicCapIcon class="w-16 h-16 text-white/50" />
      </div>
      <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />

      <!-- Category badge -->
      <span
        :class="['absolute top-2 left-2 inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', categoryConfig.color]"
      >
        {{ categoryConfig.label }}
      </span>

      <!-- Level badge -->
      <span
        :class="['absolute top-2 right-2 inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', levelConfig.color]"
      >
        {{ levelConfig.label }}
      </span>

      <!-- Credit reward -->
      <div
        v-if="course.creditReward > 0"
        class="absolute bottom-2 right-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-400/90 text-yellow-900"
      >
        <SparklesIcon class="w-3.5 h-3.5" />
        {{ course.creditReward }} 크레딧
      </div>

      <!-- Duration -->
      <div class="absolute bottom-2 left-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-black/60 text-white">
        <ClockIcon class="w-3.5 h-3.5" />
        {{ formatDuration(course.duration) }}
      </div>
    </div>

    <!-- Content -->
    <div class="p-4">
      <!-- Title & description -->
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate mb-1">
        {{ course.title }}
      </h3>
      <p class="text-sm text-gray-500 dark:text-gray-400 line-clamp-2 mb-3">
        {{ course.description }}
      </p>

      <!-- Instructor -->
      <div class="flex items-center gap-2 mb-3">
        <div class="w-6 h-6 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center">
          <span class="text-xs font-medium text-gray-600 dark:text-gray-400">
            {{ course.instructorName.charAt(0) }}
          </span>
        </div>
        <span class="text-sm text-gray-600 dark:text-gray-400">{{ course.instructorName }}</span>
      </div>

      <!-- Progress bar -->
      <div v-if="isEnrolled" class="mb-3">
        <div class="flex items-center justify-between text-xs mb-1">
          <span class="text-gray-500 dark:text-gray-400">진행률</span>
          <span class="text-gray-700 dark:text-gray-300 font-medium">
            {{ course.completedLessons }}/{{ course.totalLessons }} 레슨
          </span>
        </div>
        <div class="w-full h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
          <div
            :class="[
              'h-full rounded-full transition-all duration-500',
              isCompleted
                ? 'bg-green-500 dark:bg-green-400'
                : 'bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400',
            ]"
            :style="{ width: `${progressPercent}%` }"
          />
        </div>
      </div>

      <!-- Rating & enrolled count -->
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-1">
          <template v-for="_i in ratingStars.full" :key="'full-' + _i">
            <StarIconSolid class="w-4 h-4 text-yellow-400" />
          </template>
          <template v-for="_i in ratingStars.empty" :key="'empty-' + _i">
            <StarIcon class="w-4 h-4 text-gray-300 dark:text-gray-600" />
          </template>
          <span class="text-sm text-gray-600 dark:text-gray-400 ml-1">{{ course.rating }}</span>
        </div>
        <div class="flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400">
          <UserGroupIcon class="w-4 h-4" />
          {{ course.enrolledCount.toLocaleString('ko-KR') }}
        </div>
      </div>

      <!-- Tags -->
      <div v-if="course.tags.length > 0" class="flex flex-wrap gap-1 mb-3">
        <span
          v-for="tag in course.tags.slice(0, 3)"
          :key="tag"
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          #{{ tag }}
        </span>
        <span
          v-if="course.tags.length > 3"
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          +{{ course.tags.length - 3 }}
        </span>
      </div>

      <!-- Action button -->
      <button
        @click.stop="handleAction"
        :class="[
          'w-full inline-flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors',
          isCompleted
            ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300 cursor-default'
            : isEnrolled
              ? 'bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600'
              : 'bg-primary-600 text-white hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600',
        ]"
        :disabled="isCompleted"
      >
        <PlayIcon v-if="!isCompleted" class="w-4 h-4" />
        {{ isCompleted ? '수강 완료' : isEnrolled ? '이어서 학습' : '수강하기' }}
      </button>
    </div>
  </div>
</template>
