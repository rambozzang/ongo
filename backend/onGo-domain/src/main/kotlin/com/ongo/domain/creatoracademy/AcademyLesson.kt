package com.ongo.domain.creatoracademy

import java.time.LocalDateTime

data class AcademyLesson(
    val id: Long? = null,
    val courseId: Long,
    val orderNumber: Int = 1,
    val title: String,
    val description: String? = null,
    val videoUrl: String? = null,
    val duration: Int = 0,
    val resources: String = "[]",
    val createdAt: LocalDateTime? = null,
)
