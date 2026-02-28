package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fancommunity.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class CommunityPostJooqRepository(
    private val dsl: DSLContext,
) : CommunityPostRepository {

    companion object {
        private val TABLE = DSL.table("community_posts")
        private val TYPE = DSL.field("type", String::class.java)
        private val CONTENT = DSL.field("content", String::class.java)
        private val AUTHOR_NAME = DSL.field("author_name", String::class.java)
        private val AUTHOR_AVATAR = DSL.field("author_avatar", String::class.java)
        private val IS_CREATOR = DSL.field("is_creator", Boolean::class.java)
        private val LIKES = DSL.field("likes", Int::class.java)
        private val COMMENTS_COUNT = DSL.field("comments_count", Int::class.java)
        private val SHARES = DSL.field("shares", Int::class.java)
        private val IS_PINNED = DSL.field("is_pinned", Boolean::class.java)
        private val TAGS = DSL.field("tags", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<CommunityPost> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .fetch { it.toPost() }

    override fun findByWorkspaceIdAndType(workspaceId: Long, type: String): List<CommunityPost> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(TYPE.eq(type))
            .orderBy(CREATED_AT.desc())
            .fetch { it.toPost() }

    override fun findById(id: Long): CommunityPost? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toPost()

    override fun save(post: CommunityPost): CommunityPost {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, post.workspaceId)
            .set(TYPE, post.type)
            .set(TITLE, post.title)
            .set(CONTENT, post.content)
            .set(AUTHOR_NAME, post.authorName)
            .set(AUTHOR_AVATAR, post.authorAvatar)
            .set(IS_CREATOR, post.isCreator)
            .set(LIKES, post.likes)
            .set(COMMENTS_COUNT, post.commentsCount)
            .set(SHARES, post.shares)
            .set(IS_PINNED, post.isPinned)
            .set(TAGS, DSL.field("?::jsonb", String::class.java, post.tags.let { com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(it) }))
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(post: CommunityPost): CommunityPost {
        dsl.update(TABLE)
            .set(TYPE, post.type)
            .set(TITLE, post.title)
            .set(CONTENT, post.content)
            .set(AUTHOR_NAME, post.authorName)
            .set(AUTHOR_AVATAR, post.authorAvatar)
            .set(IS_CREATOR, post.isCreator)
            .set(LIKES, post.likes)
            .set(COMMENTS_COUNT, post.commentsCount)
            .set(SHARES, post.shares)
            .set(IS_PINNED, post.isPinned)
            .set(TAGS, DSL.field("?::jsonb", String::class.java, com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(post.tags)))
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(post.id))
            .execute()
        return findById(post.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toPost(): CommunityPost {
        val tagsRaw = get("tags")
        val tagsStr = when (tagsRaw) {
            is String -> tagsRaw
            else -> tagsRaw?.toString() ?: "[]"
        }
        val tagsList = tagsStr.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }

        return CommunityPost(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            type = get(TYPE) ?: "DISCUSSION",
            title = get(TITLE),
            content = get(CONTENT),
            authorName = get(AUTHOR_NAME),
            authorAvatar = get(AUTHOR_AVATAR),
            isCreator = get(IS_CREATOR) ?: false,
            likes = get(LIKES) ?: 0,
            commentsCount = get(COMMENTS_COUNT) ?: 0,
            shares = get(SHARES) ?: 0,
            isPinned = get(IS_PINNED) ?: false,
            tags = tagsList,
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
    }
}

@Repository
class PostCommentJooqRepository(
    private val dsl: DSLContext,
) : PostCommentRepository {

    companion object {
        private val TABLE = DSL.table("post_comments")
        private val POST_ID = DSL.field("post_id", Long::class.java)
        private val AUTHOR_NAME = DSL.field("author_name", String::class.java)
        private val AUTHOR_AVATAR = DSL.field("author_avatar", String::class.java)
        private val IS_CREATOR = DSL.field("is_creator", Boolean::class.java)
        private val CONTENT = DSL.field("content", String::class.java)
        private val LIKES = DSL.field("likes", Int::class.java)
    }

    override fun findByPostId(postId: Long): List<PostComment> =
        dsl.select().from(TABLE)
            .where(POST_ID.eq(postId))
            .orderBy(CREATED_AT.asc())
            .fetch { it.toComment() }

    override fun findById(id: Long): PostComment? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toComment()

    override fun save(comment: PostComment): PostComment {
        val id = dsl.insertInto(TABLE)
            .set(POST_ID, comment.postId)
            .set(AUTHOR_NAME, comment.authorName)
            .set(AUTHOR_AVATAR, comment.authorAvatar)
            .set(IS_CREATOR, comment.isCreator)
            .set(CONTENT, comment.content)
            .set(LIKES, comment.likes)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    override fun countByPostId(postId: Long): Int =
        dsl.selectCount().from(TABLE).where(POST_ID.eq(postId)).fetchOne(0, Int::class.java) ?: 0

    private fun Record.toComment(): PostComment = PostComment(
        id = get(ID),
        postId = get(POST_ID),
        authorName = get(AUTHOR_NAME),
        authorAvatar = get(AUTHOR_AVATAR),
        isCreator = get(IS_CREATOR) ?: false,
        content = get(CONTENT),
        likes = get(LIKES) ?: 0,
        createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
    )
}
