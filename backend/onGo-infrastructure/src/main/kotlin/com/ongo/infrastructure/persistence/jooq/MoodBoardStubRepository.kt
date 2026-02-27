package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.moodboard.*
import org.springframework.stereotype.Repository

@Repository
class MoodBoardStubRepository : MoodBoardRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<MoodBoard> = emptyList()
    override fun findById(id: Long): MoodBoard? = null
    override fun save(board: MoodBoard): MoodBoard = board.copy(id = 1)
    override fun deleteById(id: Long) {}
}

@Repository
class MoodBoardItemStubRepository : MoodBoardItemRepository {
    override fun findByBoardId(boardId: Long): List<MoodBoardItem> = emptyList()
}
