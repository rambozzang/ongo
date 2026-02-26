package com.ongo.domain.contenttranslator

interface TranslationGlossaryRepository {
    fun findByUserId(userId: Long): List<TranslationGlossary>
    fun save(glossary: TranslationGlossary): TranslationGlossary
    fun deleteById(id: Long)
}
