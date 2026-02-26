<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  AcademicCapIcon,
  XMarkIcon,
  PlayCircleIcon,
  ClockIcon,
  UserGroupIcon,
  SparklesIcon,
  BookOpenIcon,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import CourseCard from '@/components/creatoracademy/CourseCard.vue'
import ProgressDashboard from '@/components/creatoracademy/ProgressDashboard.vue'
import CategoryFilter from '@/components/creatoracademy/CategoryFilter.vue'
import { useCreatorAcademyStore } from '@/stores/creatorAcademy'
import type { Course, CourseCategory, CourseLevel } from '@/types/creatorAcademy'

const store = useCreatorAcademyStore()

const selectedCategory = ref<string>('ALL')
const activeTab = ref<'all' | 'enrolled'>('all')
const showDetailModal = ref(false)
const selectedCourse = ref<Course | null>(null)

onMounted(async () => {
  await Promise.all([store.fetchCourses(), store.fetchProgress()])
})

const filteredCourses = computed(() => {
  let courses = activeTab.value === 'enrolled'
    ? store.enrolledCourses
    : store.courses

  if (selectedCategory.value !== 'ALL') {
    courses = courses.filter((c) => c.category === selectedCategory.value)
  }

  return courses
})

function handleCategorySelect(category: string) {
  selectedCategory.value = category
  if (category === 'ALL') {
    store.fetchCourses()
  } else {
    store.fetchCourses(category)
  }
}

function handleCourseClick(course: Course) {
  selectedCourse.value = course
  showDetailModal.value = true
}

function handleEnroll(courseId: number) {
  store.enroll(courseId)
}

function handleContinue(courseId: number) {
  const course = store.courses.find((c) => c.id === courseId)
  if (course) {
    selectedCourse.value = course
    showDetailModal.value = true
  }
}

function handleModalEnroll() {
  if (selectedCourse.value) {
    store.enroll(selectedCourse.value.id)
  }
}

