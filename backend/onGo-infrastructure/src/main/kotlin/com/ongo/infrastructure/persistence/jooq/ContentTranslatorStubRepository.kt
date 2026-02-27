package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contenttranslator.*
import org.springframework.stereotype.Repository

@Repository
class TranslationGlossaryStubRepository : TranslationGlossaryRepository {
    override fun findByUserId(userId: Long): List<TranslationGlossary> = emptyList()
    override fun save(glossary: TranslationGlossary): TranslationGlossary = glossary.copy(id = 1)
    override fun deleteById(id: Long) {}
}

@Repository
class TranslationJobStubRepository : TranslationJobRepository {
    override fun findById(id: Long): TranslationJob? = null
    override fun findByUserId(userId: Long): List<TranslationJob> = emptyList()
    override fun save(job: TranslationJob): TranslationJob = job.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
}
