package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fancommunity.*
import org.springframework.stereotype.Repository

@Repository
class CommunityPostStubRepository : CommunityPostRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<CommunityPost> = emptyList()
    override fun findByWorkspaceIdAndType(workspaceId: Long, type: String): List<CommunityPost> = emptyList()
    override fun findById(id: Long): CommunityPost? = null
    override fun save(post: CommunityPost): CommunityPost = post.copy(id = 1)
    override fun update(post: CommunityPost): CommunityPost = post
    override fun deleteById(id: Long) {}
}

@Repository
class PostCommentStubRepository : PostCommentRepository {
    override fun findByPostId(postId: Long): List<PostComment> = emptyList()
    override fun findById(id: Long): PostComment? = null
    override fun save(comment: PostComment): PostComment = comment.copy(id = 1)
    override fun deleteById(id: Long) {}
    override fun countByPostId(postId: Long): Int = 0
}
