package com.ongo.domain.fancommunity

interface PostCommentRepository {
    fun findByPostId(postId: Long): List<PostComment>
    fun findById(id: Long): PostComment?
    fun save(comment: PostComment): PostComment
    fun deleteById(id: Long)
    fun countByPostId(postId: Long): Int
}