const levelConfig: Record<CourseLevel, { label: string; color: string }> = {
  BEGINNER: { label: '입문', color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300' },
  INTERMEDIATE: { label: '중급', color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300' },
  ADVANCED: { label: '고급', color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300' },
}

const categoryLabels: Record<CourseCategory, string> = {
  FILMING: '촬영',
  EDITING: '편집',
  MARKETING: '마케팅',
  AI_TOOLS: 'AI 도구',
  MONETIZATION: '수익화',
  GROWTH: '성장',
  BRANDING: '브랜딩',
  ANALYTICS: '분석',
}

function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  if (hours === 0) return `${mins}분`
  if (mins === 0) return `${hours}시간`
  return `${hours}시간 ${mins}분`
}

const detailProgressPercent = computed(() => {
  if (!selectedCourse.value) return 0
  if (selectedCourse.value.totalLessons === 0) return 0
  return Math.min(Math.round((selectedCourse.value.completedLessons / selectedCourse.value.totalLessons) * 100), 100)
})

const isDetailEnrolled = computed(() => {
  if (!selectedCourse.value) return false
  return selectedCourse.value.completedLessons > 0
})

const isDetailCompleted = computed(() => {
  if (!selectedCourse.value) return false
  return selectedCourse.value.completedLessons === selectedCourse.value.totalLessons
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <div class="p-2 rounded-lg bg-primary-100 dark:bg-primary-900/30">
            <AcademicCapIcon class="w-7 h-7 text-primary-600 dark:text-primary-400" />
          </div>
          <div>
            <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
              크리에이터 아카데미
            </h1>
            <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
              성장하는 크리에이터를 위한 맞춤형 학습 플랫폼
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="store.isLoading" :full-page="true" size="lg" />

    <div v-else class="space-y-6">
      <!-- Progress Dashboard -->
      <ProgressDashboard
        v-if="store.progress"
        :progress="store.progress"
      />

      <!-- Tab Switcher -->
      <div class="flex items-center gap-4 border-b border-gray-200 dark:border-gray-700">
        <button
          @click="activeTab = 'all'"
          :class="[
            'pb-3 px-1 text-sm font-medium border-b-2 transition-colors',
            activeTab === 'all'
              ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300',
          ]"
        >
          전체 강좌
          <span class="ml-1.5 inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400">
            {{ store.courses.length }}
          </span>
        </button>
        <button
          @click="activeTab = 'enrolled'"
          :class="[
            'pb-3 px-1 text-sm font-medium border-b-2 transition-colors',
            activeTab === 'enrolled'
              ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300',
          ]"
        >
          내 학습
          <span class="ml-1.5 inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400">
            {{ store.enrolledCourses.length }}
          </span>
        </button>
      </div>

      <!-- Category Filter -->
      <CategoryFilter
        :selected="selectedCategory"
        @select="handleCategorySelect"
      />

      <!-- Course Grid -->
      <div
        v-if="filteredCourses.length > 0"
        class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-6"
      >
        <CourseCard
          v-for="course in filteredCourses"
          :key="course.id"
          :course="course"
          @click="handleCourseClick"
          @enroll="handleEnroll"
          @continue="handleContinue"
        />
      </div>

      <!-- Empty State -->
      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
      >
        <BookOpenIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
          {{ activeTab === 'enrolled' ? '수강 중인 강좌가 없습니다' : '해당 카테고리에 강좌가 없습니다' }}
        </h3>
        <p class="text-sm text-gray-600 dark:text-gray-400">
          {{ activeTab === 'enrolled' ? '관심 있는 강좌를 찾아 수강을 시작해보세요!' : '다른 카테고리를 선택해보세요.' }}
        </p>
      </div>
    </div>

    <!-- ============ Course Detail Modal ============ -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showDetailModal && selectedCourse"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showDetailModal = false"
        >
          <Transition
            enter-active-class="transition-all duration-200"
            enter-from-class="opacity-0 scale-95"
            enter-to-class="opacity-100 scale-100"
            leave-active-class="transition-all duration-200"
            leave-from-class="opacity-100 scale-100"
            leave-to-class="opacity-0 scale-95"
          >
            <div
              v-if="showDetailModal && selectedCourse"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Modal Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                <div class="flex items-start justify-between">
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2 mb-2">
                      <span
                        :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', levelConfig[selectedCourse.level].color]"
                      >
                        {{ levelConfig[selectedCourse.level].label }}
                      </span>
                      <span class="text-xs text-gray-500 dark:text-gray-400">
                        {{ categoryLabels[selectedCourse.category] }}
                      </span>
                    </div>
                    <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
                      {{ selectedCourse.title }}
                    </h2>
                    <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      {{ selectedCourse.description }}
                    </p>
                  </div>
                  <button
                    @click="showDetailModal = false"
                    class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors ml-4"
                  >
                    <XMarkIcon class="w-5 h-5" />
                  </button>
                </div>
              </div>

              <!-- Modal Body -->
              <div class="flex-1 overflow-y-auto px-6 py-4 space-y-6">
                <!-- Course stats -->
                <div class="grid grid-cols-2 tablet:grid-cols-4 gap-3">
                  <div class="text-center p-3 rounded-lg bg-gray-50 dark:bg-gray-800">
                    <ClockIcon class="w-5 h-5 text-gray-400 mx-auto mb-1" />
                    <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatDuration(selectedCourse.duration) }}</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">총 시간</p>
                  </div>
                  <div class="text-center p-3 rounded-lg bg-gray-50 dark:bg-gray-800">
                    <PlayCircleIcon class="w-5 h-5 text-gray-400 mx-auto mb-1" />
                    <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ selectedCourse.totalLessons }}개</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">레슨</p>
                  </div>
                  <div class="text-center p-3 rounded-lg bg-gray-50 dark:bg-gray-800">
                    <UserGroupIcon class="w-5 h-5 text-gray-400 mx-auto mb-1" />
                    <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ selectedCourse.enrolledCount.toLocaleString('ko-KR') }}</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">수강생</p>
                  </div>
                  <div class="text-center p-3 rounded-lg bg-gray-50 dark:bg-gray-800">
                    <SparklesIcon class="w-5 h-5 text-yellow-400 mx-auto mb-1" />
                    <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ selectedCourse.creditReward }}</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">크레딧 보상</p>
                  </div>
                </div>

                <!-- Instructor -->
                <div class="flex items-center gap-3 p-3 rounded-lg bg-gray-50 dark:bg-gray-800">
                  <div class="w-10 h-10 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center">
                    <span class="text-sm font-medium text-gray-600 dark:text-gray-400">
                      {{ selectedCourse.instructorName.charAt(0) }}
                    </span>
                  </div>
                  <div>
                    <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ selectedCourse.instructorName }}</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">강사</p>
                  </div>
                  <div class="ml-auto flex items-center gap-1">
                    <StarIconSolid class="w-4 h-4 text-yellow-400" />
                    <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ selectedCourse.rating }}</span>
                  </div>
                </div>

                <!-- Progress (if enrolled) -->
                <div v-if="isDetailEnrolled" class="space-y-2">
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-gray-700 dark:text-gray-300 font-medium">학습 진행률</span>
                    <span class="text-gray-500 dark:text-gray-400">
                      {{ selectedCourse.completedLessons }}/{{ selectedCourse.totalLessons }} 레슨 완료
                    </span>
                  </div>
                  <div class="w-full h-2.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                    <div
                      :class="[
                        'h-full rounded-full transition-all duration-500',
                        isDetailCompleted
                          ? 'bg-green-500 dark:bg-green-400'
                          : 'bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400',
                      ]"
                      :style="{ width: `${detailProgressPercent}%` }"
                    />
                  </div>
                </div>

                <!-- Lessons list -->
                <div>
                  <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">
                    커리큘럼 ({{ selectedCourse.totalLessons }}개 레슨)
                  </h3>
                  <div v-if="selectedCourse.lessons.length > 0" class="space-y-2">
                    <div
                      v-for="lesson in selectedCourse.lessons"
                      :key="lesson.id"
                      class="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                    >
                      <div
                        :class="[
                          'w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 text-sm font-medium',
                          lesson.status === 'COMPLETED'
                            ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
                            : lesson.status === 'IN_PROGRESS'
                              ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                              : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400',
                        ]"
                      >
                        {{ lesson.orderNumber }}
                      </div>
                      <div class="flex-1 min-w-0">
                        <p class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                          {{ lesson.title }}
                        </p>
                        <p class="text-xs text-gray-500 dark:text-gray-400">{{ formatDuration(lesson.duration) }}</p>
                      </div>
                      <span
                        v-if="lesson.status === 'COMPLETED'"
                        class="text-xs text-green-600 dark:text-green-400 font-medium"
                      >
                        완료
                      </span>
                      <span
                        v-else-if="lesson.status === 'IN_PROGRESS'"
                        class="text-xs text-blue-600 dark:text-blue-400 font-medium"
                      >
                        학습 중
                      </span>
                    </div>
                  </div>
                  <p v-else class="text-sm text-gray-400 dark:text-gray-500 text-center py-6">
                    커리큘럼 상세 정보가 준비 중입니다
                  </p>
                </div>

                <!-- Tags -->
                <div v-if="selectedCourse.tags.length > 0">
                  <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">태그</h3>
                  <div class="flex flex-wrap gap-1.5">
                    <span
                      v-for="tag in selectedCourse.tags"
                      :key="tag"
                      class="inline-flex items-center px-2.5 py-1 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                    >
                      #{{ tag }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- Modal Footer -->
              <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
                <button
                  @click="showDetailModal = false"
                  class="btn-secondary"
                >
                  닫기
                </button>
                <button
                  v-if="!isDetailCompleted"
                  @click="handleModalEnroll"
                  class="btn-primary inline-flex items-center gap-2"
                >
                  <PlayCircleIcon class="w-5 h-5" />
                  {{ isDetailEnrolled ? '이어서 학습' : '수강 시작' }}
                </button>
                <span
                  v-else
                  class="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-green-700 dark:text-green-300 bg-green-100 dark:bg-green-900/30 rounded-lg"
                >
                  수강 완료
                </span>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
