package com.ongo.application.collaborationboard.dto

data class BoardTaskResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val column: String,
    val priority: String,
    val status: String,
    val assigneeId: Long?,
    val assigneeName: String?,
    val dueDate: String?,
    val tags: List<String>,
    val attachments: Int,
    val comments: Int,
    val videoId: String?,
    val orderIndex: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class BoardColumnResponse(
    val type: String,
    val label: String,
    val tasks: List<BoardTaskResponse>,
)

data class CreateBoardTaskRequest(
    val title: String,
    val description: String? = null,
    val column: String = "IDEA",
    val priority: String = "MEDIUM",
    val assigneeId: Long? = null,
    val dueDate: String? = null,
    val tags: List<String>? = null,
)

data class UpdateBoardTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val assigneeId: Long? = null,
    val dueDate: String? = null,
    val tags: List<String>? = null,
)

data class MoveBoardTaskRequest(
    val taskId: Long,
    val toColumn: String,
    val orderIndex: Int = 0,
)

data class BoardActivityResponse(
    val id: Long,
    val userId: Long,
    val userName: String,
    val action: String,
    val taskId: Long?,
    val taskTitle: String?,
    val fromColumn: String?,
    val toColumn: String?,
    val timestamp: String,
)

data class BoardSummaryResponse(
    val totalTasks: Int,
    val completedTasks: Int,
    val overdueTasks: Int,
    val inProgressTasks: Int,
    val avgCompletionTime: Double,
    val teamMembers: Int,
    val recentActivities: List<BoardActivityResponse>,
)
