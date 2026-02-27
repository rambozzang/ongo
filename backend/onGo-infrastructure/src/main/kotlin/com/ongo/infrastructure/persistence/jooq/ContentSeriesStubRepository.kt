package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentseries.*
import org.springframework.stereotype.Repository

@Repository
class ContentSeriesStubRepository : ContentSeriesRepository {
    override fun findById(id: Long): ContentSeries? = null
    override fun findByUserId(userId: Long): List<ContentSeries> = emptyList()
    override fun save(series: ContentSeries): ContentSeries = series.copy(id = 1)
    override fun update(series: ContentSeries): ContentSeries = series
    override fun delete(id: Long) {}
}

@Repository
class SeriesEpisodeStubRepository : SeriesEpisodeRepository {
    override fun findById(id: Long): SeriesEpisode? = null
    override fun findBySeriesId(seriesId: Long): List<SeriesEpisode> = emptyList()
    override fun save(episode: SeriesEpisode): SeriesEpisode = episode.copy(id = 1)
    override fun update(episode: SeriesEpisode): SeriesEpisode = episode
    override fun delete(id: Long) {}
}
