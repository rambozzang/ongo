package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.video.VideoFavoriteRepository
import org.jooq.Table
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class VideoFavoriteJooqRepository(
    private val dsl: org.jooq.DSLContext,
) : VideoFavoriteRepository {

    companion object {
        private val TABLE: Table<*> = DSL.table("video_favorites")
        private val USER_ID = DSL.field("user_id", Long::class.java)
        private val VIDEO_ID = DSL.field("video_id", Long::class.java)
        private val CREATED_AT = DSL.field("created_at")
    }

    override fun findVideoIdsByUserId(userId: Long): List<Long> =
        dsl.select(VIDEO_ID).from(TABLE)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch(VIDEO_ID)

    override fun exists(userId: Long, videoId: Long): Boolean =
        dsl.fetchExists(
            dsl.selectOne().from(TABLE)
                .where(USER_ID.eq(userId)).and(VIDEO_ID.eq(videoId))
        )

    override fun add(userId: Long, videoId: Long) {
        dsl.insertInto(TABLE).columns(USER_ID, VIDEO_ID).values(userId, videoId)
            .onConflict(USER_ID, VIDEO_ID).doNothing().execute()
    }

    override fun remove(userId: Long, videoId: Long) {
        dsl.deleteFrom(TABLE).where(USER_ID.eq(userId)).and(VIDEO_ID.eq(videoId)).execute()
    }

    override fun removeAll(userId: Long) {
        dsl.deleteFrom(TABLE).where(USER_ID.eq(userId)).execute()
    }
}
