package com.ongo.domain.video

import com.ongo.domain.video.entity.VideoSubtitle

interface VideoSubtitleRepository {
    fun save(subtitle: VideoSubtitle): VideoSubtitle
    fun findByVideoId(videoId: Long): List<VideoSubtitle>
    fun findByVideoIdAndLanguage(videoId: Long, language: String): VideoSubtitle?
    fun update(subtitle: VideoSubtitle): VideoSubtitle
    fun delete(id: Long)
}
