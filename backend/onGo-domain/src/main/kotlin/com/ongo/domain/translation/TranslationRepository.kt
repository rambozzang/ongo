package com.ongo.domain.translation

interface TranslationRepository {
    fun findByVideoId(videoId: Long): List<VideoTranslation>
    fun findById(id: Long): VideoTranslation?
    fun findByVideoIdAndLanguage(videoId: Long, language: String): VideoTranslation?
    fun save(translation: VideoTranslation): VideoTranslation
    fun update(id: Long, title: String?, description: String?, tags: String?, subtitleContent: String?, status: String?)
    fun delete(id: Long)
}
