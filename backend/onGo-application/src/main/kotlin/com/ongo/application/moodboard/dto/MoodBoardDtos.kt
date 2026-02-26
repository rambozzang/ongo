package com.ongo.application.moodboard.dto

data class MoodBoardResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val category: String?,
    val itemCount: Int,
    val coverImage: String?,
    val tags: List<String>,
    val isPublic: Boolean,
    val createdAt: String,
)

data class MoodBoardItemResponse(
    val id: Long,
    val boardId: Long,
    val type: String,
    val title: String?,
    val imageUrl: String?,
    val sourceUrl: String?,
    val note: String?,
    val color: String?,
    val position: Int,
)

data class CreateMoodBoardRequest(
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
)

data class MoodBoardSummaryResponse(
    val totalBoards: Int,
    val totalItems: Int,
    val topCategory: String,
    val recentBoard: String,
    val avgItemsPerBoard: Double,
)
