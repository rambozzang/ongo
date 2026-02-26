export type CourseLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type CourseCategory = 'FILMING' | 'EDITING' | 'MARKETING' | 'AI_TOOLS' | 'MONETIZATION' | 'GROWTH' | 'BRANDING' | 'ANALYTICS'
export type LessonStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'

export interface Course {
  id: number
  title: string
  description: string
  category: CourseCategory
  level: CourseLevel
  instructorName: string
  instructorAvatar?: string
  thumbnailUrl?: string
  totalLessons: number
  completedLessons: number
  duration: number
  rating: number
  enrolledCount: number
  creditReward: number
  tags: string[]
  lessons: Lesson[]
  createdAt: string
}

export interface Lesson {
  id: number
  courseId: number
  orderNumber: number
  title: string
  description: string
  videoUrl?: string
  duration: number
  status: LessonStatus
  resources: { name: string; url: string; type: string }[]
}

export interface LearningPath {
  id: number
  title: string
  description: string
  courses: Course[]
  totalDuration: number
  completionRate: number
  recommendedFor: string
}

export interface LearningProgress {
  totalCoursesEnrolled: number
  totalCompleted: number
  totalCreditsEarned: number
  currentStreak: number
  totalWatchTime: number
  completionRate: number
  recentActivity: { courseId: number; courseTitle: string; lessonTitle: string; completedAt: string }[]
  weakAreas: { area: string; suggestedCourses: number[] }[]
}

export interface EnrollRequest {
  courseId: number
}
