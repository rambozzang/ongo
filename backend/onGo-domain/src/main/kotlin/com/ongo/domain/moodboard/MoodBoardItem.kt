package com.ongo.domain.moodboard

import java.time.LocalDateTime

data class MoodBoardItem(
    val id: Long = 0,
    val boardId: Long,
    val type: String,
    val title: String? = null,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val note: String? = null,
    val color: String? = null,
    val position: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
