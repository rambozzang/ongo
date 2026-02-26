package com.ongo.application.moodboard

import com.ongo.application.moodboard.dto.*
import com.ongo.domain.moodboard.*
import org.springframework.stereotype.Service

@Service
class MoodBoardUseCase(
    private val boardRepository: MoodBoardRepository,
    private val itemRepository: MoodBoardItemRepository,
) {

    fun getBoards(workspaceId: Long): List<MoodBoardResponse> {
        return boardRepository.findByWorkspaceId(workspaceId).map { toBoardResponse(it) }
    }

    fun getBoard(id: Long): MoodBoardResponse? {
        return boardRepository.findById(id)?.let { toBoardResponse(it) }
    }

    fun createBoard(workspaceId: Long, request: CreateMoodBoardRequest): MoodBoardResponse {
        val board = MoodBoard(
            workspaceId = workspaceId,
            name = request.name,
            description = request.description,
            category = request.category,
            tags = request.tags,
        )
        return toBoardResponse(boardRepository.save(board))
    }

    fun getItems(boardId: Long): List<MoodBoardItemResponse> {
        return itemRepository.findByBoardId(boardId).map { toItemResponse(it) }
    }

    fun deleteBoard(id: Long) {
        boardRepository.deleteById(id)
    }

    fun getSummary(workspaceId: Long): MoodBoardSummaryResponse {
        val boards = boardRepository.findByWorkspaceId(workspaceId)
        val totalItems = boards.sumOf { it.itemCount }
        val avg = if (boards.isNotEmpty()) totalItems.toDouble() / boards.size else 0.0
        val topCat = boards.mapNotNull { it.category }.groupBy { it }.maxByOrNull { it.value.size }?.key ?: "-"
        val recent = boards.maxByOrNull { it.createdAt }?.name ?: "-"
        return MoodBoardSummaryResponse(boards.size, totalItems, topCat, recent, avg)
    }

    private fun toBoardResponse(b: MoodBoard) = MoodBoardResponse(
        id = b.id, name = b.name, description = b.description,
        category = b.category, itemCount = b.itemCount, coverImage = b.coverImage,
        tags = b.tags, isPublic = b.isPublic, createdAt = b.createdAt.toString(),
    )

    private fun toItemResponse(i: MoodBoardItem) = MoodBoardItemResponse(
        id = i.id, boardId = i.boardId, type = i.type, title = i.title,
        imageUrl = i.imageUrl, sourceUrl = i.sourceUrl, note = i.note,
        color = i.color, position = i.position,
    )
}
