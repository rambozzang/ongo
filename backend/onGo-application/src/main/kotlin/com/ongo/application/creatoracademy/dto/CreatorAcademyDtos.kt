package com.ongo.application.creatoracademy.dto

import java.time.LocalDateTime

data class CourseResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val category: String,
    val level: String,
    val instructorName: String,
    val instructorAvatar: String?,
    val thumbnailUrl: String?,
    val totalLessons: Int,
    val completedLessons: Int,
    val duration: Int,
    val rating: Double,
    val enrolledCount: Int,
    val creditReward: Int,
    val tags: String,
    val createdAt: LocalDateTime?,
)

data class LessonResponse(
    val id: Long,
    val courseId: Long,
    val orderNumber: Int,
    val title: String,
    val description: String?,
    val videoUrl: String?,
    val duration: Int,
    val resources: String,
)

data class EnrollRequest(
    val courseId: Long,
)

data class LearningProgressResponse(
    val totalCoursesEnrolled: Int,
    val totalCompleted: Int,
    val totalCreditsEarned: Int,
    val completionRate: Double,
    val recentActivity: String,
    val weakAreas: String,
)
