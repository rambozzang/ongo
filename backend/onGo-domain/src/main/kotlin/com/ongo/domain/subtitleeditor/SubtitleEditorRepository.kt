package com.ongo.domain.subtitleeditor

interface SubtitleEditorRepository {
    fun findById(id: Long): SubtitleTrack?
    fun findByUserId(userId: Long): List<SubtitleTrack>
    fun findByVideoId(videoId: Long): List<SubtitleTrack>
    fun save(subtitleTrack: SubtitleTrack): SubtitleTrack
    fun update(subtitleTrack: SubtitleTrack): SubtitleTrack
    fun delete(id: Long)
}
