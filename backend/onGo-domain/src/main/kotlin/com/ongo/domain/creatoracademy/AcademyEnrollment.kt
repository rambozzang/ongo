package com.ongo.domain.creatoracademy

import java.time.LocalDateTime

data class AcademyEnrollment(
    val id: Long? = null,
    val userId: Long,
    val courseId: Long,
    val completedLessons: Int = 0,
    val lastLessonId: Long? = null,
    val enrolledAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
)
