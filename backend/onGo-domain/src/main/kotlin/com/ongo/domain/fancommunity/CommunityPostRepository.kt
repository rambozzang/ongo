package com.ongo.domain.fancommunity

interface CommunityPostRepository {
    fun findByWorkspaceId(workspaceId: Long): List<CommunityPost>
    fun findByWorkspaceIdAndType(workspaceId: Long, type: String): List<CommunityPost>
    fun findById(id: Long): CommunityPost?
    fun save(post: CommunityPost): CommunityPost
    fun update(post: CommunityPost): CommunityPost
    fun deleteById(id: Long)
}
