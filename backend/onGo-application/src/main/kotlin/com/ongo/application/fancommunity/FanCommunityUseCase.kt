package com.ongo.application.fancommunity

import com.ongo.application.fancommunity.dto.*
import com.ongo.domain.fancommunity.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FanCommunityUseCase(
    private val postRepository: CommunityPostRepository,
    private val commentRepository: PostCommentRepository,
) {

    fun getPosts(workspaceId: Long, type: String? = null): List<CommunityPostResponse> {
        val list = if (type != null) postRepository.findByWorkspaceIdAndType(workspaceId, type)
        else postRepository.findByWorkspaceId(workspaceId)
        return list.map { toPostResponse(it) }
    }

    fun getPost(workspaceId: Long, id: Long): CommunityPostResponse {
        val post = postRepository.findById(id)
            ?: throw IllegalArgumentException("게시글을 찾을 수 없습니다")
        return toPostResponse(post)
    }

    @Transactional
    fun createPost(workspaceId: Long, authorName: String, request: CreatePostRequest): CommunityPostResponse {
        val saved = postRepository.save(
            CommunityPost(
                workspaceId = workspaceId, type = request.type, title = request.title,
                content = request.content, authorName = authorName, isCreator = true,
                tags = request.tags, isPinned = request.isPinned,
            )
        )
        return toPostResponse(saved)
    }

    @Transactional
    fun deletePost(workspaceId: Long, id: Long) {
        postRepository.deleteById(id)
    }

    @Transactional
    fun likePost(workspaceId: Long, id: Long) {
        val post = postRepository.findById(id)
            ?: throw IllegalArgumentException("게시글을 찾을 수 없습니다")
        postRepository.update(post.copy(likes = post.likes + 1))
    }

    @Transactional
    fun pinPost(workspaceId: Long, id: Long) {
        val post = postRepository.findById(id)
            ?: throw IllegalArgumentException("게시글을 찾을 수 없습니다")
        postRepository.update(post.copy(isPinned = !post.isPinned))
    }

    fun getComments(postId: Long): List<PostCommentResponse> {
        return commentRepository.findByPostId(postId).map { toCommentResponse(it) }
    }

    @Transactional
    fun createComment(postId: Long, authorName: String, request: CreateCommentRequest): PostCommentResponse {
        val saved = commentRepository.save(
            PostComment(postId = postId, authorName = authorName, isCreator = true, content = request.content)
        )
        val post = postRepository.findById(postId)
        if (post != null) {
            postRepository.update(post.copy(commentsCount = commentRepository.countByPostId(postId)))
        }
        return toCommentResponse(saved)
    }

    fun getSummary(workspaceId: Long): CommunitySummaryResponse {
        val all = postRepository.findByWorkspaceId(workspaceId)
        val totalLikes = all.sumOf { it.likes.toLong() }
        val weekAgo = java.time.LocalDateTime.now().minusDays(7)
        val thisWeek = all.count { it.createdAt.isAfter(weekAgo) }
        return CommunitySummaryResponse(
            totalPosts = all.size, totalMembers = 0, activeMembersToday = 0,
            totalLikes = totalLikes, postsThisWeek = thisWeek,
        )
    }

    private fun toPostResponse(p: CommunityPost) = CommunityPostResponse(
        id = p.id, type = p.type, title = p.title, content = p.content,
        authorName = p.authorName, authorAvatar = p.authorAvatar, isCreator = p.isCreator,
        likes = p.likes, comments = p.commentsCount, shares = p.shares,
        isPinned = p.isPinned, tags = p.tags, attachments = emptyList(),
        createdAt = p.createdAt.toString(), updatedAt = p.updatedAt.toString(),
    )

    private fun toCommentResponse(c: PostComment) = PostCommentResponse(
        id = c.id, postId = c.postId, authorName = c.authorName,
        authorAvatar = c.authorAvatar, isCreator = c.isCreator,
        content = c.content, likes = c.likes, createdAt = c.createdAt.toString(),
    )
}
