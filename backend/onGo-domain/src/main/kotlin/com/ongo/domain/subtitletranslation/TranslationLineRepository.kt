package com.ongo.domain.subtitletranslation

interface TranslationLineRepository {
    fun findByTranslationId(translationId: Long): List<TranslationLine>
    fun findById(id: Long): TranslationLine?
    fun save(line: TranslationLine): TranslationLine
    fun saveBatch(lines: List<TranslationLine>): List<TranslationLine>
    fun update(line: TranslationLine): TranslationLine
}
