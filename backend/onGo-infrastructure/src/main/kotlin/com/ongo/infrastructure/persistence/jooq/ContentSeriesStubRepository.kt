package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentseries.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ContentSeriesJooqRepository(
    private val dsl: DSLContext,
) : ContentSeriesRepository {

    companion object {
        private val TABLE = DSL.table("content_series")
        private val COVER_IMAGE_URL = DSL.field("cover_image_url", String::class.java)
        private val FREQUENCY = DSL.field("frequency", String::class.java)
        private val CUSTOM_FREQUENCY_DAYS = DSL.field("custom_frequency_days", Int::class.java)
        private val TAGS = DSL.field("tags", String::class.java)
    }

    override fun findById(id: Long): ContentSeries? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toSeries()

    override fun findByUserId(userId: Long): List<ContentSeries> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toSeries() }

    override fun save(series: ContentSeries): ContentSeries {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, series.userId)
            .set(TITLE, series.title)
            .set(DESCRIPTION, series.description)
            .set(COVER_IMAGE_URL, series.coverImageUrl)
            .set(STATUS, series.status)
            .set(PLATFORM, series.platform)
            .set(FREQUENCY, series.frequency)
            .set(CUSTOM_FREQUENCY_DAYS, series.customFrequencyDays)
            .set(TAGS, series.tags)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(series: ContentSeries): ContentSeries {
        dsl.update(TABLE)
            .set(TITLE, series.title)
            .set(DESCRIPTION, series.description)
            .set(COVER_IMAGE_URL, series.coverImageUrl)
            .set(STATUS, series.status)
            .set(PLATFORM, series.platform)
            .set(FREQUENCY, series.frequency)
            .set(CUSTOM_FREQUENCY_DAYS, series.customFrequencyDays)
            .set(TAGS, series.tags)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(series.id))
            .execute()

        return findById(series.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toSeries(): ContentSeries =
        ContentSeries(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            coverImageUrl = get(COVER_IMAGE_URL),
            status = get(STATUS) ?: "ACTIVE",
            platform = get(PLATFORM) ?: "YOUTUBE",
            frequency = get(FREQUENCY) ?: "WEEKLY",
            customFrequencyDays = get("custom_frequency_days")?.let { (it as Number).toInt() },
            tags = get(TAGS) ?: "[]",
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
}

@Repository
class SeriesEpisodeJooqRepository(
    private val dsl: DSLContext,
) : SeriesEpisodeRepository {

    companion object {
        private val TABLE = DSL.table("series_episodes")
        private val SERIES_ID = DSL.field("series_id", Long::class.java)
        private val EPISODE_NUMBER = DSL.field("episode_number", Int::class.java)
        private val VIDEO_ID = DSL.field("video_id", String::class.java)
        private val SCHEDULED_DATE = DSL.field("scheduled_date", LocalDateTime::class.java)
        private val PUBLISHED_DATE = DSL.field("published_date", LocalDateTime::class.java)
        private val VIEWS = DSL.field("views", Long::class.java)
        private val LIKES = DSL.field("likes", Long::class.java)
        private val COMMENTS = DSL.field("comments", Long::class.java)
    }

    override fun findById(id: Long): SeriesEpisode? =
        dsl.select().from(TABLE).where(Fields.ID.eq(id)).fetchOne()?.toEpisode()

    override fun findBySeriesId(seriesId: Long): List<SeriesEpisode> =
        dsl.select().from(TABLE).where(SERIES_ID.eq(seriesId))
            .orderBy(EPISODE_NUMBER.asc())
            .fetch().map { it.toEpisode() }

    override fun save(episode: SeriesEpisode): SeriesEpisode {
        val id = dsl.insertInto(TABLE)
            .set(SERIES_ID, episode.seriesId)
            .set(EPISODE_NUMBER, episode.episodeNumber)
            .set(Fields.TITLE, episode.title)
            .set(VIDEO_ID, episode.videoId)
            .set(Fields.PLATFORM, episode.platform)
            .set(Fields.STATUS, episode.status)
            .set(SCHEDULED_DATE, episode.scheduledDate)
            .set(PUBLISHED_DATE, episode.publishedDate)
            .set(VIEWS, episode.views)
            .set(LIKES, episode.likes)
            .set(COMMENTS, episode.comments)
            .returningResult(Fields.ID)
            .fetchOne()!!.get(Fields.ID)

        return findById(id)!!
    }

    override fun update(episode: SeriesEpisode): SeriesEpisode {
        dsl.update(TABLE)
            .set(EPISODE_NUMBER, episode.episodeNumber)
            .set(Fields.TITLE, episode.title)
            .set(VIDEO_ID, episode.videoId)
            .set(Fields.PLATFORM, episode.platform)
            .set(Fields.STATUS, episode.status)
            .set(SCHEDULED_DATE, episode.scheduledDate)
            .set(PUBLISHED_DATE, episode.publishedDate)
            .set(VIEWS, episode.views)
            .set(LIKES, episode.likes)
            .set(COMMENTS, episode.comments)
            .set(Fields.UPDATED_AT, LocalDateTime.now())
            .where(Fields.ID.eq(episode.id))
            .execute()

        return findById(episode.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(Fields.ID.eq(id)).execute()
    }

    private fun Record.toEpisode(): SeriesEpisode =
        SeriesEpisode(
            id = get(Fields.ID),
            seriesId = get(SERIES_ID),
            episodeNumber = get(EPISODE_NUMBER),
            title = get(Fields.TITLE),
            videoId = get(VIDEO_ID),
            platform = get(Fields.PLATFORM) ?: "YOUTUBE",
            status = get(Fields.STATUS) ?: "PLANNED",
            scheduledDate = localDateTime(SCHEDULED_DATE),
            publishedDate = localDateTime(PUBLISHED_DATE),
            views = get(VIEWS) ?: 0,
            likes = get(LIKES) ?: 0,
            comments = get(COMMENTS) ?: 0,
            createdAt = localDateTime(Fields.CREATED_AT),
            updatedAt = localDateTime(Fields.UPDATED_AT),
        )
}
