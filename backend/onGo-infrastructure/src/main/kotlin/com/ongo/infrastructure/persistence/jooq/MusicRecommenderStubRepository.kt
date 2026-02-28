package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.musicrecommender.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MusicRecommendationJooqRepository(
    private val dsl: DSLContext,
) : MusicRecommendationRepository {

    companion object {
        private val TABLE = DSL.table("music_recommendations")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val SELECTED_TRACK_ID = DSL.field("selected_track_id", Long::class.java)
    }

    override fun findById(id: Long): MusicRecommendation? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toRecommendation()

    override fun findByUserId(userId: Long): List<MusicRecommendation> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toRecommendation() }

    override fun save(recommendation: MusicRecommendation): MusicRecommendation {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, recommendation.userId)
            .set(VIDEO_ID, recommendation.videoId)
            .set(VIDEO_TITLE, recommendation.videoTitle)
            .set(SELECTED_TRACK_ID, recommendation.selectedTrackId)
            .set(STATUS, recommendation.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toRecommendation(): MusicRecommendation =
        MusicRecommendation(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            videoTitle = get(VIDEO_TITLE),
            selectedTrackId = get("selected_track_id")?.let { (it as Number).toLong() },
            status = get(STATUS) ?: "PENDING",
            createdAt = localDateTime(CREATED_AT),
        )
}

@Repository
class MusicTrackJooqRepository(
    private val dsl: DSLContext,
) : MusicTrackRepository {

    companion object {
        private val TABLE = DSL.table("music_tracks")
        private val ARTIST = DSL.field("artist", String::class.java)
        private val GENRE = DSL.field("genre", String::class.java)
        private val MOOD = DSL.field("mood", String::class.java)
        private val BPM = DSL.field("bpm", Int::class.java)
        private val DURATION = DSL.field("duration", Int::class.java)
        private val PREVIEW_URL = DSL.field("preview_url", String::class.java)
        private val LICENSE_TYPE = DSL.field("license_type", String::class.java)
    }

    override fun findById(id: Long): MusicTrack? =
        dsl.select().from(TABLE).where(Fields.ID.eq(id)).fetchOne()?.toTrack()

    override fun findByGenre(genre: String): List<MusicTrack> =
        dsl.select().from(TABLE).where(GENRE.eq(genre))
            .fetch().map { it.toTrack() }

    override fun findByMood(mood: String): List<MusicTrack> =
        dsl.select().from(TABLE).where(MOOD.eq(mood))
            .fetch().map { it.toTrack() }

    override fun save(track: MusicTrack): MusicTrack {
        val id = dsl.insertInto(TABLE)
            .set(TITLE, track.title)
            .set(ARTIST, track.artist)
            .set(GENRE, track.genre)
            .set(MOOD, track.mood)
            .set(BPM, track.bpm)
            .set(DURATION, track.duration)
            .set(PREVIEW_URL, track.previewUrl)
            .set(LICENSE_TYPE, track.licenseType)
            .returningResult(Fields.ID)
            .fetchOne()!!.get(Fields.ID)

        return findById(id)!!
    }

    private fun Record.toTrack(): MusicTrack =
        MusicTrack(
            id = get(Fields.ID),
            title = get(TITLE),
            artist = get(ARTIST),
            genre = get(GENRE),
            mood = get(MOOD),
            bpm = get(BPM) ?: 0,
            duration = get(DURATION) ?: 0,
            previewUrl = get(PREVIEW_URL),
            licenseType = get(LICENSE_TYPE) ?: "FREE",
            createdAt = localDateTime(Fields.CREATED_AT),
        )
}
