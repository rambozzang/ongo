import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { creatorAcademyApi } from '@/api/creatorAcademy'
import type { Course, LearningPath, LearningProgress } from '@/types/creatorAcademy'

function generateMockCourses(): Course[] {
  return [
    { id: 1, title: '유튜브 성장 전략 마스터', description: '구독자 1만에서 10만까지 가는 실전 전략', category: 'GROWTH', level: 'INTERMEDIATE', instructorName: '김성장', thumbnailUrl: '', totalLessons: 12, completedLessons: 5, duration: 180, rating: 4.8, enrolledCount: 1250, creditReward: 10, tags: ['유튜브', '성장', '전략'], lessons: [], createdAt: '2025-01-01' },
    { id: 2, title: 'AI 도구 활용 완벽 가이드', description: 'onGo의 AI 기능을 100% 활용하는 방법', category: 'AI_TOOLS', level: 'BEGINNER', instructorName: '이AI', thumbnailUrl: '', totalLessons: 8, completedLessons: 8, duration: 120, rating: 4.9, enrolledCount: 2100, creditReward: 5, tags: ['AI', '자동화', '효율'], lessons: [], createdAt: '2025-01-15' },
    { id: 3, title: '숏폼 콘텐츠 편집 기초', description: 'TikTok, Reels, Shorts 편집의 핵심 기술', category: 'EDITING', level: 'BEGINNER', instructorName: '박편집', thumbnailUrl: '', totalLessons: 10, completedLessons: 0, duration: 150, rating: 4.7, enrolledCount: 890, creditReward: 8, tags: ['편집', '숏폼', 'TikTok'], lessons: [], createdAt: '2025-02-01' },
    { id: 4, title: '브랜드 딜 협상 고급 과정', description: '높은 단가의 브랜드 딜을 따내는 협상 전략', category: 'MONETIZATION', level: 'ADVANCED', instructorName: '최수익', thumbnailUrl: '', totalLessons: 6, completedLessons: 2, duration: 90, rating: 4.6, enrolledCount: 560, creditReward: 15, tags: ['수익화', '브랜드딜', '협상'], lessons: [], createdAt: '2025-02-10' },
    { id: 5, title: '채널 분석 데이터 읽기', description: '유튜브 애널리틱스의 모든 지표를 이해하기', category: 'ANALYTICS', level: 'INTERMEDIATE', instructorName: '정분석', thumbnailUrl: '', totalLessons: 8, completedLessons: 3, duration: 100, rating: 4.5, enrolledCount: 720, creditReward: 8, tags: ['분석', '데이터', '최적화'], lessons: [], createdAt: '2025-02-15' },
  ]
}

function generateMockProgress(): LearningProgress {
  return {
    totalCoursesEnrolled: 4, totalCompleted: 1, totalCreditsEarned: 23, currentStreak: 5,
    totalWatchTime: 480, completionRate: 42.5,
    recentActivity: [
      { courseId: 1, courseTitle: '유튜브 성장 전략 마스터', lessonTitle: '알고리즘의 비밀', completedAt: '2025-02-25' },
      { courseId: 4, courseTitle: '브랜드 딜 협상 고급 과정', lessonTitle: '미디어킷 작성법', completedAt: '2025-02-24' },
    ],
    weakAreas: [
      { area: '편집 기술', suggestedCourses: [3] },
      { area: 'SEO 최적화', suggestedCourses: [5] },
    ],
  }
}

export const useCreatorAcademyStore = defineStore('creatorAcademy', () => {
  const courses = ref<Course[]>([])
  const learningPaths = ref<LearningPath[]>([])
  const progress = ref<LearningProgress | null>(null)
  const recommendations = ref<Course[]>([])
  const isLoading = ref(false)

  const enrolledCourses = computed(() => courses.value.filter((c) => c.completedLessons > 0))
  const completedCourses = computed(() => courses.value.filter((c) => c.completedLessons === c.totalLessons))

  async function fetchCourses(category?: string) {
    isLoading.value = true
    try {
      courses.value = await creatorAcademyApi.getCourses(category)
    } catch {
      courses.value = generateMockCourses()
      if (category) courses.value = courses.value.filter((c) => c.category === category)
    } finally {
      isLoading.value = false
    }
  }

  async function fetchProgress() {
    try {
      progress.value = await creatorAcademyApi.getProgress()
    } catch {
      progress.value = generateMockProgress()
    }
  }

  async function enroll(courseId: number) {
    try { await creatorAcademyApi.enroll(courseId) } catch { /* fallback */ }
    const course = courses.value.find((c) => c.id === courseId)
    if (course) course.enrolledCount++
  }

  async function completeLesson(courseId: number, lessonId: number) {
    try { await creatorAcademyApi.completeLesson(courseId, lessonId) } catch { /* fallback */ }
    const course = courses.value.find((c) => c.id === courseId)
    if (course && course.completedLessons < course.totalLessons) course.completedLessons++
  }

  return { courses, learningPaths, progress, recommendations, isLoading, enrolledCourses, completedCourses, fetchCourses, fetchProgress, enroll, completeLesson }
})
