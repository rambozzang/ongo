import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Course,
  LearningPath,
  LearningProgress,
} from '@/types/creatorAcademy'

export const creatorAcademyApi = {
  getCourses: (category?: string) =>
    apiClient.get<ResData<Course[]>>('/academy/courses', { params: { category } }).then(unwrapResponse),

  getCourse: (id: number) =>
    apiClient.get<ResData<Course>>(`/academy/courses/${id}`).then(unwrapResponse),

  enroll: (courseId: number) =>
    apiClient.post<ResData<void>>('/academy/enroll', { courseId }).then(unwrapResponse),

  completeLesson: (courseId: number, lessonId: number) =>
    apiClient.post<ResData<void>>(`/academy/courses/${courseId}/lessons/${lessonId}/complete`).then(unwrapResponse),

  getLearningPaths: () =>
    apiClient.get<ResData<LearningPath[]>>('/academy/paths').then(unwrapResponse),

  getProgress: () =>
    apiClient.get<ResData<LearningProgress>>('/academy/progress').then(unwrapResponse),

  getRecommendations: () =>
    apiClient.get<ResData<Course[]>>('/academy/recommendations').then(unwrapResponse),
}
