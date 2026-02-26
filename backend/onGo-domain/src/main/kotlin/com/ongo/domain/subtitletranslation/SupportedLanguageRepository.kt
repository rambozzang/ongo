package com.ongo.domain.subtitletranslation

interface SupportedLanguageRepository {
    fun findAll(): List<SupportedLanguage>
    fun findByCode(code: String): SupportedLanguage?
}
