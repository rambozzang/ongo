package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subtitletranslation.*
import org.springframework.stereotype.Repository

@Repository
class SubtitleTranslationStubRepository : SubtitleTranslationRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<SubtitleTranslation> = emptyList()
    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<SubtitleTranslation> = emptyList()
    override fun findById(id: Long): SubtitleTranslation? = null
    override fun save(translation: SubtitleTranslation): SubtitleTranslation = translation.copy(id = 1)
    override fun update(translation: SubtitleTranslation): SubtitleTranslation = translation
}

@Repository
class SupportedLanguageStubRepository : SupportedLanguageRepository {
    override fun findAll(): List<SupportedLanguage> = emptyList()
    override fun findByCode(code: String): SupportedLanguage? = null
}

@Repository
class TranslationLineStubRepository : TranslationLineRepository {
    override fun findByTranslationId(translationId: Long): List<TranslationLine> = emptyList()
    override fun findById(id: Long): TranslationLine? = null
    override fun save(line: TranslationLine): TranslationLine = line.copy(id = 1)
    override fun saveBatch(lines: List<TranslationLine>): List<TranslationLine> = lines
    override fun update(line: TranslationLine): TranslationLine = line
}
