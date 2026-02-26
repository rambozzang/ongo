package com.ongo.domain.contenttranslator

interface TranslationJobRepository {
    fun findById(id: Long): TranslationJob?
    fun findByUserId(userId: Long): List<TranslationJob>
    fun save(job: TranslationJob): TranslationJob
    fun updateStatus(id: Long, status: String)
}
