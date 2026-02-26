package com.ongo.domain.creatoracademy

import java.time.LocalDateTime

data class AcademyCourse(
    val id: Long? = null,
    val title: String,
    val description: String? = null,
    val category: String = "GROWTH",
    val level: String = "BEGINNER",
    val instructorName: String,
    val instructorAvatar: String? = null,
    val thumbnailUrl: String? = null,
    val totalLessons: Int = 0,
    val duration: Int = 0,
    val rating: Double = 0.0,
    val enrolledCount: Int = 0,
    val creditReward: Int = 0,
    val tags: String = "[]",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
