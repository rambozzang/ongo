package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subtitleeditor.*
import org.springframework.stereotype.Repository

@Repository
class SubtitleEditorStubRepository : SubtitleEditorRepository {
    override fun findById(id: Long): SubtitleTrack? = null
    override fun findByUserId(userId: Long): List<SubtitleTrack> = emptyList()
    override fun findByVideoId(videoId: Long): List<SubtitleTrack> = emptyList()
    override fun save(subtitleTrack: SubtitleTrack): SubtitleTrack = subtitleTrack.copy(id = 1)
    override fun update(subtitleTrack: SubtitleTrack): SubtitleTrack = subtitleTrack
    override fun delete(id: Long) {}
}
