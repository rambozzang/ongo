package com.ongo.application.contentseries

import com.ongo.application.contentseries.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.contentseries.ContentSeries
import com.ongo.domain.contentseries.ContentSeriesRepository
import com.ongo.domain.contentseries.SeriesEpisode
import com.ongo.domain.contentseries.SeriesEpisodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentSeriesUseCase(
    private val contentSeriesRepository: ContentSeriesRepository,
    private val seriesEpisodeRepository: SeriesEpisodeRepository,
) {

    fun getAll(userId: Long): List<ContentSeriesResponse> {
        val seriesList = contentSeriesRepository.findByUserId(userId)
        return seriesList.map { series ->
            val episodes = seriesEpisodeRepository.findBySeriesId(series.id!!)
            series.toResponse(episodes)
        }
    }

    fun getById(userId: Long, seriesId: Long): ContentSeriesResponse {
        val series = contentSeriesRepository.findById(seriesId)
            ?: throw NotFoundException("콘텐츠 시리즈", seriesId)
        if (series.userId != userId) throw ForbiddenException("해당 시리즈에 대한 권한이 없습니다")
        val episodes = seriesEpisodeRepository.findBySeriesId(seriesId)
        return series.toResponse(episodes)
    }

    @Transactional
    fun create(userId: Long, request: CreateSeriesRequest): ContentSeriesResponse {
        val series = ContentSeries(
            userId = userId,
            title = request.title,
            description = request.description,
            platform = request.platform,
            frequency = request.frequency,
            customFrequencyDays = request.customFrequencyDays,
            tags = request.tags.joinToString(","),
        )
        val saved = contentSeriesRepository.save(series)
        return saved.toResponse(emptyList())
    }

    @Transactional
    fun update(userId: Long, seriesId: Long, request: UpdateSeriesRequest): ContentSeriesResponse {
        val series = contentSeriesRepository.findById(seriesId)
            ?: throw NotFoundException("콘텐츠 시리즈", seriesId)
        if (series.userId != userId) throw ForbiddenException("해당 시리즈에 대한 권한이 없습니다")
        val updated = series.copy(
            title = request.title ?: series.title,
            description = request.description ?: series.description,
            status = request.status ?: series.status,
            frequency = request.frequency ?: series.frequency,
            tags = request.tags?.joinToString(",") ?: series.tags,
        )
        val saved = contentSeriesRepository.update(updated)
        val episodes = seriesEpisodeRepository.findBySeriesId(seriesId)
        return saved.toResponse(episodes)
    }

    @Transactional
    fun delete(userId: Long, seriesId: Long) {
        val series = contentSeriesRepository.findById(seriesId)
            ?: throw NotFoundException("콘텐츠 시리즈", seriesId)
        if (series.userId != userId) throw ForbiddenException("해당 시리즈에 대한 권한이 없습니다")
        contentSeriesRepository.delete(seriesId)
    }

    @Transactional
    fun addEpisode(userId: Long, seriesId: Long, request: AddEpisodeRequest): SeriesEpisodeResponse {
        val series = contentSeriesRepository.findById(seriesId)
            ?: throw NotFoundException("콘텐츠 시리즈", seriesId)
        if (series.userId != userId) throw ForbiddenException("해당 시리즈에 대한 권한이 없습니다")
        val existing = seriesEpisodeRepository.findBySeriesId(seriesId)
        val episode = SeriesEpisode(
            seriesId = seriesId,
            episodeNumber = existing.size + 1,
            title = request.title,
            videoId = request.videoId,
            platform = request.platform,
            status = request.status,
            scheduledDate = request.scheduledDate,
        )
        return seriesEpisodeRepository.save(episode).toResponse()
    }

    @Transactional
    fun updateEpisode(userId: Long, seriesId: Long, episodeId: Long, request: UpdateEpisodeRequest): SeriesEpisodeResponse {
        val series = contentSeriesRepository.findById(seriesId)
            ?: throw NotFoundException("콘텐츠 시리즈", seriesId)
        if (series.userId != userId) throw ForbiddenException("해당 시리즈에 대한 권한이 없습니다")
        val episode = seriesEpisodeRepository.findById(episodeId)
            ?: throw NotFoundException("에피소드", episodeId)
        val updated = episode.copy(
            title = request.title ?: episode.title,
            videoId = request.videoId ?: episode.videoId,
            status = request.status ?: episode.status,
            scheduledDate = request.scheduledDate ?: episode.scheduledDate,
        )
        return seriesEpisodeRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteEpisode(userId: Long, seriesId: Long, episodeId: Long) {
        val series = contentSeriesRepository.findById(seriesId)
            ?: throw NotFoundException("콘텐츠 시리즈", seriesId)
        if (series.userId != userId) throw ForbiddenException("해당 시리즈에 대한 권한이 없습니다")
        seriesEpisodeRepository.delete(episodeId)
    }

    fun getAnalytics(userId: Long, seriesId: Long): SeriesAnalyticsResponse {
        val series = contentSeriesRepository.findById(seriesId)
            ?: throw NotFoundException("콘텐츠 시리즈", seriesId)
        if (series.userId != userId) throw ForbiddenException("해당 시리즈에 대한 권한이 없습니다")
        val episodes = seriesEpisodeRepository.findBySeriesId(seriesId)
        val published = episodes.filter { it.status == "PUBLISHED" }
        val best = published.maxByOrNull { it.views }

        return SeriesAnalyticsResponse(
            seriesId = seriesId,
            viewsTrend = "[]",
            subscriberGrowth = 0,
            avgEngagement = if (published.isNotEmpty()) published.map { it.likes.toDouble() / maxOf(it.views, 1) }.average() * 100 else 0.0,
            bestPerformingEpisode = best?.toResponse(),
            dropOffRate = 0.0,
            audienceReturnRate = 0.0,
        )
    }

    private fun ContentSeries.toResponse(episodes: List<SeriesEpisode>): ContentSeriesResponse {
        val published = episodes.filter { it.status == "PUBLISHED" }
        return ContentSeriesResponse(
            id = id!!,
            title = title,
            description = description,
            coverImageUrl = coverImageUrl,
            status = status,
            platform = platform,
            frequency = frequency,
            totalEpisodes = episodes.size,
            publishedEpisodes = published.size,
            avgViews = if (published.isNotEmpty()) published.map { it.views }.average().toLong() else 0,
            totalViews = published.sumOf { it.views },
            episodes = episodes.map { it.toResponse() },
            tags = tags,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun SeriesEpisode.toResponse() = SeriesEpisodeResponse(
        id = id!!,
        seriesId = seriesId,
        episodeNumber = episodeNumber,
        title = title,
        videoId = videoId,
        platform = platform,
        status = status,
        scheduledDate = scheduledDate,
        publishedDate = publishedDate,
        views = views,
        likes = likes,
        comments = comments,
    )
}
