package com.ongo.domain.subtitletranslation

interface SubtitleTranslationRepository {
    fun findByWorkspaceId(workspaceId: Long): List<SubtitleTranslation>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<SubtitleTranslation>
    fun findById(id: Long): SubtitleTranslation?
    fun save(translation: SubtitleTranslation): SubtitleTranslation
    fun update(translation: SubtitleTranslation): SubtitleTranslation
}
