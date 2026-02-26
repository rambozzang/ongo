package com.ongo.domain.contentseries

interface SeriesEpisodeRepository {
    fun findById(id: Long): SeriesEpisode?
    fun findBySeriesId(seriesId: Long): List<SeriesEpisode>
    fun save(episode: SeriesEpisode): SeriesEpisode
    fun update(episode: SeriesEpisode): SeriesEpisode
    fun delete(id: Long)
}
