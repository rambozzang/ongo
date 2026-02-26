package com.ongo.domain.contentversioning

interface ContentVersionRepository {
    fun findByGroupId(groupId: Long): List<ContentVersion>
    fun findById(id: Long): ContentVersion?
    fun save(version: ContentVersion): ContentVersion
}
