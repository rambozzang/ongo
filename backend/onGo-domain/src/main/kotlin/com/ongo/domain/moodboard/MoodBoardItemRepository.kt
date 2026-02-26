package com.ongo.domain.moodboard

interface MoodBoardItemRepository {
    fun findByBoardId(boardId: Long): List<MoodBoardItem>
}
