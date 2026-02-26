package com.ongo.domain.moodboard

interface MoodBoardRepository {
    fun findByWorkspaceId(workspaceId: Long): List<MoodBoard>
    fun findById(id: Long): MoodBoard?
    fun save(board: MoodBoard): MoodBoard
    fun deleteById(id: Long)
}
