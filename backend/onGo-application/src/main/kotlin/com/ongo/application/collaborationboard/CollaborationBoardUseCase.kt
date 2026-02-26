package com.ongo.application.collaborationboard

import com.ongo.application.collaborationboard.dto.*
import com.ongo.domain.collaborationboard.BoardActivity
import com.ongo.domain.collaborationboard.BoardActivityRepository
import com.ongo.domain.collaborationboard.BoardTask
import com.ongo.domain.collaborationboard.BoardTaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class CollaborationBoardUseCase(
    private val taskRepository: BoardTaskRepository,
    private val activityRepository: BoardActivityRepository,
) {

    private val columnLabels = mapOf(
        "IDEA" to "아이디어", "SCRIPTING" to "대본 작성", "FILMING" to "촬영",
        "EDITING" to "편집", "REVIEW" to "검토", "SCHEDULED" to "예약됨", "PUBLISHED" to "발행 완료",
    )

    fun getBoard(workspaceId: Long): List<BoardColumnResponse> {
        val tasks = taskRepository.findByWorkspaceId(workspaceId)
        val grouped = tasks.groupBy { it.columnType }
        return columnLabels.map { (type, label) ->
            BoardColumnResponse(type, label, (grouped[type] ?: emptyList()).map { toTaskResponse(it) })
        }
    }

    fun getSummary(workspaceId: Long): BoardSummaryResponse {
        val tasks = taskRepository.findByWorkspaceId(workspaceId)
        val completed = tasks.count { it.columnType == "PUBLISHED" }
        val overdue = tasks.count { val dd = it.dueDate; dd != null && dd.isBefore(LocalDate.now()) && it.columnType != "PUBLISHED" }
        val inProgress = tasks.count { it.status == "IN_PROGRESS" }
        val activities = activityRepository.findByWorkspaceId(workspaceId, 10)
        return BoardSummaryResponse(
            totalTasks = tasks.size, completedTasks = completed, overdueTasks = overdue,
            inProgressTasks = inProgress, avgCompletionTime = 5.0, teamMembers = tasks.mapNotNull { it.assigneeId }.distinct().size,
            recentActivities = activities.map { toActivityResponse(it) },
        )
    }

    @Transactional
    fun createTask(workspaceId: Long, userId: Long, request: CreateBoardTaskRequest): BoardTaskResponse {
        val task = taskRepository.save(
            BoardTask(
                workspaceId = workspaceId, title = request.title, description = request.description,
                columnType = request.column, priority = request.priority, assigneeId = request.assigneeId,
                dueDate = request.dueDate?.let { LocalDate.parse(it) }, tags = request.tags ?: emptyList(),
            )
        )
        activityRepository.save(BoardActivity(workspaceId = workspaceId, userId = userId, action = "CREATED", taskId = task.id, taskTitle = task.title, toColumn = task.columnType))
        return toTaskResponse(task)
    }

    @Transactional
    fun moveTask(workspaceId: Long, userId: Long, request: MoveBoardTaskRequest): BoardTaskResponse {
        val task = taskRepository.findById(request.taskId)
            ?: throw IllegalArgumentException("태스크를 찾을 수 없습니다")
        val fromColumn = task.columnType
        val updated = taskRepository.update(task.copy(columnType = request.toColumn, orderIndex = request.orderIndex))
        activityRepository.save(BoardActivity(workspaceId = workspaceId, userId = userId, action = "MOVED", taskId = task.id, taskTitle = task.title, fromColumn = fromColumn, toColumn = request.toColumn))
        return toTaskResponse(updated)
    }

    @Transactional
    fun deleteTask(workspaceId: Long, id: Long) {
        taskRepository.deleteById(id)
    }

    fun getActivities(workspaceId: Long, limit: Int): List<BoardActivityResponse> {
        return activityRepository.findByWorkspaceId(workspaceId, limit).map { toActivityResponse(it) }
    }

    private fun toTaskResponse(task: BoardTask) = BoardTaskResponse(
        id = task.id, title = task.title, description = task.description,
        column = task.columnType, priority = task.priority, status = task.status,
        assigneeId = task.assigneeId, assigneeName = null, dueDate = task.dueDate?.toString(),
        tags = task.tags, attachments = task.attachments, comments = 0,
        videoId = task.videoId, orderIndex = task.orderIndex,
        createdAt = task.createdAt.toString(), updatedAt = task.updatedAt.toString(),
    )

    private fun toActivityResponse(a: BoardActivity) = BoardActivityResponse(
        id = a.id, userId = a.userId, userName = "", action = a.action,
        taskId = a.taskId, taskTitle = a.taskTitle, fromColumn = a.fromColumn,
        toColumn = a.toColumn, timestamp = a.createdAt.toString(),
    )
}
