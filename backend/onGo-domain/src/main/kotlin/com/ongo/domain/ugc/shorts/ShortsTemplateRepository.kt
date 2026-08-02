package com.ongo.domain.ugc.shorts

/**
 * 쇼츠 템플릿 저장소.
 */
interface ShortsTemplateRepository {
    fun findByWorkspace(workspaceId: Long): List<ShortsTemplate>
    fun findById(id: Long): ShortsTemplate?
    fun save(template: ShortsTemplate): ShortsTemplate
    fun update(template: ShortsTemplate): ShortsTemplate
    fun delete(id: Long): Boolean
    fun clearDefault(workspaceId: Long)
}
