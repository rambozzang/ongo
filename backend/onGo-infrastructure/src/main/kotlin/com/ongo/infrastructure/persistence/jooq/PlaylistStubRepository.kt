package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.playlist.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PlaylistJooqRepository(
    private val dsl: DSLContext,
) : PlaylistRepository {

    companion object {
        private val TABLE = DSL.table("playlists")
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val PLATFORM_PLAYLIST_ID = DSL.field("platform_playlist_id", String::class.java)
        private val VISIBILITY = DSL.field("visibility", String::class.java)
        private val THUMBNAIL_URL = DSL.field("thumbnail_url", String::class.java)
        private val VIDEO_COUNT = DSL.field("video_count", Int::class.java)
        private val TOTAL_VIEWS = DSL.field("total_views", Long::class.java)
        private val TOTAL_DURATION = DSL.field("total_duration", Int::class.java)
        private val TAGS = DSL.field("tags", Array<String>::class.java)
        private val IS_SYNCED = DSL.field("is_synced", Boolean::class.java)
        private val LAST_SYNCED_AT = DSL.field("last_synced_at", LocalDateTime::class.java)
    }

    override fun findByUserId(userId: Long): List<Playlist> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toPlaylist() }

    override fun findByUserIdAndPlatform(userId: Long, platform: String): List<Playlist> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(PLATFORM.eq(platform))
            .fetch().map { it.toPlaylist() }

    override fun findById(id: Long): Playlist? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toPlaylist()

    override fun save(playlist: Playlist): Playlist {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, playlist.userId)
            .set(TITLE, playlist.title)
            .set(DESCRIPTION, playlist.description)
            .set(PLATFORM, playlist.platform)
            .set(PLATFORM_PLAYLIST_ID, playlist.platformPlaylistId)
            .set(VISIBILITY, playlist.visibility)
            .set(THUMBNAIL_URL, playlist.thumbnailUrl)
            .set(VIDEO_COUNT, playlist.videoCount)
            .set(TOTAL_VIEWS, playlist.totalViews)
            .set(TOTAL_DURATION, playlist.totalDuration)
            .set(TAGS, playlist.tags.toTypedArray())
            .set(IS_SYNCED, playlist.isSynced)
            .set(LAST_SYNCED_AT, playlist.lastSyncedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(playlist: Playlist): Playlist {
        dsl.update(TABLE)
            .set(TITLE, playlist.title)
            .set(DESCRIPTION, playlist.description)
            .set(PLATFORM, playlist.platform)
            .set(PLATFORM_PLAYLIST_ID, playlist.platformPlaylistId)
            .set(VISIBILITY, playlist.visibility)
            .set(THUMBNAIL_URL, playlist.thumbnailUrl)
            .set(VIDEO_COUNT, playlist.videoCount)
            .set(TOTAL_VIEWS, playlist.totalViews)
            .set(TOTAL_DURATION, playlist.totalDuration)
            .set(TAGS, playlist.tags.toTypedArray())
            .set(IS_SYNCED, playlist.isSynced)
            .set(LAST_SYNCED_AT, playlist.lastSyncedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(playlist.id))
            .execute()
        return findById(playlist.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toPlaylist(): Playlist {
        val tagsRaw = get("tags")
        val tagsList: List<String> = when (tagsRaw) {
            is Array<*> -> (tagsRaw as Array<String>).toList()
            else -> emptyList()
        }
        return Playlist(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            platform = get(PLATFORM),
            platformPlaylistId = get(PLATFORM_PLAYLIST_ID),
            visibility = get(VISIBILITY) ?: "PUBLIC",
            thumbnailUrl = get(THUMBNAIL_URL),
            videoCount = get(VIDEO_COUNT) ?: 0,
            totalViews = get(TOTAL_VIEWS) ?: 0,
            totalDuration = get(TOTAL_DURATION) ?: 0,
            tags = tagsList,
            isSynced = get(IS_SYNCED) ?: false,
            lastSyncedAt = localDateTime(LAST_SYNCED_AT),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
    }
}

@Repository
class PlaylistVideoJooqRepository(
    private val dsl: DSLContext,
) : PlaylistVideoRepository {

    companion object {
        private val TABLE = DSL.table("playlist_videos")
        private val PLAYLIST_ID = DSL.field("playlist_id", Long::class.java)
        private val VIDEO_ID = DSL.field("video_id", String::class.java)
        private val THUMBNAIL_URL = DSL.field("thumbnail_url", String::class.java)
        private val DURATION = DSL.field("duration", Int::class.java)
        private val VIEWS = DSL.field("views", Long::class.java)
        private val POSITION = DSL.field("position", Int::class.java)
        private val ADDED_AT = DSL.field("added_at", LocalDateTime::class.java)
    }

    override fun findByPlaylistId(playlistId: Long): List<PlaylistVideo> =
        dsl.select().from(TABLE)
            .where(PLAYLIST_ID.eq(playlistId))
            .orderBy(POSITION.asc())
            .fetch().map { it.toPlaylistVideo() }

    override fun findById(id: Long): PlaylistVideo? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toPlaylistVideo()

    override fun save(video: PlaylistVideo): PlaylistVideo {
        val newId = dsl.insertInto(TABLE)
            .set(PLAYLIST_ID, video.playlistId)
            .set(VIDEO_ID, video.videoId)
            .set(TITLE, video.title)
            .set(THUMBNAIL_URL, video.thumbnailUrl)
            .set(DURATION, video.duration)
            .set(VIEWS, video.views)
            .set(POSITION, video.position)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(newId)).fetchOne()!!.toPlaylistVideo()
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    override fun deleteByPlaylistIdAndVideoId(playlistId: Long, videoId: String) {
        dsl.deleteFrom(TABLE)
            .where(PLAYLIST_ID.eq(playlistId))
            .and(VIDEO_ID.eq(videoId))
            .execute()
    }

    override fun reorder(playlistId: Long, videoIds: List<String>) {
        videoIds.forEachIndexed { index, vid ->
            dsl.update(TABLE)
                .set(POSITION, index)
                .where(PLAYLIST_ID.eq(playlistId))
                .and(VIDEO_ID.eq(vid))
                .execute()
        }
    }

    private fun Record.toPlaylistVideo(): PlaylistVideo =
        PlaylistVideo(
            id = get(ID),
            playlistId = get(PLAYLIST_ID),
            videoId = get(VIDEO_ID),
            title = get(TITLE),
            thumbnailUrl = get(THUMBNAIL_URL),
            duration = get(DURATION) ?: 0,
            views = get(VIEWS) ?: 0,
            position = get(POSITION) ?: 0,
            addedAt = localDateTime(ADDED_AT) ?: LocalDateTime.now(),
        )
}
