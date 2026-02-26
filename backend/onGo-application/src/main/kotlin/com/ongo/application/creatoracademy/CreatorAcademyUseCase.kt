package com.ongo.application.creatoracademy

import com.ongo.application.creatoracademy.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.creatoracademy.AcademyCourseRepository
import com.ongo.domain.creatoracademy.AcademyEnrollment
import com.ongo.domain.creatoracademy.AcademyEnrollmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatorAcademyUseCase(
    private val academyCourseRepository: AcademyCourseRepository,
    private val academyEnrollmentRepository: AcademyEnrollmentRepository,
) {

    fun getCourses(userId: Long, category: String?): List<CourseResponse> {
        val courses = if (category != null) {
            academyCourseRepository.findByCategory(category)
        } else {
            academyCourseRepository.findAll()
        }
        val enrollments = academyEnrollmentRepository.findByUserId(userId)
        return courses.map { course ->
            val enrollment = enrollments.find { it.courseId == course.id }
            CourseResponse(
                id = course.id!!,
                title = course.title,
                description = course.description,
                category = course.category,
                level = course.level,
                instructorName = course.instructorName,
                instructorAvatar = course.instructorAvatar,
                thumbnailUrl = course.thumbnailUrl,
                totalLessons = course.totalLessons,
                completedLessons = enrollment?.completedLessons ?: 0,
                duration = course.duration,
                rating = course.rating,
                enrolledCount = course.enrolledCount,
                creditReward = course.creditReward,
                tags = course.tags,
                createdAt = course.createdAt,
            )
        }
    }

    fun getCourse(userId: Long, courseId: Long): CourseResponse {
        val course = academyCourseRepository.findById(courseId)
            ?: throw NotFoundException("강좌", courseId)
        val enrollment = academyEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
        return CourseResponse(
            id = course.id!!,
            title = course.title,
            description = course.description,
            category = course.category,
            level = course.level,
            instructorName = course.instructorName,
            instructorAvatar = course.instructorAvatar,
            thumbnailUrl = course.thumbnailUrl,
            totalLessons = course.totalLessons,
            completedLessons = enrollment?.completedLessons ?: 0,
            duration = course.duration,
            rating = course.rating,
            enrolledCount = course.enrolledCount,
            creditReward = course.creditReward,
            tags = course.tags,
            createdAt = course.createdAt,
        )
    }

    @Transactional
    fun enroll(userId: Long, courseId: Long) {
        academyCourseRepository.findById(courseId) ?: throw NotFoundException("강좌", courseId)
        val existing = academyEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
        if (existing == null) {
            academyEnrollmentRepository.save(AcademyEnrollment(userId = userId, courseId = courseId))
        }
    }

    @Transactional
    fun completeLesson(userId: Long, courseId: Long, lessonId: Long) {
        val enrollment = academyEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
            ?: throw NotFoundException("수강 정보", courseId)
        val updated = enrollment.copy(
            completedLessons = enrollment.completedLessons + 1,
            lastLessonId = lessonId,
        )
        academyEnrollmentRepository.update(updated)
    }

    fun getProgress(userId: Long): LearningProgressResponse {
        val enrollments = academyEnrollmentRepository.findByUserId(userId)
        val completed = enrollments.count { it.completedAt != null }
        val totalEnrolled = enrollments.size
        return LearningProgressResponse(
            totalCoursesEnrolled = totalEnrolled,
            totalCompleted = completed,
            totalCreditsEarned = 0,
            completionRate = if (totalEnrolled > 0) (completed.toDouble() / totalEnrolled * 100) else 0.0,
            recentActivity = "[]",
            weakAreas = "[]",
        )
    }
}
