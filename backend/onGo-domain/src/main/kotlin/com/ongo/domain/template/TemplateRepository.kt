package com.ongo.domain.template

interface TemplateRepository {
    fun findById(id: Long): Template?
    fun findByUserId(userId: Long, page: Int, size: Int): List<Template>
    fun countByUserId(userId: Long): Int
    fun save(template: Template): Template
    fun update(template: Template): Template
    fun delete(id: Long)
    fun incrementUsageCount(id: Long)
}
